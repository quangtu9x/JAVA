-- =====================================================================
-- Module MỚI "simpki" (com.td.worker.tasks.simpki) — ký số từ xa qua SIM-PKI/MSSP (ETSI TS 102 204,
-- docs/TDSimSign/sdk-mobilepki). TÁCH BIỆT HOÀN TOÀN khỏi vbdi/vbden/guinhanquocgia hiện có. File
-- MIGRATION MỚI (không sửa V1/V2 hiện có).
--
-- Bảng dedup/audit cho task simpki.sign — nằm trong database RIÊNG của Worker (KHÔNG phải database
-- tenant — Worker không migrate tenant, xem docs/06-multi-tenant.md), ĐÚNG như
-- worker_guinhanquocgia_send_log/worker_units. KHÔNG lưu nội dung chữ ký (dữ liệu nghiệp vụ thuộc
-- td-web, gắn vào file_metadata phía đó qua envelope phản hồi simpki.sign_reply) — chỉ dedup +
-- trạng thái/trace id (transId/verificationCode/statusCode của MSSP).
--
-- LƯU Ý: SimPkiSignLogStore CŨNG tự CREATE TABLE IF NOT EXISTS lúc runtime (để chạy được cả khi
-- worker.db.schema-management=script/H2, không qua Flyway) — file migration này là nguồn THẬT cho
-- môi trường production (schema-management=flyway), 2 định nghĩa PHẢI khớp nhau.
-- =====================================================================

CREATE TABLE worker_simpki_sign_log (
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
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_worker_simpki_sign_log_tenant_doc
    ON worker_simpki_sign_log(tenant_id, document_id);

COMMENT ON TABLE worker_simpki_sign_log IS
    'Dedup/audit ký số từ xa qua SIM-PKI/MSSP (ETSI TS 102 204) - module simpki, xem docs/17-checklist-tich-hop-kyso-simpki.md';
