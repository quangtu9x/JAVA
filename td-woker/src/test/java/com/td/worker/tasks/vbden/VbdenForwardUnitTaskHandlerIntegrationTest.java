package com.td.worker.tasks.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.UnitResult;
import com.td.worker.infrastructure.fileregistry.SharedFileRegistry;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.transport.rabbit.RabbitPublisher;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test {@link VbdenForwardUnitTaskHandler} — port {@code DocumentInProcess.java
 * #copyDocumentProcessToDocumentIn()} (Domino) phần TẠO bản sao "văn bản đến" tại tenant đích, xem
 * javadoc lớp handler.
 *
 * <p>Testcontainers/Postgres thật (không H2) — cùng lý do {@code documents.dynamic_fields} là JSONB
 * thật + {@code worker.tenant.pool-auto-commit=false} đã ghi ở các test vbdi tương tự.
 */
class VbdenForwardUnitTaskHandlerIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static HikariDataSource adminDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TenantDataSourceManager tenantDataSources;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RabbitPublisher> rabbitPublisher = mock(ObjectProvider.class);
    private WorkerProperties props;
    private VbdenForwardUnitTaskHandler handler;
    private SharedFileRegistry sharedFileRegistry;

    @BeforeAll
    static void startContainer() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdworker_vbden_forward_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua VbdenForwardUnitTaskHandlerIntegrationTest: khong co Docker Linux container "
                    + "kha dung trong moi truong nay (" + ex.getMessage() + "). Test nay chay that "
                    + "trong CI (.github/workflows/ci.yml, ubuntu-latest).");
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-vbden-forward-test");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        config.setAutoCommit(false);
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);

        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setPoolName("hikari-vbden-forward-test-admin");
        adminConfig.setJdbcUrl(postgres.getJdbcUrl());
        adminConfig.setUsername(postgres.getUsername());
        adminConfig.setPassword(postgres.getPassword());
        adminConfig.setDriverClassName(postgres.getDriverClassName());
        adminConfig.addDataSourceProperty("stringtype", "unspecified");
        adminDataSource = new HikariDataSource(adminConfig);

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

        // Bo sung 08/09/2026 (3) - subset THAT cua "file_metadata" (giong VbdiDeliveryIncoming
        // DocumentIntegrationTest), can cho insertFileMetadataRefs test duoc dung schema.
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
                description TEXT,
                is_shared BOOLEAN NOT NULL DEFAULT FALSE
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

        tenantDataSources = mock(TenantDataSourceManager.class);
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.of(dataSource));
        props = new WorkerProperties();
        props.getTasks().getVbden().setEnabled(true);
        sharedFileRegistry = mock(SharedFileRegistry.class);
        handler = new VbdenForwardUnitTaskHandler(props, tenantDataSources, objectMapper, rabbitPublisher,
            sharedFileRegistry);
    }

    private ObjectNode basePayload() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("originDocumentId", UUID.randomUUID().toString());
        node.put("originTenantId", "h05_01");
        node.put("targetIdentifier", "H05.02");
        node.put("targetName", "So NN&MT");
        node.put("actingUsername", "vanthu01");
        node.put("opinion", "Chuyen cho don vi ban xu ly");
        node.put("subject", "V/v phoi hop xu ly ho so");
        node.put("code", "123/QD");
        node.put("promulgationDate", "01/09/2026");
        node.put("deadlineDate", "15/09/2026");
        node.put("unitInCharge", "Bo Tai nguyen va Moi truong");
        node.put("unitInChargeIdentifier", "BTNMT");
        return node;
    }

    private TaskContext context(String tenantId) {
        return new TaskContext("vbden.forward_unit", "msg-1", "u1", "corr-1", tenantId,
            null, Map.of(), 5, null, Map.of());
    }

    @Test
    void successfulForward_createsMinimalIncomingDocument() throws Exception {
        JsonNode payload = basePayload();
        UnitResult result = handler.execute(context("h05_02"), payload);
        assertThat(result.isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> doc = jdbc.queryForList("SELECT * FROM documents").get(0);
        assertThat(doc.get("document_type")).isEqualTo("vbden");
        assertThat(doc.get("status")).isEqualTo("0");
        assertThat(doc.get("title")).isEqualTo("V/v phoi hop xu ly ho so");

        Map<String, Object> dynamicFields = objectMapper.readValue(
            doc.get("dynamic_fields").toString(), Map.class);
        assertThat(dynamicFields.get("origin_document_id")).isEqualTo(payload.get("originDocumentId").asText());
        assertThat(dynamicFields.get("origin_tenant_id")).isEqualTo("h05_01");
        assertThat(dynamicFields.get("code")).isEqualTo("123/QD");
        assertThat(dynamicFields.get("promulgation_date")).isEqualTo("01/09/2026");
        assertThat(dynamicFields.get("forwarded_by")).isEqualTo("vanthu01");
        assertThat(dynamicFields.get("forwarded_opinion")).isEqualTo("Chuyen cho don vi ban xu ly");
        assertThat((String) dynamicFields.get("received_date")).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
        assertThat(dynamicFields.get("deadline_date")).isEqualTo("15/09/2026");
        assertThat(dynamicFields.get("unit_in_charge")).isEqualTo("Bo Tai nguyen va Moi truong");
        assertThat(dynamicFields.get("unit_in_charge_identifier")).isEqualTo("BTNMT");
    }

    /**
     * Bugfix 08/09/2026 (2, người dùng test thật: "văn thư/người đại diện nhận đang không thấy văn
     * bản ở bất kỳ đâu") — xem javadoc lớp: {@code identifier_creator} là field DUY NHẤT mọi view
     * org-scoped của vbden dùng để lọc "văn bản của tổ chức tôi" — PHẢI set đúng
     * {@code targetIdentifier} (KHÔNG phải origin), không forward từ payload.
     */
    @Test
    void createdCopy_hasIdentifierCreatorSetToTargetIdentifier_soOrgScopedViewsCanFindIt() throws Exception {
        JsonNode payload = basePayload();
        assertThat(handler.execute(context("h05_02"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> dynamicFields = objectMapper.readValue(
            jdbc.queryForList("SELECT * FROM documents").get(0).get("dynamic_fields").toString(), Map.class);
        assertThat(dynamicFields.get("identifier_creator")).isEqualTo("H05.02");
    }

    /**
     * Bổ sung 08/09/2026 (3, người dùng: "đã thấy trong 'Văn bản đến Cơ quan' nhưng chưa thấy ở
     * trong 'Văn bản đến trong tỉnh'") — xem javadoc lớp: bản sao PHẢI khớp
     * {@code ViewQueryService#quamangnoiboFilter} (contact_unit_identifier + document_internet="1"),
     * KHÔNG chỉ {@code identifier_creator}.
     */
    @Test
    void createdCopy_matchesQuamangnoiboFilterFields() throws Exception {
        JsonNode payload = basePayload();
        assertThat(handler.execute(context("h05_02"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> dynamicFields = objectMapper.readValue(
            jdbc.queryForList("SELECT * FROM documents").get(0).get("dynamic_fields").toString(), Map.class);
        assertThat(dynamicFields.get("contact_unit_identifier")).isEqualTo("H05.02");
        assertThat(dynamicFields.get("document_internet")).isEqualTo("1");
        assertThat(dynamicFields).doesNotContainKey("reply"); // chua bi lay lai - phai con hien thi.
    }

    /**
     * Bổ sung 08/09/2026 (3, người dùng: "thiếu files nữa, nhưng không phải nhân ra mà đọc chung với
     * đơn vị chuyển xử lý luôn") — xem javadoc lớp: {@code file_metadata} MỚI trỏ THẲNG
     * {@code objectKey}/{@code bucketName} của văn bản gốc, KHÔNG copy MinIO.
     */
    @Test
    void createdCopy_insertsFileMetadataRowsPointingAtSameObjectsAsOrigin() throws Exception {
        ObjectNode payload = basePayload();
        ArrayNode files = payload.putArray("files");
        ObjectNode file1 = files.addObject();
        file1.put("objectKey", "documents/2026/09/08/origin-doc/aaa.pdf");
        file1.put("bucketName", "td-webapi-files");
        file1.put("originalFilename", "to_trinh.pdf");
        file1.put("size", 20480);
        file1.put("contentType", "application/pdf");
        file1.put("fileExtension", "pdf");

        assertThat(handler.execute(context("h05_02"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> doc = jdbc.queryForList("SELECT * FROM documents").get(0);
        UUID documentId = (UUID) doc.get("id");

        List<Map<String, Object>> fileRows = jdbc.queryForList(
            "SELECT * FROM file_metadata WHERE document_id = ?", documentId);
        assertThat(fileRows).hasSize(1);
        assertThat(fileRows.get(0).get("original_filename")).isEqualTo("to_trinh.pdf");
        // "doc chung" - file_path/bucket_name TRO THANG object cua van ban goc, KHONG copy sang vi
        // tri moi (khac han vbdi/PrepareVbdiDeliveryFilesUseCase).
        assertThat(fileRows.get(0).get("file_path")).isEqualTo("documents/2026/09/08/origin-doc/aaa.pdf");
        assertThat(fileRows.get(0).get("bucket_name")).isEqualTo("td-webapi-files");
        assertThat(fileRows.get(0).get("file_category")).isEqualTo("DOCUMENT");
        // Yeu cau nguoi dung 08/09/2026 ("khong cho xoa dinh kem goc"): dong file_metadata cua ban
        // sao PHAI danh dau is_shared=TRUE de DeleteFileUseCaseImpl chan xoa (xem migration V1.0.50).
        assertThat(fileRows.get(0).get("is_shared")).isEqualTo(true);
        // 22/10/2026: tham chieu vao so dang ky tep dung chung - object goc chi bi xoa khi het don vi tham chieu.
        verify(sharedFileRegistry).registerQuietly("h05_02", SharedFileRegistry.SOURCE_FORWARD_UNIT, List.of(
            new SharedFileRegistry.SharedFileRef((UUID) fileRows.get(0).get("id"), documentId, "td-webapi-files",
                "documents/2026/09/08/origin-doc/aaa.pdf")));

        Map<String, Object> dynamicFields = objectMapper.readValue(doc.get("dynamic_fields").toString(), Map.class);
        List<Map<String, Object>> filesJson = (List<Map<String, Object>>) dynamicFields.get("files");
        assertThat(filesJson).hasSize(1);
        assertThat(filesJson.get(0)).containsKeys("name", "size", "type", "file_id");
        assertThat(UUID.fromString((String) filesJson.get(0).get("file_id")))
            .isEqualTo(fileRows.get(0).get("id"));
    }

    /** Không có {@code files} nào trong payload — vẫn tạo văn bản bình thường, không lỗi. */
    @Test
    void createdCopy_withoutFiles_doesNotFailAndHasNoFileMetadataRows() throws Exception {
        JsonNode payload = basePayload();
        assertThat(handler.execute(context("h05_02"), payload).isSuccess()).isTrue();

        assertThat(new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM file_metadata", Long.class)).isEqualTo(0L);
    }

    @Test
    void missingSubject_fallsBackToPlaceholderTitle() throws Exception {
        ObjectNode payload = basePayload();
        payload.remove("subject");

        assertThat(handler.execute(context("h05_02"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String title = (String) jdbc.queryForList("SELECT * FROM documents").get(0).get("title");
        assertThat(title).contains(payload.get("originDocumentId").asText());
    }

    @Test
    void successfulForward_publishesProperlyEnvelopedReplyToOriginTenant() throws Exception {
        var rabbitPublisherMock = mock(RabbitPublisher.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<RabbitPublisher> consumer = invocation.getArgument(0);
            consumer.accept(rabbitPublisherMock);
            return null;
        }).when(rabbitPublisher).ifAvailable(any());
        props.getRabbit().setEnabled(true);

        JsonNode payload = basePayload();
        assertThat(handler.execute(context("h05_02"), payload).isSuccess()).isTrue();

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(rabbitPublisherMock).publish(any(), routingKeyCaptor.capture(), bodyCaptor.capture());
        assertThat(routingKeyCaptor.getValue()).isEqualTo("worker.vbden_forward_unit_reply");

        JsonNode envelope = objectMapper.readTree(bodyCaptor.getValue());
        assertThat(envelope.get("taskType").asText()).isEqualTo("vbden.forward_unit.reply");
        JsonNode unit = envelope.get("units").get(0);
        assertThat(unit.get("tenantId").asText()).isEqualTo("h05_01");
        JsonNode innerPayload = unit.get("payload");
        assertThat(innerPayload.get("originDocumentId").asText()).isEqualTo(payload.get("originDocumentId").asText());
        assertThat(innerPayload.get("targetIdentifier").asText()).isEqualTo("H05.02");
        assertThat(innerPayload.get("createdTenantId").asText()).isEqualTo("h05_02");
        assertThat(innerPayload.get("createdDocumentId").asText()).isNotBlank();
    }

    /**
     * 23/10/2026 (chạy nhiều instance): cùng 1 unit chạy lại (redelivery/lease hết hạn) — KHÔNG tạo bản
     * sao thứ 2, không nhân đôi file_metadata; reply gửi lại mang CÙNG unitId. Unit KHÁC (lượt chuyển xử
     * lý mới) vẫn tạo bản sao mới như trước.
     */
    @Test
    void rerunOfSameUnit_doesNotDuplicateCopy_butNewUnitStillCreatesNewCopy() throws Exception {
        var rabbitPublisherMock = mock(RabbitPublisher.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<RabbitPublisher> consumer = invocation.getArgument(0);
            consumer.accept(rabbitPublisherMock);
            return null;
        }).when(rabbitPublisher).ifAvailable(any());
        props.getRabbit().setEnabled(true);

        ObjectNode payload = basePayload();
        ObjectNode file1 = payload.putArray("files").addObject();
        file1.put("objectKey", "documents/2026/10/23/origin/a.pdf");
        file1.put("bucketName", "td-webapi-files");
        file1.put("originalFilename", "a.pdf");
        file1.put("size", 10);
        file1.put("fileExtension", "pdf");

        assertThat(handler.execute(context("h05_02"), payload).isSuccess()).isTrue();
        assertThat(handler.execute(context("h05_02"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documents", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_metadata", Long.class)).isEqualTo(1L);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(rabbitPublisherMock, org.mockito.Mockito.times(2))
            .publish(any(), any(), bodyCaptor.capture());
        String unitId1 = objectMapper.readTree(bodyCaptor.getAllValues().get(0)).at("/units/0/unitId").asText();
        String unitId2 = objectMapper.readTree(bodyCaptor.getAllValues().get(1)).at("/units/0/unitId").asText();
        assertThat(unitId1).isNotBlank().isEqualTo(unitId2);

        TaskContext otherUnit = new TaskContext("vbden.forward_unit", "msg-2", "u2", "corr-2", "h05_02",
            null, Map.of(), 5, null, Map.of());
        assertThat(handler.execute(otherUnit, payload).isSuccess()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documents", Long.class)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_metadata", Long.class)).isEqualTo(2L);
    }

    @Test
    void missingRequiredFieldReturnsFailure() {
        ObjectNode payload = basePayload();
        payload.remove("targetIdentifier");
        UnitResult result = handler.execute(context("h05_02"), payload);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void disabledTaskReturnsFailure() {
        props.getTasks().getVbden().setEnabled(false);
        JsonNode payload = basePayload();
        UnitResult result = handler.execute(context("h05_02"), payload);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void missingTenantDataSourceFails() {
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.empty());
        JsonNode payload = basePayload();
        UnitResult result = handler.execute(context("h05_02"), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("h05_02");
    }
}
