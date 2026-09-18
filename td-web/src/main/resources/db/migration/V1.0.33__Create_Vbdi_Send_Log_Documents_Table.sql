-- Yêu cầu người dùng 28/08/2026 ("tách lưu Nhật ký gửi nhận ra bảng vật lý khác như làm với
-- draft_process"): bảng vật lý ĐỘC LẬP cho "Nhật ký gửi nhận" của văn bản đi — port dữ liệu Domino
-- thật *_obj_log_rtext (coordinator_obj_log_rtext/internal_unit_obj_log_rtext/
-- contact_unit_obj_log_rtext/external_unit_obj_log_rtext, xem docs/views_domino/data_vbdi_mau.txt +
-- docs/analysis/07-phan-tich-port-senddocument-vbdi-qlvb.md) — trước đây (nếu ghi) sẽ rơi vào JSON
-- lồng nhau trong vbdi_documents.dynamic_fields, giờ tách hẳn thành 1 dòng/1 sự kiện gửi-nhận, đúng
-- khuôn "bảng con audit-trail" mới nhất đã dùng cho duthao_edit_history_documents (V1.0.32 —
-- cột typed parent_id thay vì containment JSONB, KHÔNG dùng lại khuôn generic Dynamic Data Engine
-- của draft_process_documents/V1.0.30 vì dữ liệu ở đây không phải "document" biến thiên tự do mà là
-- 1 vết có cấu trúc cố định rõ ràng — xem javadoc VbdiSendLogDocument).
--
-- Mỗi dòng = 1 sự kiện gửi/nhận cho 1 nơi nhận (append-only, không sửa/xoá) — tương đương 1 phần tử
-- trong mảng "detail" của *_obj_log_rtext Domino gốc.
CREATE TABLE vbdi_send_log_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
);

CREATE INDEX IX_vbdi_send_log_documents_parent_id ON vbdi_send_log_documents(parent_id);
CREATE INDEX IX_vbdi_send_log_documents_recipient_group ON vbdi_send_log_documents(recipient_group);
CREATE INDEX IX_vbdi_send_log_documents_recipient_identifier ON vbdi_send_log_documents(recipient_identifier);
CREATE INDEX IX_vbdi_send_log_documents_created_on ON vbdi_send_log_documents(created_on);
CREATE INDEX IX_vbdi_send_log_documents_deleted_on ON vbdi_send_log_documents(deleted_on);

COMMENT ON TABLE vbdi_send_log_documents IS 'Bảng vật lý ĐỘC LẬP cho Nhật ký gửi nhận văn bản đi (yêu cầu người dùng 28/08/2026, tách khỏi vbdi_documents.dynamic_fields) - mỗi dòng = 1 sự kiện gửi/nhận cho 1 nơi nhận (4 nhóm: coordinator/internal_unit/contact_unit/external_unit), trỏ parent_id về vbdi_documents.id';
COMMENT ON COLUMN vbdi_send_log_documents.parent_id IS 'id văn bản đi cha (vbdi_documents.id)';
COMMENT ON COLUMN vbdi_send_log_documents.send_type IS 'senddocument/resenddocument/guibosung/guilaitoanbo - tương đương action Domino qlvb/vbdi?action=';
COMMENT ON COLUMN vbdi_send_log_documents.recipient_group IS 'coordinator (cá nhân/phê duyệt) / internal_unit (đơn vị nội bộ) / contact_unit (cơ quan liên hệ) / external_unit (cơ quan ngoài/liên thông quốc gia) - đúng 4 nhóm Domino thật';
COMMENT ON COLUMN vbdi_send_log_documents.recipient_identifier IS 'mã định danh đơn vị nhận, hoặc username nếu recipient_group=coordinator (cá nhân)';
COMMENT ON COLUMN vbdi_send_log_documents.status IS 'trạng thái sự kiện dạng nhãn tự do (Gửi văn bản/Đã đến/Đã tiếp nhận/Phân công xử lý/...) - đúng field "status" trong detail[] Domino gốc';
