-- =====================================================================
-- Bổ sung 09/09/2026 — module MỚI "guinhanquocgia" (com.td.worker.tasks.guinhanquocgia), TÁCH BIỆT
-- HOÀN TOÀN khỏi vbdi/vbden hiện có. File MIGRATION MỚI (không sửa V1__create_worker_tables.sql).
--
-- Bảng dedup/audit cho task guinhanquocgia.send — nằm trong database RIÊNG của Worker (KHÔNG phải
-- database tenant — Worker không migrate tenant, xem docs/06-multi-tenant.md), ĐÚNG như
-- worker_units/worker_messages. Khác worker_vbdi_reply_log (tạo runtime trong DB TENANT của
-- VbdiReplyTaskHandler) vì nghiệp vụ gửi nhận quốc gia không gắn 1-1 với 1 tenant xử lý theo cùng
-- cách — tenant_id ở đây chỉ là cột lọc/audit, không phải chọn database.
--
-- THAM KHẢO cột đề xuất ở docs/td-guinhanquocgia/docs/07-mapping-du-lieu-postgresql.md
-- (bảng vpcp_edoc_outbox) — RÚT GỌN cho scaffold: chỉ dedup + trạng thái gửi, chưa lưu toàn bộ
-- nghiệp vụ (bảng nghiệp vụ đầy đủ nên do td-web sở hữu khi tích hợp thật, cùng nguyên tắc
-- vbdi_send_log_documents).
--
-- LƯU Ý: GuiNhanQuocGiaSendLogStore CŨNG tự CREATE TABLE IF NOT EXISTS lúc runtime (để chạy được cả
-- khi worker.db.schema-management=script/H2, không qua Flyway) — file migration này là nguồn THẬT
-- cho môi trường production (schema-management=flyway), 2 định nghĩa PHẢI khớp nhau.
-- =====================================================================

CREATE TABLE worker_guinhanquocgia_send_log (
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
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_worker_guinhanquocgia_send_log_tenant_doc
    ON worker_guinhanquocgia_send_log(tenant_id, document_id);

COMMENT ON TABLE worker_guinhanquocgia_send_log IS
    'Dedup/audit gửi văn bản qua Trục liên thông văn bản quốc gia (VPCP/VXP) - module guinhanquocgia, xem docs/16-checklist-tich-hop-gui-nhan-quoc-gia.md';
