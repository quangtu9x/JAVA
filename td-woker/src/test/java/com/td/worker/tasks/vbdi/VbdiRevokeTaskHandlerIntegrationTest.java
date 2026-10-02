package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.UnitResult;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test {@link VbdiRevokeTaskHandler} — port {@code ActionRevokeDocument} (Domino
 * {@code StatusProcess.ExecuteVBDIBussiness}, xem javadoc lớp handler).
 *
 * <p>Bối cảnh bugfix 08/09/2026 (người dùng báo lỗi thật): trước bản vá, bấm "Lấy lại VB" khiến
 * {@code AddVbdiSendLogUseCase} publish {@code vbdi.delivery} (sai) — td-worker log "DONE" nhưng văn
 * bản đến của đơn vị nhận VẪN còn nguyên trong danh sách. Test này xác nhận với taskType
 * {@code vbdi.revoke} MỚI: văn bản đến ĐÃ CÓ (khớp {@code parentid_send}) bị đánh dấu
 * {@code reply="1"} (đúng field {@code ViewQueryService#quamangnoiboFilter} dùng để loại khỏi hàng
 * đợi hoạt động) và phản hồi "Đã lấy lại" (status 101) được publish ngược về đơn vị gửi.
 *
 * <p>Testcontainers/Postgres thật (không H2) — cùng lý do {@code documents.dynamic_fields} là JSONB
 * thật đã ghi ở {@link VbdiDeliveryIncomingDocumentIntegrationTest}.
 */
class VbdiRevokeTaskHandlerIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static HikariDataSource adminDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TenantDataSourceManager tenantDataSources;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RabbitPublisher> rabbitPublisher = mock(ObjectProvider.class);
    private WorkerProperties props;
    private VbdiRevokeTaskHandler handler;

    @BeforeAll
    static void startContainer() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdworker_vbdi_revoke_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua VbdiRevokeTaskHandlerIntegrationTest: khong co Docker Linux container kha "
                    + "dung trong moi truong nay (" + ex.getMessage() + "). Test nay chay that trong "
                    + "CI (.github/workflows/ci.yml, ubuntu-latest).");
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-vbdi-revoke-test");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        config.setAutoCommit(false); // dung cau hinh tenant pool that (worker.tenant.pool-auto-commit=false).
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);

        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setPoolName("hikari-vbdi-revoke-test-admin");
        adminConfig.setJdbcUrl(postgres.getJdbcUrl());
        adminConfig.setUsername(postgres.getUsername());
        adminConfig.setPassword(postgres.getPassword());
        adminConfig.setDriverClassName(postgres.getDriverClassName());
        adminConfig.addDataSourceProperty("stringtype", "unspecified");
        adminDataSource = new HikariDataSource(adminConfig);

        // Subset THAT cua "documents" (giong VbdiDeliveryIncomingDocumentIntegrationTest).
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

        tenantDataSources = mock(TenantDataSourceManager.class);
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.of(dataSource));
        props = new WorkerProperties();
        props.getTasks().getVbdi().setEnabled(true);
        handler = new VbdiRevokeTaskHandler(props, tenantDataSources, objectMapper, rabbitPublisher);
    }

    private ObjectNode basePayload(String sendType) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sendLogId", "sl-revoke-1");
        node.put("documentId", "doc-1"); // = parentid_send / id_DocumentOut goc
        node.put("senderTenantId", "H05_01");
        node.put("senderIdentifier", "H05.01");
        node.put("recipientGroup", VbdiTaskConstants.GROUP_CONTACT_UNIT);
        node.put("recipientIdentifier", "H05.02");
        node.put("recipientName", "Sở NN&MT");
        node.put("sendType", sendType);
        return node;
    }

    private TaskContext context(String tenantId) {
        return new TaskContext("vbdi.revoke", "msg-1", "u1", "corr-1", tenantId,
            null, Map.of(), 5, null, Map.of());
    }

    /** Mô phỏng "văn bản đến chờ vào sổ" đã được {@code VbdenIncomingDocumentWriter} tạo trước đó. */
    private UUID insertExistingIncomingDocument(String parentidSend, String contactUnitIdentifier) {
        UUID id = UUID.randomUUID();
        String dynamicFields = """
            {"parentid_send": "%s", "contact_unit_identifier": "%s", "document_internet": "1", "send_log_id": "sl-send-1"}
            """.formatted(parentidSend, contactUnitIdentifier);
        new JdbcTemplate(adminDataSource).update("""
                INSERT INTO documents (id, title, document_type, status, dynamic_fields)
                VALUES (?, 'Van ban den cho vao so', 'vbden', '0', ?::jsonb)
                """, id, dynamicFields);
        return id;
    }

    /**
     * Bổ sung 08/09/2026 (3, yêu cầu người dùng): mô phỏng văn bản đến ĐÃ "vào sổ"
     * ({@code LuuVanBanDenUseCase} R4 {@code loaiKhoiDanhSachChoVaoSo}) — {@code contact_unit_identifier}
     * ĐÃ BỊ XOÁ khỏi dynamic_fields, chỉ còn {@code parentid_send} (KHÔNG bao giờ bị đụng bởi "vào sổ").
     */
    private UUID insertRegisteredIncomingDocument(String parentidSend) {
        UUID id = UUID.randomUUID();
        String dynamicFields = """
            {"parentid_send": "%s", "document_internet": "1", "document_number": "12", "document_register_id": "reg-1"}
            """.formatted(parentidSend);
        new JdbcTemplate(adminDataSource).update("""
                INSERT INTO documents (id, title, document_type, status, dynamic_fields)
                VALUES (?, 'Van ban den da vao so', 'vbden', '0', ?::jsonb)
                """, id, dynamicFields);
        return id;
    }

    /**
     * Bổ sung 21/09/2026 (bugfix "Lấy lại văn bản" cho ĐƠN VỊ NỘI BỘ — xem javadoc
     * {@link VbdiRevokeTaskHandler#revokeIncomingDocuments}): mô phỏng văn bản đến CHỜ VÀO SỔ tạo cho
     * 1 đơn vị nội bộ CỤ THỂ ({@code unit_process} = mảng 1 phần tử, KHÔNG phải {@code contact_unit_identifier}).
     */
    private UUID insertInternalUnitIncomingDocument(String parentidSend, String unitIdentifier) {
        UUID id = UUID.randomUUID();
        String dynamicFields = """
            {"parentid_send": "%s", "internal": "1", "unit_process": ["%s"], "document_internet": "1"}
            """.formatted(parentidSend, unitIdentifier);
        new JdbcTemplate(adminDataSource).update("""
                INSERT INTO documents (id, title, document_type, status, dynamic_fields)
                VALUES (?, 'Van ban den noi bo cho vao so', 'vbden', '0', ?::jsonb)
                """, id, dynamicFields);
        return id;
    }

    /**
     * Bổ sung 21/09/2026: mô phỏng văn bản đến nội bộ ĐÃ "vào sổ" ({@code LuuVanBanDenUseCase} R4) —
     * {@code unit_process} đã bị xoá rỗng, identifier chuyển sang {@code unit_process_registered}.
     */
    private UUID insertRegisteredInternalUnitIncomingDocument(String parentidSend, String unitIdentifier) {
        UUID id = UUID.randomUUID();
        String dynamicFields = """
            {"parentid_send": "%s", "internal": "1", "unit_process": [], "unit_process_registered": ["%s"], "document_internet": "1"}
            """.formatted(parentidSend, unitIdentifier);
        new JdbcTemplate(adminDataSource).update("""
                INSERT INTO documents (id, title, document_type, status, dynamic_fields)
                VALUES (?, 'Van ban den noi bo da vao so', 'vbden', '0', ?::jsonb)
                """, id, dynamicFields);
        return id;
    }

    /** Mô phỏng bản ghi {@code vbden_so} ({@code TiepNhanVbdenUseCase#REGISTER_ENTRY_DOCUMENT_TYPE}) liên kết. */
    private UUID insertSoVanBanEntry(UUID parentId) {
        UUID id = UUID.randomUUID();
        String dynamicFields = """
            {"parentid": "%s", "document_number": "12"}
            """.formatted(parentId);
        new JdbcTemplate(adminDataSource).update("""
                INSERT INTO documents (id, title, document_type, status, dynamic_fields)
                VALUES (?, 'Van ban den da vao so', 'vbden_so', '0', ?::jsonb)
                """, id, dynamicFields);
        return id;
    }

    /**
     * Bug đã xác nhận thật (log người dùng): "Lấy lại VB" phải ẩn văn bản đến khỏi danh sách hoạt
     * động — {@code reply="1"} là field {@code ViewQueryService#quamangnoiboFilter} dùng để loại trừ.
     */
    @Test
    void layLaiVanBanMarksExistingIncomingDocumentAsReplied() throws Exception {
        UUID docId = insertExistingIncomingDocument("doc-1", "H05.02");
        ObjectNode payload = basePayload("laylaivanban");

        UnitResult result = handler.execute(context("H05_02"), payload);
        assertThat(result.isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> dynamicFields = objectMapper.readValue(
            jdbc.queryForObject("SELECT dynamic_fields::text FROM documents WHERE id = ?", String.class, docId),
            Map.class);
        assertThat(dynamicFields.get("reply")).isEqualTo("1");
        assertThat(dynamicFields.get("is_revoke")).isEqualTo("1");
        assertThat(dynamicFields.get("delete_date")).asString().matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
        // KHONG tao them van ban moi nao (khac bug cu - dung chung taskType vbdi.delivery).
        Long totalDocs = jdbc.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
        assertThat(totalDocs).isEqualTo(1L);
    }

    /** "Thu hồi ngay" (nhãn khác, CÙNG cơ chế Domino RevokeDocumentOut.java) hoạt động y hệt. */
    @Test
    void thuHoiVanBanMarksExistingIncomingDocumentAsReplied() throws Exception {
        UUID docId = insertExistingIncomingDocument("doc-1", "H05.02");
        ObjectNode payload = basePayload("thuhoivanban");

        assertThat(handler.execute(context("H05_02"), payload).isSuccess()).isTrue();

        String reply = new JdbcTemplate(dataSource).queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, docId);
        assertThat(reply).isEqualTo("1");
    }

    /**
     * Không khớp {@code parentid_send} nào (vd coordinator — chưa từng tạo văn bản đến thật; hoặc
     * internal_unit nhưng KHÔNG có văn bản nào được tạo trước đó cho recipientIdentifier này).
     */
    @Test
    void noMatchingIncomingDocumentIsNoOpAndDoesNotPublishReply() throws Exception {
        // KHONG insert van ban den nao truoc.
        var rabbitPublisherMock = mock(RabbitPublisher.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<RabbitPublisher> consumer = invocation.getArgument(0);
            consumer.accept(rabbitPublisherMock);
            return null;
        }).when(rabbitPublisher).ifAvailable(any());
        props.getRabbit().setEnabled(true);

        ObjectNode payload = basePayload("laylaivanban");
        payload.put("recipientGroup", VbdiTaskConstants.GROUP_INTERNAL_UNIT);

        assertThat(handler.execute(context("H05_02"), payload).isSuccess()).isTrue();
        verifyNoInteractions(rabbitPublisherMock);
    }

    /**
     * Port {@code SendReplyToSender} — status_code="101"/"Đã lấy lại", CÙNG cơ chế envelope/routing
     * đã sửa đúng ở {@code VbdiDeliveryTaskHandler#publishArrivedReply} (bugfix routing 07/09/2026 (4)).
     */
    @Test
    void successfulRevokePublishesProperlyEnvelopedDaLayLaiReply() throws Exception {
        insertExistingIncomingDocument("doc-1", "H05.02");
        var rabbitPublisherMock = mock(RabbitPublisher.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<RabbitPublisher> consumer = invocation.getArgument(0);
            consumer.accept(rabbitPublisherMock);
            return null;
        }).when(rabbitPublisher).ifAvailable(any());
        props.getRabbit().setEnabled(true);

        ObjectNode payload = basePayload("laylaivanban");
        assertThat(handler.execute(context("H05_02"), payload).isSuccess()).isTrue();

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(rabbitPublisherMock).publish(any(), routingKeyCaptor.capture(), bodyCaptor.capture());

        assertThat(routingKeyCaptor.getValue()).isEqualTo("worker.vbdi_reply");

        JsonNode envelope = objectMapper.readTree(bodyCaptor.getValue());
        assertThat(envelope.get("taskType").asText()).isEqualTo("vbdi.reply");
        JsonNode unit = envelope.get("units").get(0);
        assertThat(unit.get("unitId").asText()).isEqualTo("sl-revoke-1");
        assertThat(unit.get("tenantId").asText()).isEqualTo("H05_01");
        JsonNode innerPayload = unit.get("payload");
        assertThat(innerPayload.get("sendLogId").asText()).isEqualTo("sl-revoke-1");
        assertThat(innerPayload.get("statusCode").asText()).isEqualTo(VbdiTaskConstants.STATUS_101_DA_LAY_LAI);
        assertThat(innerPayload.get("statusLabel").asText()).isEqualTo(VbdiTaskConstants.LABEL_DA_LAY_LAI);
        assertThat(innerPayload.get("recipientIdentifier").asText()).isEqualTo("H05.02");
    }

    /**
     * Bổ sung 08/09/2026 (3, yêu cầu người dùng "lấy lại tận gốc" — "có trường hợp gửi nhiều lần cùng
     * 1 văn bản xong gửi 1 lệnh để lấy lại"): 2 bản ghi "văn bản đến" CÙNG {@code parentid_send} (mô
     * phỏng gửi lại/gửi bổ sung 2 lần trước khi "Lấy lại" MỘT LẦN) — cả 2 phải bị lấy lại, không chỉ 1.
     */
    @Test
    void multipleIncomingDocumentsForSameSourceDocument_allGetRevoked() throws Exception {
        UUID doc1 = insertExistingIncomingDocument("doc-1", "H05.02");
        UUID doc2 = insertExistingIncomingDocument("doc-1", "H05.02");
        ObjectNode payload = basePayload("thuhoitoanbo");

        assertThat(handler.execute(context("H05_02"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (UUID id : List.of(doc1, doc2)) {
            String reply = jdbc.queryForObject(
                "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, id);
            assertThat(reply).as("document %s phai duoc lay lai", id).isEqualTo("1");
        }
    }

    /**
     * Bổ sung 08/09/2026 (3, xem javadoc lớp điểm 2 — bugfix quan trọng nhất phát hiện qua đối chiếu
     * DB tenant thật với người dùng): văn bản ĐÃ "vào sổ" ({@code LuuVanBanDenUseCase} xoá
     * {@code contact_unit_identifier}) VẪN phải lấy lại được — chỉ còn {@code parentid_send} để khớp.
     * Trước bản vá (3), điều kiện cũ đòi hỏi {@code contact_unit_identifier} còn nguyên sẽ khớp 0
     * dòng ở ca này (lỗi thật đã xác nhận).
     */
    @Test
    void alreadyRegisteredDocumentWithContactUnitIdentifierCleared_stillGetsRevoked() throws Exception {
        UUID docId = insertRegisteredIncomingDocument("doc-1");
        ObjectNode payload = basePayload("laylaivanban");

        assertThat(handler.execute(context("H05_02"), payload).isSuccess()).isTrue();

        String reply = new JdbcTemplate(dataSource).queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, docId);
        assertThat(reply).isEqualTo("1");
    }

    /**
     * Bổ sung 08/09/2026 (3, xem javadoc lớp điểm 3): văn bản đã vào sổ có 1 bản ghi con
     * {@code vbden_so} liên kết ({@code parentid} = id văn bản gốc) — "Lấy lại" phải đánh dấu CẢ 2
     * bản ghi (đúng Domino {@code ActionRevokeDocument} tra {@code sovanban_by_idvbden}), không chỉ
     * văn bản gốc.
     */
    @Test
    void revokesLinkedVbdenSoEntryToo() throws Exception {
        UUID rootId = insertRegisteredIncomingDocument("doc-1");
        UUID soId = insertSoVanBanEntry(rootId);
        ObjectNode payload = basePayload("laylaivanban");

        assertThat(handler.execute(context("H05_02"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String rootReply = jdbc.queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, rootId);
        String soReply = jdbc.queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, soId);
        assertThat(rootReply).isEqualTo("1");
        assertThat(soReply).as("ban ghi vbden_so lien ket cung phai bi lay lai").isEqualTo("1");
    }

    /** Bản ghi {@code vbden_so} của 1 văn bản KHÔNG liên quan (parentid khác) không bị đụng vào. */
    @Test
    void unrelatedVbdenSoEntryIsNotTouched() throws Exception {
        UUID rootId = insertRegisteredIncomingDocument("doc-1");
        UUID soId = insertSoVanBanEntry(rootId);
        UUID unrelatedRootId = UUID.randomUUID();
        UUID unrelatedSoId = insertSoVanBanEntry(unrelatedRootId); // parentid khac, khong lien quan
        ObjectNode payload = basePayload("laylaivanban");

        assertThat(handler.execute(context("H05_02"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, soId))
            .isEqualTo("1");
        assertThat(jdbc.queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, unrelatedSoId))
            .isNull();
    }

    /**
     * Bổ sung 21/09/2026 (bugfix người dùng: "kiểm tra Lấy lại văn bản đã hoàn thiện cho ĐƠN VỊ NỘI
     * BỘ chưa" — xem javadoc {@link VbdiRevokeTaskHandler#revokeIncomingDocuments}): văn bản gửi tới
     * CẢ "Phòng A" lẫn "Phòng B" (2 đơn vị nội bộ CHIA SẺ 1 tenant — tenant của chính cơ quan gửi,
     * KHÁC contact_unit) — "Lấy lại" chỉ chọn Phòng A KHÔNG được đụng tới văn bản đến của Phòng B.
     * Trước bản vá, điều kiện CHỈ {@code parentid_send} sẽ khớp CẢ 2 (lấy lại nhầm Phòng B).
     */
    @Test
    void layLaiInternalUnit_onlyRevokesTheSelectedUnitNotOtherUnitsInSameTenant() throws Exception {
        UUID phongADoc = insertInternalUnitIncomingDocument("doc-1", "H05.01.01");
        UUID phongBDoc = insertInternalUnitIncomingDocument("doc-1", "H05.01.02");
        ObjectNode payload = basePayload("laylaivanban");
        payload.put("recipientGroup", VbdiTaskConstants.GROUP_INTERNAL_UNIT);
        payload.put("recipientIdentifier", "H05.01.01");

        assertThat(handler.execute(context("H05_01"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String phongAReply = jdbc.queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, phongADoc);
        String phongBReply = jdbc.queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, phongBDoc);
        assertThat(phongAReply).as("Phong A duoc chon lay lai").isEqualTo("1");
        assertThat(phongBReply).as("Phong B KHONG duoc chon - khong duoc dung vao").isNull();
    }

    /**
     * Bổ sung 21/09/2026 (đối chứng test trên): văn bản đến nội bộ ĐÃ "vào sổ" ({@code unit_process}
     * rỗng, identifier chuyển sang {@code unit_process_registered}) VẪN phải lấy lại được — cùng lý
     * do đã áp dụng cho {@code contact_unit_identifier} (điểm 2 javadoc lớp).
     */
    @Test
    void layLaiInternalUnit_alreadyRegisteredDocument_stillGetsRevoked() throws Exception {
        UUID phongADoc = insertRegisteredInternalUnitIncomingDocument("doc-1", "H05.01.01");
        UUID phongBDoc = insertInternalUnitIncomingDocument("doc-1", "H05.01.02");
        ObjectNode payload = basePayload("laylaivanban");
        payload.put("recipientGroup", VbdiTaskConstants.GROUP_INTERNAL_UNIT);
        payload.put("recipientIdentifier", "H05.01.01");

        assertThat(handler.execute(context("H05_01"), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, phongADoc))
            .isEqualTo("1");
        assertThat(jdbc.queryForObject(
            "SELECT dynamic_fields->>'reply' FROM documents WHERE id = ?", String.class, phongBDoc))
            .as("Phong B KHONG duoc chon - khong duoc dung vao").isNull();
    }

    @Test
    void disabledTaskReturnsFailure() {
        props.getTasks().getVbdi().setEnabled(false);
        JsonNode payload = basePayload("laylaivanban");
        UnitResult result = handler.execute(context("H05_02"), payload);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void missingTenantDataSourceFails() {
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.empty());
        JsonNode payload = basePayload("laylaivanban");
        UnitResult result = handler.execute(context("H05_02"), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("H05_02");
    }
}
