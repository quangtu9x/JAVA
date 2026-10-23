package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.UnitResult;
import com.td.worker.infrastructure.fileregistry.SharedFileRegistry;
import com.td.worker.infrastructure.minio.WorkerMinioClient;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.transport.rabbit.RabbitPublisher;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test {@link VbdiDeliveryTaskHandler} — nhánh {@code contact_unit} (cơ quan/đơn vị KHÁC thuộc
 * tỉnh — bugfix 06/09/2026, trước đó nhầm gắn vào {@code internal_unit}) tạo THẬT bản ghi "văn bản
 * đến chờ vào sổ" (xem {@link VbdenIncomingDocumentWriter}).
 *
 * <p><b>Vì sao Testcontainers (Postgres thật), không phải H2</b> như phần lớn test khác của
 * {@code td-woker}: {@code markArrived}/{@link VbdiDeliveryTaskHandler} dùng
 * {@code INSERT ... ON CONFLICT (send_log_id) DO NOTHING} — đã XÁC NHẬN bằng test trực tiếp H2
 * 2.2.224 KHÔNG parse được cú pháp này ở BẤT KỲ {@code MODE} nào (lỗi cú pháp, không phải vấn đề
 * tương thích kiểu dữ liệu) — và {@code documents.dynamic_fields} là JSONB thật. Đổi
 * {@code markArrived} sang mẫu "portable" {@code INSERT ... WHERE NOT EXISTS} (như
 * {@code JdbcCheckpointStore}) sẽ MẤT tính atomic của "ON CONFLICT DO NOTHING" bên trong 1
 * transaction đang mở (Postgres poison cả transaction khi 1 statement lỗi UNIQUE VIOLATION, không
 * chỉ riêng statement đó) — không an toàn cho thiết kế "dedup + văn bản trong CÙNG 1 transaction"
 * (xem javadoc {@link VbdiDeliveryTaskHandler#execute}), nên PHẢI giữ nguyên SQL thật và test bằng
 * PostgreSQL thật thay vì đổi production code để lách H2. Cùng pattern/lý do đã dùng ở td-web
 * ({@code DynamicFieldsJsonbIntegrationTest}/{@code TenantSchemaIsolationIntegrationTest}) — skip
 * (không fail build) nếu môi trường không có Docker Linux container khả dụng; chạy thật ở CI
 * ({@code ubuntu-latest}).</p>
 */
class VbdiDeliveryIncomingDocumentIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    /** DataSource dùng bởi handler dưới test - autoCommit=false, ĐÚNG cấu hình tenant pool thật. */
    private static HikariDataSource dataSource;
    /** DataSource RIÊNG cho test harness (schema setup/reset) - autoCommit=true mặc định, để DDL/
     * DML của bản thân test luôn commit thật, KHÔNG lẫn với autoCommit=false đang test ở trên. */
    private static HikariDataSource adminDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TenantDataSourceManager tenantDataSources;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RabbitPublisher> rabbitPublisher = mock(ObjectProvider.class);
    private WorkerProperties props;
    private VbdiDeliveryTaskHandler handler;
    private SharedFileRegistry sharedFileRegistry;

    @BeforeAll
    static void startContainer() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdworker_vbdi_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua VbdiDeliveryIncomingDocumentIntegrationTest: khong co Docker Linux container "
                    + "kha dung trong moi truong nay (" + ex.getMessage() + "). Test nay chay that "
                    + "trong CI (.github/workflows/ci.yml, ubuntu-latest).");
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-vbdi-incoming-document-test");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        // Bugfix 06/09/2026: worker.tenant.pool-auto-commit MẶC ĐỊNH false cho MỌI pool tenant thật
        // (TenantDataSourceManager) - PHẢI tái tạo đúng ở test, nếu không test sẽ KHÔNG bắt được lỗi
        // "CREATE TABLE/INSERT chạy ngoài transaction bị HikariCP tự rollback khi trả connection về
        // pool" (lỗi thật đã xảy ra ở vận hành - xem javadoc VbdiDeliveryTaskHandler#execute).
        config.setAutoCommit(false);
        // Giống hệt TenantDataSourceManager thật - điều kiện để String bind vào cột jsonb.
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);

        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setPoolName("hikari-vbdi-incoming-document-test-admin");
        adminConfig.setJdbcUrl(postgres.getJdbcUrl());
        adminConfig.setUsername(postgres.getUsername());
        adminConfig.setPassword(postgres.getPassword());
        adminConfig.setDriverClassName(postgres.getDriverClassName());
        adminConfig.addDataSourceProperty("stringtype", "unspecified");
        adminDataSource = new HikariDataSource(adminConfig); // autoCommit=true mặc định

        // Subset THẬT của "documents" (V1.0.3 + V1.0.12 dynamic_fields + V1.0.27 revert-partition,
        // xem JAVA/td-web/src/main/resources/db/migration) - đủ cột cho test này, không cần Flyway
        // đầy đủ của td-web (module khác, không phải test dependency của td-woker).
        new JdbcTemplate(adminDataSource).execute("""
            CREATE TABLE documents (
                id UUID PRIMARY KEY,
                title VARCHAR(300) NOT NULL,
                document_type VARCHAR(100),
                status VARCHAR(50),
                content TEXT,
                tags_json TEXT NOT NULL DEFAULT '[]',
                attributes_json TEXT NOT NULL DEFAULT '{}',
                metadata_json TEXT NOT NULL DEFAULT '{}',
                dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
                version_no BIGINT NOT NULL DEFAULT 1,
                created_by UUID,
                created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                last_modified_by UUID,
                last_modified_on TIMESTAMP WITH TIME ZONE,
                deleted_on TIMESTAMP WITH TIME ZONE,
                deleted_by UUID
            )
            """);

        // Bo sung 07/09/2026 - subset THAT cua "file_metadata" (V1.0.2, td-web migration) - du cot
        // cho VbdenIncomingDocumentWriter#insertFileMetadata test duoc dung schema, khong can Flyway
        // day du.
        new JdbcTemplate(adminDataSource).execute("""
            CREATE TABLE file_metadata (
                id UUID PRIMARY KEY,
                document_id UUID,
                original_filename VARCHAR(255) NOT NULL,
                stored_filename VARCHAR(255) NOT NULL UNIQUE,
                file_path VARCHAR(500) NOT NULL,
                file_size BIGINT NOT NULL,
                content_type VARCHAR(255),
                file_extension VARCHAR(20),
                file_category VARCHAR(50) NOT NULL,
                bucket_name VARCHAR(100) NOT NULL,
                uploaded_by UUID,
                uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                is_public BOOLEAN DEFAULT FALSE,
                is_shared BOOLEAN NOT NULL DEFAULT FALSE,
                description TEXT
            )
            """);
    }

    @AfterAll
    static void stopContainer() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (adminDataSource != null) {
            adminDataSource.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(dataSource != null, "Container chưa khởi động được (xem @BeforeAll)");
        new JdbcTemplate(adminDataSource).execute("TRUNCATE TABLE documents");
        new JdbcTemplate(adminDataSource).execute("TRUNCATE TABLE file_metadata");
        new JdbcTemplate(adminDataSource).execute("DROP TABLE IF EXISTS worker_vbdi_delivery_received");

        tenantDataSources = mock(TenantDataSourceManager.class);
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.of(dataSource));
        props = new WorkerProperties();
        props.getTasks().getVbdi().setEnabled(true);
        props.getTasks().getVbdi().setVerifyObjectExists(false);
        sharedFileRegistry = mock(SharedFileRegistry.class);
        handler = new VbdiDeliveryTaskHandler(
            props, tenantDataSources, mock(WorkerMinioClient.class), objectMapper, rabbitPublisher, sharedFileRegistry);
    }

    @AfterEach
    void resetTables() {
        if (adminDataSource != null) {
            new JdbcTemplate(adminDataSource).execute("DROP TABLE IF EXISTS worker_vbdi_delivery_received");
        }
    }

    private ObjectNode basePayload(String recipientGroup) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sendLogId", "sl-1");
        node.put("documentId", "doc-1");
        node.put("senderTenantId", "H05");
        node.put("senderIdentifier", "H05");
        node.put("recipientGroup", recipientGroup);
        node.put("recipientIdentifier", "recv-1");
        node.put("sendType", "senddocument");
        return node;
    }

    private TaskContext context(String tenantId) {
        return new TaskContext("vbdi.delivery", "msg-1", "u1", "corr-1", tenantId,
            null, Map.of(), 5, null, Map.of());
    }

    @Test
    void contactUnitCreatesRealIncomingDocumentInQuamangnoiboQueueShape() throws Exception {
        ObjectNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);
        payload.put("senderOrganName", "Sở Khoa học và Công nghệ");
        ObjectNode snapshot = payload.putObject("documentSnapshot");
        snapshot.put("subject", "V/v triển khai nhiệm vụ khoa học công nghệ");
        snapshot.put("code", "123/QĐ-SKHCN");
        snapshot.put("promulgationDate", "01/09/2026"); // dd/MM/yyyy - đúng định dạng FE thật dùng
        // Bo sung 07/09/2026 - doi chieu docs/views_domino/data_vbdi_SendDocument_output.json (xem
        // javadoc VbdenIncomingDocumentWriter). Ten field GIU NGUYEN theo VBDI_FORM_FIELDS.
        snapshot.put("draftor", "Văn thư Cục");
        snapshot.put("signer", "Lê Phú Hà");
        snapshot.put("signerPosition", "Giám đốc");
        snapshot.put("creator", "vanthu01");
        snapshot.put("deliveryMethod", "Điện tử");
        snapshot.put("documentType", "Báo cáo");
        snapshot.put("sector", "Khoa học công nghệ");
        snapshot.put("confidentiality", "Thường");
        snapshot.put("pageAmount", "5");
        snapshot.put("priority", "Hoả tốc");
        snapshot.put("documentReplaceRetrieve", "2");

        UnitResult result = handler.execute(context("H06"), payload);
        assertThat(result.isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Map<String, Object>> docs = jdbc.queryForList("SELECT * FROM documents");
        assertThat(docs).hasSize(1);
        Map<String, Object> doc = docs.get(0);
        assertThat(doc.get("document_type")).isEqualTo("vbden");
        assertThat(doc.get("status")).isEqualTo(VbdenIncomingDocumentWriter.STATUS_MOI);
        assertThat(doc.get("title")).isEqualTo("V/v triển khai nhiệm vụ khoa học công nghệ");

        Map<String, Object> dynamicFields = objectMapper.readValue(
            doc.get("dynamic_fields").toString(), Map.class);
        // "recv-1" = recipientIdentifier - đúng field ViewQueryService#quamangnoiboFilter khoá
        // theo (contact_unit_identifier) + document_internet="1" để văn bản hiện ở hàng đợi
        // "đến qua mạng nội bộ" (contact_unit, KHÔNG phải dennoibo/internal_unit).
        assertThat(dynamicFields.get("contact_unit_identifier")).isEqualTo("recv-1");
        assertThat(dynamicFields.get("document_internet")).isEqualTo("1");
        assertThat(dynamicFields.get("unit_in_charge")).isEqualTo("Sở Khoa học và Công nghệ");
        assertThat(dynamicFields.get("code")).isEqualTo("123/QĐ-SKHCN");
        assertThat(dynamicFields.get("promulgation_date")).isEqualTo("01/09/2026");
        assertThat(dynamicFields.get("send_log_id")).isEqualTo("sl-1");
        // Bugfix 07/09/2026: priority phai lay tu van ban di THAT ("Hoa toc"), KHONG con hard-code
        // "Thuong" bat ke van ban di chon do khan gi (xem test rieng priorityFallsBackToThuong...
        // cho truong hop KHONG co priority trong snapshot).
        assertThat(dynamicFields.get("priority")).isEqualTo("Hoả tốc");
        // Định dạng THẬT xác nhận qua docs/views_domino/data_vbden_trongtinh_mau.txt
        // ("received_date": "06/09/2026 17:01:39") - KHÔNG phải ISO.
        assertThat((String) dynamicFields.get("received_date"))
            .matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
        // Bo sung 07/09/2026 - 10 field mo ta doi chieu data_vbdi_SendDocument_output.json, forward
        // tu van ban di goc (xem AddVbdiSendLogUseCase.publishDeliveryMessage).
        assertThat(dynamicFields.get("draftor")).isEqualTo("Văn thư Cục");
        assertThat(dynamicFields.get("signer")).isEqualTo("Lê Phú Hà");
        assertThat(dynamicFields.get("signer_position")).isEqualTo("Giám đốc");
        assertThat(dynamicFields.get("creator")).isEqualTo("vanthu01");
        assertThat(dynamicFields.get("delivery_method")).isEqualTo("Điện tử");
        assertThat(dynamicFields.get("document_type")).isEqualTo("Báo cáo");
        assertThat(dynamicFields.get("sector")).isEqualTo("Khoa học công nghệ");
        assertThat(dynamicFields.get("confidentiality")).isEqualTo("Thường");
        assertThat(dynamicFields.get("page_amount")).isEqualTo("5");
        // Ghi VERBATIM (khong dich sang bussiness_doc_type/paper/documentreply cua vbden) - da chot
        // voi nguoi dung vi form vbden dung 3 ten khac nhau, khong ro anh xa dung.
        assertThat(dynamicFields.get("document_replace_retrieve")).isEqualTo("2");

        // Dedup table phải lưu đúng id văn bản vừa tạo (đối chiếu vận hành).
        UUID linkedDocId = jdbc.queryForObject(
            "SELECT created_document_id FROM worker_vbdi_delivery_received WHERE send_log_id = ?",
            UUID.class, "sl-1");
        assertThat(linkedDocId).isEqualTo(doc.get("id"));
    }

    @Test
    void priorityFallsBackToThuongWhenSenderDocumentHasNone() throws Exception {
        // documentSnapshot khong co "priority" (vd van ban di cu, tao truoc khi field nay duoc
        // forward) - phai fallback ve "Thuong" nhu truoc, KHONG duoc de trong/null.
        JsonNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);

        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> doc = jdbc.queryForList("SELECT * FROM documents").get(0);
        Map<String, Object> dynamicFields = objectMapper.readValue(
            doc.get("dynamic_fields").toString(), Map.class);
        assertThat(dynamicFields.get("priority")).isEqualTo("Thường");
    }

    /**
     * Bổ sung 07/09/2026 (yêu cầu người dùng "khi phát hành sẽ gửi thêm 'files'... không muốn nhân
     * tệp đính kèm"): {@code documentSnapshot.files} phải ghi ĐƯỢC 1 dòng {@code file_metadata}
     * RIÊNG cho tenant nơi nhận này (KHÔNG copy vật lý gì thêm — {@code file_path}/{@code
     * bucket_name} trỏ CÙNG object đã copy sẵn phía gửi), và {@code dynamic_fields.files} phải khớp
     * shape {@code {name,size,type,file_id}} mà FE (filesVBden.jsx) đang đọc.
     */
    @Test
    void contactUnitInsertsFileMetadataRowsPointingAtAlreadyCopiedObjects() throws Exception {
        ObjectNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);
        ObjectNode snapshot = payload.putObject("documentSnapshot");
        snapshot.put("subject", "Văn bản có đính kèm");
        ArrayNode files = snapshot.putArray("files");
        ObjectNode file1 = files.addObject();
        file1.put("objectKey", "vbdi-delivery/H05.01/2026/09/07/doc-1/aaa.pdf");
        file1.put("bucketName", "td-webapi-files");
        file1.put("originalFilename", "to_trinh.pdf");
        file1.put("size", 20480);
        file1.put("contentType", "application/pdf");
        file1.put("fileExtension", "pdf");
        ObjectNode file2 = files.addObject();
        file2.put("objectKey", "vbdi-delivery/H05.01/2026/09/07/doc-1/bbb.docx");
        file2.put("bucketName", "td-webapi-files");
        file2.put("originalFilename", "phu_luc.docx");
        file2.put("size", 4096);

        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> doc = jdbc.queryForList("SELECT * FROM documents").get(0);
        UUID documentId = (UUID) doc.get("id");

        List<Map<String, Object>> fileRows = jdbc.queryForList(
            "SELECT * FROM file_metadata WHERE document_id = ? ORDER BY original_filename", documentId);
        assertThat(fileRows).hasSize(2);
        assertThat(fileRows.get(0).get("original_filename")).isEqualTo("phu_luc.docx");
        assertThat(fileRows.get(0).get("file_path")).isEqualTo("vbdi-delivery/H05.01/2026/09/07/doc-1/bbb.docx");
        assertThat(fileRows.get(0).get("bucket_name")).isEqualTo("td-webapi-files");
        assertThat(fileRows.get(0).get("file_category")).isEqualTo("DOCUMENT");
        assertThat(fileRows.get(1).get("original_filename")).isEqualTo("to_trinh.pdf");
        assertThat(fileRows.get(1).get("file_path")).isEqualTo("vbdi-delivery/H05.01/2026/09/07/doc-1/aaa.pdf");
        assertThat(fileRows.get(1).get("file_extension")).isEqualTo("pdf");
        // 22/10/2026: dong dung chung (chan xoa nhu "Chuyen xu ly") + ghi tham chieu vao so dang ky sau commit.
        assertThat(fileRows).allSatisfy(r -> assertThat(r.get("is_shared")).isEqualTo(true));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SharedFileRegistry.SharedFileRef>> refs = ArgumentCaptor.forClass(List.class);
        verify(sharedFileRegistry).registerQuietly(eq("H06"), eq(SharedFileRegistry.SOURCE_VBDI_DELIVERY), refs.capture());
        assertThat(refs.getValue()).extracting(SharedFileRegistry.SharedFileRef::fileMetadataId)
            .containsExactlyInAnyOrderElementsOf(fileRows.stream().map(r -> (UUID) r.get("id")).toList());
        assertThat(refs.getValue()).allSatisfy(r -> {
            assertThat(r.documentId()).isEqualTo(documentId);
            assertThat(r.bucketName()).isEqualTo("td-webapi-files");
        });

        Map<String, Object> dynamicFields = objectMapper.readValue(
            doc.get("dynamic_fields").toString(), Map.class);
        List<Map<String, Object>> filesJson = (List<Map<String, Object>>) dynamicFields.get("files");
        assertThat(filesJson).hasSize(2);
        assertThat(filesJson).allSatisfy(f -> {
            assertThat(f).containsKeys("name", "size", "type", "file_id");
        });
        // file_id phai KHOP dung id da INSERT vao file_metadata (2 noi doc cung 1 gia tri).
        List<UUID> fileIdsInJson = filesJson.stream()
            .map(f -> UUID.fromString((String) f.get("file_id"))).toList();
        List<UUID> fileIdsInTable = fileRows.stream().map(r -> (UUID) r.get("id")).toList();
        assertThat(fileIdsInJson).containsExactlyInAnyOrderElementsOf(fileIdsInTable);
    }

    /**
     * Văn bản đi KHÔNG có tệp đính kèm nào (snapshot.files rỗng/null) — văn bản đến vẫn tạo bình
     * thường, chỉ KHÔNG có {@code dynamic_fields.files}/dòng {@code file_metadata} nào — KHÔNG lỗi.
     */
    @Test
    void contactUnitWithoutFilesCreatesDocumentWithoutFileMetadataRows() throws Exception {
        JsonNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);

        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> doc = jdbc.queryForList("SELECT * FROM documents").get(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_metadata", Long.class)).isEqualTo(0L);
        Map<String, Object> dynamicFields = objectMapper.readValue(
            doc.get("dynamic_fields").toString(), Map.class);
        assertThat(dynamicFields).doesNotContainKey("files");
    }

    /**
     * Bugfix 07/09/2026 (lỗi thật vận hành, log thực tế: {@code DuplicateKeyException} trên
     * {@code file_metadata_stored_filename_key}, sendType=resenddocument) — xem javadoc
     * {@code VbdenIncomingDocumentWriter#insertFileMetadata}: 2 nơi nhận KHÁC NHAU ({@code
     * sendLogId} khác nhau — vd 1 người nhận qua 2 lượt gửi khác nhau, hoặc trùng tenant đích) cùng
     * trỏ về CÙNG 1 tenant, dùng CHUNG {@code documentSnapshot.files} (đúng thiết kế "copy 1 lần
     * cho cả lượt gửi") — PHẢI insert được CẢ 2 dòng {@code file_metadata} riêng biệt trong CÙNG
     * database tenant, KHÔNG được đụng UNIQUE constraint dù CÙNG {@code objectKey}.
     */
    @Test
    void twoDifferentRecipientsInSameTenantSharingSameCopiedFileBothSucceed() throws Exception {
        ObjectNode payload1 = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);
        payload1.put("sendLogId", "sl-1");
        ObjectNode snapshot1 = payload1.putObject("documentSnapshot");
        snapshot1.put("subject", "Văn bản gửi lần 1");
        ArrayNode files1 = snapshot1.putArray("files");
        ObjectNode file1 = files1.addObject();
        file1.put("objectKey", "vbdi-delivery/H05.01/2026/09/07/doc-1/aaa.pdf");
        file1.put("bucketName", "td-webapi-files");
        file1.put("originalFilename", "to_trinh.pdf");
        file1.put("size", 20480);

        ObjectNode payload2 = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);
        payload2.put("sendLogId", "sl-2"); // sendLogId KHAC - khong bi chan boi dedup theo send_log_id.
        ObjectNode snapshot2 = payload2.putObject("documentSnapshot");
        snapshot2.put("subject", "Văn bản gửi lần 2");
        ArrayNode files2 = snapshot2.putArray("files");
        ObjectNode file2 = files2.addObject();
        // CUNG objectKey voi payload1 - mo phong dung thiet ke "copy 1 lan, dung chung" khi 2 lan
        // gui/2 noi nhan cung tro ve 1 tenant.
        file2.put("objectKey", "vbdi-delivery/H05.01/2026/09/07/doc-1/aaa.pdf");
        file2.put("bucketName", "td-webapi-files");
        file2.put("originalFilename", "to_trinh.pdf");
        file2.put("size", 20480);

        assertThat(handler.execute(context("H06"), payload1).isSuccess()).isTrue();
        assertThat(handler.execute(context("H06"), payload2).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documents", Long.class)).isEqualTo(2L);
        List<Map<String, Object>> fileRows = jdbc.queryForList(
            "SELECT stored_filename, file_path FROM file_metadata ORDER BY uploaded_at");
        assertThat(fileRows).hasSize(2);
        // Cung file_path (object MinIO vat ly) nhung stored_filename PHAI khac nhau - khong dung
        // chung 1 gia tri du trung objectKey.
        assertThat(fileRows.get(0).get("file_path")).isEqualTo(fileRows.get(1).get("file_path"));
        assertThat(fileRows.get(0).get("stored_filename")).isNotEqualTo(fileRows.get(1).get("stored_filename"));
    }

    @Test
    void contactUnitRedeliveryDoesNotDuplicateDocument() {
        JsonNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);

        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();
        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue(); // redelivery

        long count = new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM documents", Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void coordinatorDoesNotCreateIncomingDocument() {
        JsonNode payload = basePayload(VbdiTaskConstants.GROUP_COORDINATOR);

        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();

        long count = new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM documents", Long.class);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void internalUnitDoesNotCreateIncomingDocumentEither() {
        // internal_unit = đơn vị nội bộ CỦA CHÍNH cơ quan gửi (không phải "thuộc tỉnh") - ngoài
        // phạm vi tính năng này (quyết định đã chốt với người dùng 06/09/2026).
        JsonNode payload = basePayload(VbdiTaskConstants.GROUP_INTERNAL_UNIT);

        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();

        long count = new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM documents", Long.class);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void createIncomingDocumentFlagOffSkipsDocumentCreation() {
        props.getTasks().getVbdi().setCreateIncomingDocument(false);
        JsonNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);

        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documents", Long.class)).isEqualTo(0L);
        // Dedup vẫn phải ghi bình thường - chỉ tắt phần tạo văn bản.
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM worker_vbdi_delivery_received WHERE send_log_id = ?", Long.class, "sl-1"))
            .isEqualTo(1L);
    }

    @Test
    void documentInsertFailureRollsBackDedupTooNoHalfState() {
        // Xoá cột dynamic_fields TẠM THỜI để mô phỏng lỗi ghi văn bản (schema lệch/lỗi DB tạm
        // thời) - xác nhận: dedup + văn bản nằm CHUNG 1 transaction, không để lại trạng thái nửa
        // vời "đã dedup nhưng chưa có văn bản" (đúng yêu cầu "tránh mất văn bản").
        JdbcTemplate adminJdbc = new JdbcTemplate(adminDataSource);
        adminJdbc.execute("ALTER TABLE documents DROP COLUMN dynamic_fields");
        try {
            JsonNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);

            assertThatThrownBy(() -> handler.execute(context("H06"), payload))
                .isInstanceOf(RuntimeException.class);

            long dedupCount = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM worker_vbdi_delivery_received WHERE send_log_id = ?", Long.class, "sl-1");
            assertThat(dedupCount).isEqualTo(0L); // rollback - KHÔNG để lại dedup mồ côi
        } finally {
            adminJdbc.execute("ALTER TABLE documents ADD COLUMN dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb");
        }
    }

    /**
     * Bugfix 07/09/2026 (phát hiện khi hoàn thiện broker phản hồi trạng thái — xem javadoc
     * {@code VbdiDeliveryTaskHandler#publishArrivedReply}): bản trước publish JSON PHẲNG tới routing
     * key {@code "vbdi.reply." + identifier} — KHÔNG khớp bất kỳ binding nào
     * ({@code RabbitConfig} chỉ bind {@code worker.<taskType>}/{@code worker.*}) — message bị
     * EXCHANGE ÂM THẦM HUỶ. Test này xác nhận bản sửa dựng ĐÚNG 1 {@code MessageEnvelope} hoàn
     * chỉnh (khớp {@code EnvelopeParser}, td-worker) và routing key đúng quy ước
     * {@code "worker." + taskType.replace('.','_')}.
     */
    @Test
    void contactUnitSuccessPublishesProperlyEnvelopedArrivedReply() throws Exception {
        var rabbitPublisherMock = mock(RabbitPublisher.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<RabbitPublisher> consumer = invocation.getArgument(0);
            consumer.accept(rabbitPublisherMock);
            return null;
        }).when(rabbitPublisher).ifAvailable(any());
        props.getRabbit().setEnabled(true);

        ObjectNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);
        payload.put("senderTenantId", "H05_01");

        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(rabbitPublisherMock).publish(
            exchangeCaptor.capture(), routingKeyCaptor.capture(), bodyCaptor.capture());

        assertThat(routingKeyCaptor.getValue()).isEqualTo("worker.vbdi_reply");

        JsonNode envelope = objectMapper.readTree(bodyCaptor.getValue());
        assertThat(envelope.get("messageId").asText()).isNotBlank();
        assertThat(envelope.get("taskType").asText()).isEqualTo("vbdi.reply");
        assertThat(envelope.get("createdAt").asText()).isNotBlank();
        assertThat(envelope.get("units")).hasSize(1);
        JsonNode unit = envelope.get("units").get(0);
        assertThat(unit.get("unitId").asText()).isEqualTo("sl-1");
        assertThat(unit.get("tenantId").asText()).isEqualTo("H05_01");
        JsonNode innerPayload = unit.get("payload");
        assertThat(innerPayload.get("sendLogId").asText()).isEqualTo("sl-1");
        assertThat(innerPayload.get("statusCode").asText()).isEqualTo(VbdiTaskConstants.STATUS_01_DA_DEN);
        assertThat(innerPayload.get("recipientGroup").asText()).isEqualTo(VbdiTaskConstants.GROUP_CONTACT_UNIT);
        // Bugfix 07/09/2026 (2, bao loi nguoi dung: cot "Noi dung" chua luu ID don vi nhan nhu ban
        // Domino) - contact_unit tao van ban THAT nen phai co statusDetails dang "ID: <uuid>".
        assertThat(innerPayload.get("statusDetails").asText()).startsWith("ID: ");
    }

    /**
     * Bugfix 07/09/2026 (2, xem javadoc test tren): nhanh internal_unit KHONG tao van ban that (chi
     * dedup) - khong co id nao de dinh kem, statusDetails phai VANG MAT (khong bi dien "ID: null").
     */
    @Test
    void internalUnitSuccessPublishesArrivedReplyWithoutStatusDetails() throws Exception {
        var rabbitPublisherMock = mock(RabbitPublisher.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<RabbitPublisher> consumer = invocation.getArgument(0);
            consumer.accept(rabbitPublisherMock);
            return null;
        }).when(rabbitPublisher).ifAvailable(any());
        props.getRabbit().setEnabled(true);

        ObjectNode payload = basePayload(VbdiTaskConstants.GROUP_INTERNAL_UNIT);
        payload.put("senderTenantId", "H05_01");

        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(rabbitPublisherMock).publish(any(), any(), bodyCaptor.capture());
        JsonNode innerPayload = objectMapper.readTree(bodyCaptor.getValue()).get("units").get(0).get("payload");
        assertThat(innerPayload.has("statusDetails")).isFalse();
    }

    @Test
    void nationalIncomingPublishesGatewayAckOnlyAfterCommit() throws Exception {
        var publisher = mock(RabbitPublisher.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<RabbitPublisher> consumer = invocation.getArgument(0);
            consumer.accept(publisher); return null;
        }).when(rabbitPublisher).ifAvailable(any());
        props.getRabbit().setEnabled(true);
        ObjectNode payload = basePayload(VbdiTaskConstants.GROUP_CONTACT_UNIT);
        payload.put("sendId", "transport-123");
        payload.put("documentId", "business-456");
        payload.put("senderTenantId", "H06");
        payload.put("nationalIncoming", true);
        payload.put("recipientIdentifier", "H56.01");
        assertThat(handler.execute(context("H06"), payload).isSuccess()).isTrue();
        ArgumentCaptor<String> route = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(publisher).publish(any(), route.capture(), body.capture());
        assertThat(route.getValue()).isEqualTo("worker.guinhanquocgia.receive.ack");
        JsonNode envelope = objectMapper.readTree(body.getValue());
        assertThat(envelope.path("taskType").asText()).isEqualTo("guinhanquocgia.receive.ack");
        assertThat(envelope.path("units").get(0).path("payload").path("documentId").asText())
            .isEqualTo("transport-123");
        assertThat(envelope.path("units").get(0).path("payload").path("recipientIdentifier").asText())
            .isEqualTo("H56.01");
    }
}
