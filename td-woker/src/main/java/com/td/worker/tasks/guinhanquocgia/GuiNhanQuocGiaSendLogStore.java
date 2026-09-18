package com.td.worker.tasks.guinhanquocgia;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Dedup/idempotency + audit trạng thái gửi cho task {@code guinhanquocgia.send} — bổ sung
 * 09/09/2026. Bảng {@code worker_guinhanquocgia_send_log} nằm trong DATABASE RIÊNG CỦA WORKER
 * (KHÔNG phải database tenant — Worker không migrate tenant, xem docs/06-multi-tenant.md), ĐÚNG như
 * {@code worker_units}/{@code worker_messages} — khác {@code worker_vbdi_reply_log} (tạo runtime
 * trong DB TENANT) vì nghiệp vụ gửi nhận quốc gia không gắn 1-1 với 1 tenant xử lý theo cùng cách;
 * {@code tenant_id} ở đây chỉ là cột lọc/audit.
 *
 * <p>Có 2 cơ chế tạo bảng song song, CÓ CHỦ ĐÍCH (không mâu thuẫn):</p>
 * <ul>
 *   <li>Flyway migration MỚI {@code V2__create_worker_guinhanquocgia_send_log.sql} (file mới, không
 *       sửa V1) — áp dụng khi {@code worker.db.schema-management=flyway} (production).</li>
 *   <li>{@link #ensureTable(JdbcTemplate)} tự chạy {@code CREATE TABLE IF NOT EXISTS} — để chạy được
 *       cả khi {@code worker.db.schema-management=script} (test/H2, KHÔNG qua Flyway) mà KHÔNG cần
 *       sửa {@code db/worker-schema-h2.sql}/{@code worker-schema-postgres.sql} hiện có (đúng ràng
 *       buộc "chỉ thêm mới, không sửa file đang có").</li>
 * </ul>
 *
 * <p><b>Dedup PORTABLE (H2 lẫn PostgreSQL)</b>: CỐ Ý dùng "INSERT thường + bắt
 * {@link DuplicateKeyException} trên PRIMARY KEY {@code unit_id}" thay vì
 * {@code INSERT ... ON CONFLICT DO NOTHING} (như {@code VbdiReplyTaskHandler}) — vì H2 KHÔNG parse
 * được cú pháp {@code ON CONFLICT} (đã xác nhận trong chính project ở
 * {@code VbdiReplyTaskHandlerIntegrationTest}, phải dùng Testcontainers/Postgres thật). Cách này cho
 * phép viết unit test THẬT chạy được bằng H2 in-memory, không cần Docker.</p>
 */
@Slf4j
@Component
public class GuiNhanQuocGiaSendLogStore {

    private final DataSource workerDataSource;

    public GuiNhanQuocGiaSendLogStore(DataSource workerDataSource) {
        this.workerDataSource = workerDataSource;
    }

    /**
     * @return {@code true} nếu THỰC SỰ tạo mới dòng (chưa xử lý trước đó — nên tiếp tục gọi gateway);
     *         {@code false} nếu đã tồn tại (idempotent — redelivery, bỏ qua an toàn).
     */
    public boolean tryClaim(String unitId, String tenantId, GuiNhanQuocGiaSendPayload payload) {
        JdbcTemplate jdbc = new JdbcTemplate(workerDataSource);
        ensureTable(jdbc);
        try {
            jdbc.update("""
                    INSERT INTO %s
                     (unit_id, tenant_id, document_id, recipient_agency_code, sender_agency_code, doc_type, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                    """.formatted(GuiNhanQuocGiaTaskConstants.SEND_LOG_TABLE),
                unitId, tenantId, payload.getDocumentId(), payload.getRecipientAgencyCode(),
                payload.getSenderAgencyCode(), payload.getDocType());
            return true;
        } catch (DuplicateKeyException e) {
            log.info("guinhanquocgia.send: unit {} (documentId={}) đã ghi trước đó (idempotent — redelivery)",
                unitId, payload.getDocumentId());
            return false;
        }
    }

    public void markSent(String unitId, String centerDocId) {
        new JdbcTemplate(workerDataSource).update(
            "UPDATE %s SET status='SENT', center_doc_id=?, sent_at=CURRENT_TIMESTAMP WHERE unit_id=?"
                .formatted(GuiNhanQuocGiaTaskConstants.SEND_LOG_TABLE),
            centerDocId, unitId);
    }

    public void markFailed(String unitId, String errorDetail) {
        new JdbcTemplate(workerDataSource).update(
            "UPDATE %s SET status='FAILED', error_detail=? WHERE unit_id=?"
                .formatted(GuiNhanQuocGiaTaskConstants.SEND_LOG_TABLE),
            errorDetail, unitId);
    }

    private void ensureTable(JdbcTemplate jdbc) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    unit_id               VARCHAR(100) NOT NULL PRIMARY KEY,
                    tenant_id             VARCHAR(100) NOT NULL,
                    document_id           VARCHAR(100) NOT NULL,
                    recipient_agency_code VARCHAR(100),
                    sender_agency_code    VARCHAR(100),
                    doc_type              VARCHAR(20),
                    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    center_doc_id         VARCHAR(100),
                    error_detail          TEXT,
                    sent_at               TIMESTAMP WITH TIME ZONE,
                    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(GuiNhanQuocGiaTaskConstants.SEND_LOG_TABLE));
        } catch (DuplicateKeyException ex) {
            // Race CREATE TABLE giữa 2 unit đồng thời lần đầu (chưa từng tồn tại) — cùng nguyên nhân
            // đã ghi nhận ở VbdiReplyTaskHandler#ensureReplyLogTable; bỏ qua an toàn, bảng đã được
            // session thắng tạo xong.
            log.debug("{}: thua race CREATE TABLE với 1 unit khác đang chạy đồng thời — bỏ qua an toàn: {}",
                GuiNhanQuocGiaTaskConstants.SEND_LOG_TABLE, ex.getMessage());
        }
    }
}
