package com.td.worker.tasks.vbdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.UnitResult;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test {@link VbdiReplyTaskHandler} — trước 06/09/2026 KHÔNG có test nào cho handler này; đồng
 * thời xác nhận bugfix "worker_vbdi_reply_log không bao giờ thực sự tồn tại" (cùng nguyên nhân gốc
 * đã phát hiện ở {@link VbdiDeliveryTaskHandler}: {@code worker.tenant.pool-auto-commit} mặc định
 * {@code false} — DDL/DML chạy ngoài transaction Spring quản lý bị HikariCP tự rollback khi trả
 * connection về pool). Testcontainers/Postgres thật, cùng lý do H2 không dùng được đã ghi ở
 * {@link VbdiDeliveryIncomingDocumentIntegrationTest}.
 */
class VbdiReplyTaskHandlerIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    /** DataSource dùng bởi handler dưới test - autoCommit=false, ĐÚNG cấu hình tenant pool thật. */
    private static HikariDataSource dataSource;
    /** DataSource RIÊNG cho test harness (reset bảng) - autoCommit=true mặc định. */
    private static HikariDataSource adminDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TenantDataSourceManager tenantDataSources;
    private WorkerProperties props;
    private VbdiReplyTaskHandler handler;

    @BeforeAll
    static void startContainer() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdworker_vbdi_reply_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua VbdiReplyTaskHandlerIntegrationTest: khong co Docker Linux container kha "
                    + "dung trong moi truong nay (" + ex.getMessage() + "). Test nay chay that trong "
                    + "CI (.github/workflows/ci.yml, ubuntu-latest).");
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-vbdi-reply-test");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        // Bugfix 06/09/2026: tái tạo ĐÚNG worker.tenant.pool-auto-commit=false của tenant pool thật.
        config.setAutoCommit(false);
        dataSource = new HikariDataSource(config);

        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setPoolName("hikari-vbdi-reply-test-admin");
        adminConfig.setJdbcUrl(postgres.getJdbcUrl());
        adminConfig.setUsername(postgres.getUsername());
        adminConfig.setPassword(postgres.getPassword());
        adminConfig.setDriverClassName(postgres.getDriverClassName());
        adminDataSource = new HikariDataSource(adminConfig); // autoCommit=true mặc định

        // Bo sung 07/09/2026 - subset THAT cua "vbdi_send_log_documents" (V1.0.33, td-web migration)
        // - du cot cho insertSendLogEntry() test duoc dung schema.
        new JdbcTemplate(adminDataSource).execute("""
            CREATE TABLE vbdi_send_log_documents (
                id UUID PRIMARY KEY,
                parent_id UUID NOT NULL,
                send_type VARCHAR(50),
                recipient_group VARCHAR(50),
                recipient_identifier VARCHAR(100),
                recipient_name VARCHAR(300),
                mailin VARCHAR(100),
                status VARCHAR(100),
                content TEXT,
                user_creator VARCHAR(100),
                created_by UUID,
                created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                last_modified_by UUID,
                last_modified_on TIMESTAMP WITH TIME ZONE,
                deleted_on TIMESTAMP WITH TIME ZONE,
                deleted_by UUID
            )
            """);

        // Bo sung 07/09/2026 (3), sua lai (4): subset THAT cua "vbdi_documents" (bang VAT LY RIENG
        // cua module vbdi, KHONG phai "documents" dung chung - xem javadoc VbdiDocument/bugfix (4)
        // o markOriginalVbdiDocumentReplied) - can cho test duoc: nhanh "tralai" ghi
        // is_document_reply=1 NGUOC vao van ban vbdi goc (dynamic_fields JSONB).
        new JdbcTemplate(adminDataSource).execute("""
            CREATE TABLE vbdi_documents (
                id UUID PRIMARY KEY,
                title VARCHAR(300) NOT NULL,
                document_type VARCHAR(100),
                status VARCHAR(50),
                dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
                created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
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
        new JdbcTemplate(adminDataSource).execute("DROP TABLE IF EXISTS worker_vbdi_reply_log");
        new JdbcTemplate(adminDataSource).execute("TRUNCATE TABLE vbdi_send_log_documents");
        new JdbcTemplate(adminDataSource).execute("TRUNCATE TABLE vbdi_documents");

        tenantDataSources = mock(TenantDataSourceManager.class);
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.of(dataSource));
        props = new WorkerProperties();
        props.getTasks().getVbdi().setEnabled(true);
        handler = new VbdiReplyTaskHandler(props, tenantDataSources, objectMapper);
    }

    @AfterEach
    void resetTables() {
        if (adminDataSource != null) {
            new JdbcTemplate(adminDataSource).execute("DROP TABLE IF EXISTS worker_vbdi_reply_log");
        }
    }

    private ObjectNode basePayload() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sendLogId", "sl-1");
        node.put("documentId", "doc-1");
        node.put("statusCode", VbdiTaskConstants.STATUS_01_DA_DEN);
        node.put("statusLabel", VbdiTaskConstants.LABEL_DA_DEN);
        node.put("recipientIdentifier", "recv-1");
        return node;
    }

    private TaskContext context() {
        return context("u1");
    }

    // Bo sung 07/09/2026 (5, xem javadoc VbdiReplyTaskHandler): unitId gio la DEDUP KEY THAT (thay
    // (send_log_id, status_code)) - test nao mo phong NHIEU SU KIEN KHAC NHAU (khong phai redelivery
    // CHINH 1 message) phai dung unitId KHAC nhau cho moi lan goi, dung nhu producer thuc te sinh moi
    // ngau nhien moi lan publish.
    private TaskContext context(String unitId) {
        return new TaskContext("vbdi.reply", "msg-1", unitId, "corr-1", "H05",
            null, Map.of(), 5, null, Map.of());
    }

    /**
     * Mô phỏng dòng "Gửi văn bản" ĐÃ có sẵn (do {@code AddVbdiSendLogUseCase} tạo lúc gửi ban đầu) —
     * dùng {@code adminDataSource} (autoCommit=true) để dữ liệu setup này THẬT SỰ persist (khác SUT
     * đang test qua {@code dataSource}, autoCommit=false).
     */
    private void insertOriginalSendLogRow(UUID id, UUID parentId, String recipientGroup,
            String recipientIdentifier, String recipientName) {
        new JdbcTemplate(adminDataSource).update("""
                INSERT INTO vbdi_send_log_documents
                 (id, parent_id, send_type, recipient_group, recipient_identifier, recipient_name, status, user_creator)
                VALUES (?, ?, 'senddocument', ?, ?, ?, 'Gửi văn bản', 'vanthu01')
                """, id, parentId, recipientGroup, recipientIdentifier, recipientName);
    }

    @Test
    void replyActuallyPersistsAfterCommit() {
        // Bugfix 06/09/2026: trước đây bảng worker_vbdi_reply_log KHÔNG BAO GIỜ commit thật (bị
        // HikariCP rollback khi trả connection về pool) - dòng insert biến mất ngay sau khi
        // execute() trả về, dù UnitResult.success(). Xác nhận bằng CONNECTION MỚI (mô phỏng đúng
        // hành vi pool thật - mỗi lần JdbcTemplate mượn 1 connection khác).
        JsonNode payload = basePayload();
        UnitResult result = handler.execute(context(), payload);
        assertThat(result.isSuccess()).isTrue();

        Long count = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM worker_vbdi_reply_log WHERE send_log_id = ? AND status_code = ?",
            Long.class, "sl-1", VbdiTaskConstants.STATUS_01_DA_DEN);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void redeliveryOfSameStatusIsIdempotent() {
        JsonNode payload = basePayload();

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();
        assertThat(handler.execute(context(), payload).isSuccess()).isTrue(); // redelivery

        Long count = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM worker_vbdi_reply_log WHERE send_log_id = ?", Long.class, "sl-1");
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void differentStatusCodesForSameSendLogIdAreKeptSeparately() {
        ObjectNode arrived = basePayload();
        ObjectNode received = basePayload();
        received.put("statusCode", VbdiTaskConstants.STATUS_03_DA_TIEP_NHAN);

        // unitId KHAC nhau - 2 su kien THAT SU khac nhau (khong phai redelivery cung 1 message).
        assertThat(handler.execute(context("u1"), arrived).isSuccess()).isTrue();
        assertThat(handler.execute(context("u2"), received).isSuccess()).isTrue();

        Long count = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM worker_vbdi_reply_log WHERE send_log_id = ?", Long.class, "sl-1");
        assertThat(count).isEqualTo(2L);
    }

    /**
     * Bugfix 07/09/2026 (5, báo lỗi người dùng: "Trả lại" lần 2/3 KHÔNG ghi vết) — xem javadoc
     * {@link VbdiReplyTaskHandler}: dedup PHẢI theo {@code unitId} (sinh mới mỗi lần publish), KHÔNG
     * theo {@code (sendLogId, statusCode)} — văn thư bấm "Trả lại" 2 LẦN KHÁC NHAU (unitId khác,
     * CÙNG sendLogId/statusCode="tralai") phải ghi ĐỦ 2 dòng vào "Nhật ký gửi nhận", không bị coi là
     * trùng lặp.
     */
    @Test
    void traLaiClickedTwice_shouldRecordBothOccurrencesSeparately() {
        UUID sendLogRowId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        insertOriginalSendLogRow(sendLogRowId, parentId, "contact_unit", "H05.02", "Sở NN&MT");
        insertOriginalVbdiDocument(parentId);

        ObjectNode payload = basePayload();
        payload.put("sendLogId", sendLogRowId.toString());
        payload.put("statusCode", VbdiTaskConstants.STATUS_TRA_LAI);
        payload.put("statusLabel", VbdiTaskConstants.LABEL_TRA_LAI);

        // 2 lan bam "Tra lai" THAT SU khac nhau - unitId khac (dung producer thuc te: UUID.randomUUID()
        // moi lan publish), CUNG sendLogId/statusCode (khong doi suot doi van ban).
        assertThat(handler.execute(context("reply-unit-1"), payload).isSuccess()).isTrue();
        assertThat(handler.execute(context("reply-unit-2"), payload).isSuccess()).isTrue();

        Long count = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM vbdi_send_log_documents WHERE parent_id = ? AND send_type = 'phan_hoi_trang_thai'",
            Long.class, parentId);
        assertThat(count).isEqualTo(2L);
    }

    /**
     * Bổ sung 07/09/2026 (yêu cầu người dùng: hoàn thiện broker phản hồi trạng thái, lưu vào "Nhật
     * ký gửi nhận" — xem javadoc lớp + {@link VbdiReplyTaskHandler#insertSendLogEntry}): phản hồi
     * HỢP LỆ (lần đầu) phải ghi THÊM 1 dòng MỚI vào {@code vbdi_send_log_documents}, lấy
     * {@code parent_id}/{@code recipient_group}/{@code recipient_identifier}/{@code recipient_name}
     * từ CHÍNH dòng gốc (khớp {@code sendLogId}), KHÔNG đụng gì tới dòng gốc (append-only).
     */
    @Test
    void replyAppendsNewSendLogEntryCopyingRecipientInfoFromOriginalRow() {
        UUID sendLogRowId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        insertOriginalSendLogRow(sendLogRowId, parentId, "contact_unit", "H05.02", "Sở NN&MT");

        ObjectNode payload = basePayload();
        payload.put("sendLogId", sendLogRowId.toString());
        payload.put("statusCode", VbdiTaskConstants.STATUS_03_DA_TIEP_NHAN);
        payload.put("statusLabel", VbdiTaskConstants.LABEL_DA_TIEP_NHAN);
        payload.put("statusDetails", "Văn thư đã vào sổ");
        payload.put("actorDisplayName", "vanthu02");

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        List<Map<String, Object>> rows = new JdbcTemplate(dataSource).queryForList(
            "SELECT * FROM vbdi_send_log_documents WHERE parent_id = ? AND send_type = 'phan_hoi_trang_thai'",
            parentId);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("recipient_group")).isEqualTo("contact_unit");
        assertThat(row.get("recipient_identifier")).isEqualTo("H05.02");
        assertThat(row.get("recipient_name")).isEqualTo("Sở NN&MT");
        assertThat(row.get("status")).isEqualTo(VbdiTaskConstants.LABEL_DA_TIEP_NHAN);
        // Bugfix 07/09/2026 (2, xem javadoc VbdiReplyTaskHandler#buildContent): STATUS_03 tu dung
        // recipientName (tu dong goc) de dung dung cong thuc Domino that
        // (StatusProcess.addResponseStatusToSender), KHONG dung nguyen statusDetails producer gui.
        assertThat(row.get("content")).isEqualTo("Đơn vị Sở NN&MT đã đồng ý yêu cầu");
        assertThat(row.get("user_creator")).isEqualTo("vanthu02");

        // Dong GOC khong bi dung cham (van dung 1 dong, khong bi ghi de).
        Long originalCount = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM vbdi_send_log_documents WHERE id = ?", Long.class, sendLogRowId);
        assertThat(originalCount).isEqualTo(1L);
    }

    /**
     * Bổ sung 07/09/2026 (2, báo lỗi người dùng: cột "Nội dung" chưa lưu ID đơn vị nhận như bản
     * Domino — xem javadoc {@link VbdiReplyTaskHandler#buildContent}): STATUS_01 nối thẳng
     * {@code statusDetails} ("ID: ...", do {@code VbdiDeliveryTaskHandler} truyền vào) sau nhãn.
     */
    @Test
    void status01ContentIncludesCreatedDocumentId() {
        UUID sendLogRowId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        insertOriginalSendLogRow(sendLogRowId, parentId, "contact_unit", "H05.02", "Sở NN&MT");

        ObjectNode payload = basePayload();
        payload.put("sendLogId", sendLogRowId.toString());
        payload.put("statusDetails", "ID: 6b5174a5-d3fb-2ecf-4725-8e620019d3bb");

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        String content = new JdbcTemplate(dataSource).queryForObject(
            "SELECT content FROM vbdi_send_log_documents WHERE parent_id = ? AND send_type = 'phan_hoi_trang_thai'",
            String.class, parentId);
        assertThat(content).isEqualTo("Đã đến, ID: 6b5174a5-d3fb-2ecf-4725-8e620019d3bb");
    }

    /** STATUS_04 nối {@code statusDetails} (danh sách người mới được giao) trong ngoặc sau nhãn. */
    @Test
    void status04ContentIncludesAssigneesInParentheses() {
        UUID sendLogRowId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        insertOriginalSendLogRow(sendLogRowId, parentId, "contact_unit", "H05.02", "Sở NN&MT");

        ObjectNode payload = basePayload();
        payload.put("sendLogId", sendLogRowId.toString());
        payload.put("statusCode", VbdiTaskConstants.STATUS_04_PHAN_CONG);
        payload.put("statusLabel", VbdiTaskConstants.LABEL_PHAN_CONG_XU_LY);
        payload.put("statusDetails", "nguyenvana; tranvanb");

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        String content = new JdbcTemplate(dataSource).queryForObject(
            "SELECT content FROM vbdi_send_log_documents WHERE parent_id = ? AND send_type = 'phan_hoi_trang_thai'",
            String.class, parentId);
        assertThat(content).isEqualTo("Phân công xử lý (nguyenvana; tranvanb)");
    }

    /** STATUS_05 không có opinion riêng ở Domino thật — giữ nguyên nhãn trơn. */
    @Test
    void status05ContentIsPlainLabel() {
        UUID sendLogRowId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        insertOriginalSendLogRow(sendLogRowId, parentId, "contact_unit", "H05.02", "Sở NN&MT");

        ObjectNode payload = basePayload();
        payload.put("sendLogId", sendLogRowId.toString());
        payload.put("statusCode", VbdiTaskConstants.STATUS_05_DANG_XU_LY);
        payload.put("statusLabel", VbdiTaskConstants.LABEL_DANG_XU_LY);

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        String content = new JdbcTemplate(dataSource).queryForObject(
            "SELECT content FROM vbdi_send_log_documents WHERE parent_id = ? AND send_type = 'phan_hoi_trang_thai'",
            String.class, parentId);
        assertThat(content).isEqualTo("Đang xử lý");
    }

    /** STATUS_06 nối {@code statusDetails} (ý kiến, nếu có) sau dấu hai chấm. */
    @Test
    void status06ContentIncludesOpinionAfterColon() {
        UUID sendLogRowId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        insertOriginalSendLogRow(sendLogRowId, parentId, "contact_unit", "H05.02", "Sở NN&MT");

        ObjectNode payload = basePayload();
        payload.put("sendLogId", sendLogRowId.toString());
        payload.put("statusCode", VbdiTaskConstants.STATUS_06_HOAN_THANH);
        payload.put("statusLabel", VbdiTaskConstants.LABEL_HOAN_THANH);
        payload.put("statusDetails", "Đã xử lý xong");

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        String content = new JdbcTemplate(dataSource).queryForObject(
            "SELECT content FROM vbdi_send_log_documents WHERE parent_id = ? AND send_type = 'phan_hoi_trang_thai'",
            String.class, parentId);
        assertThat(content).isEqualTo("Hoàn thành: Đã xử lý xong");
    }

    /** Mô phỏng văn bản {@code vbdi} (văn bản đi) GỐC đã tồn tại phía đơn vị gửi. */
    private void insertOriginalVbdiDocument(UUID id) {
        new JdbcTemplate(adminDataSource).update("""
                INSERT INTO vbdi_documents (id, title, document_type, status, dynamic_fields)
                VALUES (?, 'Van ban di goc', 'vbdi', '2', '{}'::jsonb)
                """, id);
    }

    /**
     * Bổ sung 07/09/2026 (3, người dùng sửa lại: "Từ chối/Trả lại" PHẢI phản hồi trạng thái giống
     * 01/03, đối chiếu {@code StatusProcess.java} thật) — statusCode phi chuẩn {@code "tralai"}:
     * nội dung CỐ ĐỊNH "Đơn vị {tên đơn vị} đã trả lại văn bản" (bỏ qua statusDetails/opinion), VÀ
     * ghi ngược {@code is_document_reply=1} lên CHÍNH văn bản {@code vbdi} gốc (parent_id).
     */
    @Test
    void traLaiContentIsFixedTemplateAndMarksOriginalVbdiDocumentReplied() {
        UUID sendLogRowId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        insertOriginalSendLogRow(sendLogRowId, parentId, "contact_unit", "H05.02", "Sở NN&MT");
        insertOriginalVbdiDocument(parentId);

        ObjectNode payload = basePayload();
        payload.put("sendLogId", sendLogRowId.toString());
        payload.put("statusCode", VbdiTaskConstants.STATUS_TRA_LAI);
        payload.put("statusLabel", VbdiTaskConstants.LABEL_TRA_LAI);
        payload.put("statusDetails", "Sai doi tuong nhan"); // phai bi BO QUA, dung Domino that

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        String content = new JdbcTemplate(dataSource).queryForObject(
            "SELECT content FROM vbdi_send_log_documents WHERE parent_id = ? AND send_type = 'phan_hoi_trang_thai'",
            String.class, parentId);
        assertThat(content).isEqualTo("Đơn vị Sở NN&MT đã trả lại văn bản");

        String isDocumentReply = new JdbcTemplate(dataSource).queryForObject(
            "SELECT dynamic_fields->>'is_document_reply' FROM vbdi_documents WHERE id = ?",
            String.class, parentId);
        assertThat(isDocumentReply).isEqualTo("1");
    }

    /** Không tìm thấy văn bản vbdi gốc (vd đã bị xoá) — chỉ log cảnh báo, KHÔNG fail unit. */
    @Test
    void traLai_whenOriginalVbdiDocumentMissing_shouldSkipGracefullyWithoutFailingUnit() {
        UUID sendLogRowId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        insertOriginalSendLogRow(sendLogRowId, parentId, "contact_unit", "H05.02", "Sở NN&MT");
        // KHONG insertOriginalVbdiDocument - gia lap van ban goc da bi xoa.

        ObjectNode payload = basePayload();
        payload.put("sendLogId", sendLogRowId.toString());
        payload.put("statusCode", VbdiTaskConstants.STATUS_TRA_LAI);
        payload.put("statusLabel", VbdiTaskConstants.LABEL_TRA_LAI);

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        Long sendLogCount = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM vbdi_send_log_documents WHERE parent_id = ? AND send_type = 'phan_hoi_trang_thai'",
            Long.class, parentId);
        assertThat(sendLogCount).isEqualTo(1L); // "Nhat ky gui nhan" van ghi thanh cong binh thuong
    }

    /** Redelivery (retry) CÙNG statusCode - KHÔNG được ghi thêm dòng "Nhật ký gửi nhận" trùng lặp. */
    @Test
    void redeliveryDoesNotDuplicateSendLogEntry() {
        UUID sendLogRowId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        insertOriginalSendLogRow(sendLogRowId, parentId, "contact_unit", "H05.02", "Sở NN&MT");

        ObjectNode payload = basePayload();
        payload.put("sendLogId", sendLogRowId.toString());

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();
        assertThat(handler.execute(context(), payload).isSuccess()).isTrue(); // redelivery

        Long count = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM vbdi_send_log_documents WHERE parent_id = ? AND send_type = 'phan_hoi_trang_thai'",
            Long.class, parentId);
        assertThat(count).isEqualTo(1L);
    }

    /**
     * Không tìm thấy dòng gốc (vd dữ liệu cũ/đã xoá) — bỏ qua AN TOÀN (log cảnh báo), KHÔNG lỗi
     * toàn bộ unit (dedup vào {@code worker_vbdi_reply_log} vẫn phải thành công).
     */
    @Test
    void whenOriginalSendLogRowNotFound_shouldSkipGracefullyWithoutFailingUnit() {
        ObjectNode payload = basePayload();
        payload.put("sendLogId", UUID.randomUUID().toString()); // khong ton tai dong nao

        UnitResult result = handler.execute(context(), payload);

        assertThat(result.isSuccess()).isTrue();
        Long count = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM vbdi_send_log_documents", Long.class);
        assertThat(count).isEqualTo(0L);
    }

    /**
     * Bugfix 07/09/2026 (2) — production log: {@code DuplicateKeyException} NGAY TRÊN
     * {@code CREATE TABLE IF NOT EXISTS worker_vbdi_reply_log} khi 2+ worker-unit thread xử lý
     * {@code vbdi.reply} ĐỒNG THỜI cho CÙNG tenant, lần bảng CHƯA từng tồn tại (race đã biết của
     * Postgres — {@code CREATE TABLE IF NOT EXISTS} không atomic giữa các session khác nhau). Test
     * này dùng THREAD THẬT + {@link CountDownLatch} để ép 2 lệnh gọi {@code execute()} chạm bảng
     * chưa tồn tại CÙNG LÚC — trước bugfix, 1 trong 2 unit sẽ FAIL (exception lan ra ngoài); sau
     * bugfix cả 2 phải THÀNH CÔNG, không mất statusCode nào.
     */
    @Test
    void concurrentFirstRepliesForSameTenant_bothSucceedDespiteCreateTableRace() throws Exception {
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<java.util.concurrent.Future<UnitResult>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int idx = i;
                futures.add(pool.submit(() -> {
                    ObjectNode payload = basePayload();
                    payload.put("sendLogId", "sl-concurrent-" + idx);
                    ready.countDown();
                    go.await();
                    // unitId RIENG cho moi thread (dung nguyen tac dedup moi - xem javadoc lop):
                    // 8 su kien THAT SU khac nhau, khong phai 8 lan redeliver cung 1 message.
                    return handler.execute(context("u-concurrent-" + idx), payload);
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();

            for (var future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS).isSuccess()).isTrue();
            }
        } finally {
            pool.shutdown();
        }

        Long count = new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM worker_vbdi_reply_log WHERE status_code = ?",
            Long.class, VbdiTaskConstants.STATUS_01_DA_DEN);
        assertThat(count).isEqualTo((long) threads);
    }
}
