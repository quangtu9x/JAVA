package com.td.worker.tasks.simpki;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Dedup/idempotency + audit trạng thái ký cho task {@code simpki.sign}. Bảng
 * {@code worker_simpki_sign_log} nằm trong DATABASE RIÊNG CỦA WORKER (KHÔNG phải database tenant —
 * Worker không migrate tenant, xem docs/06-multi-tenant.md), ĐÚNG như
 * {@code worker_guinhanquocgia_send_log}/{@code worker_units}. KHÔNG lưu nội dung chữ ký (dữ liệu
 * nghiệp vụ thuộc về td-web, gắn vào file_metadata phía đó qua envelope phản hồi
 * {@code simpki.sign_reply} — xem {@link SimPkiSignTaskHandler}) — bảng này CHỈ audit trạng thái/
 * trace id (transId/verificationCode/statusCode), đúng nguyên tắc "Worker chỉ tự quản dedup/
 * checkpoint, KHÔNG sở hữu dữ liệu nghiệp vụ" (đã áp dụng cho {@code guinhanquocgia}, xem
 * docs/16-checklist-tich-hop-gui-nhan-quoc-gia.md mục 0.5).
 *
 * <p>Có 2 cơ chế tạo bảng song song, CÓ CHỦ ĐÍCH (không mâu thuẫn) — cùng pattern
 * {@code GuiNhanQuocGiaSendLogStore}:</p>
 * <ul>
 *   <li>Flyway migration MỚI {@code V3__create_worker_simpki_sign_log.sql} — áp dụng khi
 *       {@code worker.db.schema-management=flyway} (production).</li>
 *   <li>{@link #ensureTable(JdbcTemplate)} tự chạy {@code CREATE TABLE IF NOT EXISTS} — chạy được cả
 *       khi {@code worker.db.schema-management=script} (test/H2, không qua Flyway).</li>
 * </ul>
 *
 * <p><b>Dedup PORTABLE (H2 lẫn PostgreSQL)</b>: dùng "INSERT thường + bắt
 * {@link DuplicateKeyException} trên PRIMARY KEY {@code unit_id}" (H2 KHÔNG parse được
 * {@code ON CONFLICT}, đã xác nhận ở chính project — xem javadoc {@code GuiNhanQuocGiaSendLogStore}).</p>
 */
@Slf4j
@Component
public class SimPkiSignLogStore {

    private final DataSource workerDataSource;

    public SimPkiSignLogStore(DataSource workerDataSource) {
        this.workerDataSource = workerDataSource;
    }

    /**
     * @return {@code true} nếu THỰC SỰ tạo mới dòng (chưa xử lý trước đó — nên tiếp tục gọi gateway);
     *         {@code false} nếu đã tồn tại (idempotent — redelivery, bỏ qua an toàn).
     */
    public boolean tryClaim(String unitId, String tenantId, SimPkiSignPayload payload) {
        JdbcTemplate jdbc = new JdbcTemplate(workerDataSource);
        ensureTable(jdbc);
        try {
            jdbc.update("""
                    INSERT INTO %s
                     (unit_id, tenant_id, document_id, file_id, msisdn, status, created_at)
                    VALUES (?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                    """.formatted(SimPkiTaskConstants.SIGN_LOG_TABLE),
                unitId, tenantId, payload.getDocumentId(), payload.getFileId(), payload.getMsisdn());
            return true;
        } catch (DuplicateKeyException e) {
            log.info("simpki.sign: unit {} (documentId={}) đã ghi trước đó (idempotent — redelivery)",
                unitId, payload.getDocumentId());
            return false;
        }
    }

    public void markSigned(String unitId, String transId, String verificationCode, Long statusCode) {
        new JdbcTemplate(workerDataSource).update(
            "UPDATE %s SET status='SIGNED', trans_id=?, verification_code=?, status_code=?, signed_at=CURRENT_TIMESTAMP WHERE unit_id=?"
                .formatted(SimPkiTaskConstants.SIGN_LOG_TABLE),
            transId, verificationCode, statusCode, unitId);
    }

    public void markFailed(String unitId, String errorDetail) {
        new JdbcTemplate(workerDataSource).update(
            "UPDATE %s SET status='FAILED', error_detail=? WHERE unit_id=?"
                .formatted(SimPkiTaskConstants.SIGN_LOG_TABLE),
            errorDetail, unitId);
    }

    private void ensureTable(JdbcTemplate jdbc) {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    unit_id           VARCHAR(100) NOT NULL PRIMARY KEY,
                    tenant_id         VARCHAR(100) NOT NULL,
                    document_id       VARCHAR(100),
                    file_id           VARCHAR(100),
                    msisdn            VARCHAR(30),
                    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    trans_id          VARCHAR(100),
                    verification_code VARCHAR(20),
                    status_code       BIGINT,
                    error_detail      TEXT,
                    signed_at         TIMESTAMP WITH TIME ZONE,
                    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(SimPkiTaskConstants.SIGN_LOG_TABLE));
        } catch (DuplicateKeyException ex) {
            // Race CREATE TABLE giữa 2 unit đồng thời lần đầu — cùng nguyên nhân đã ghi nhận ở
            // VbdiReplyTaskHandler#ensureReplyLogTable/GuiNhanQuocGiaSendLogStore#ensureTable; bỏ
            // qua an toàn, bảng đã được session thắng tạo xong.
            log.debug("{}: thua race CREATE TABLE với 1 unit khác đang chạy đồng thời — bỏ qua an toàn: {}",
                SimPkiTaskConstants.SIGN_LOG_TABLE, ex.getMessage());
        }
    }
}
