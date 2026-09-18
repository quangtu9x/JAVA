-- Yêu cầu người dùng 18/09/2026 ("thêm vết audit của văn bản đến, tham khảo văn bản đi đã làm"):
-- mirror ĐÚNG vbdi_user_action_logs (V1.0.36) cho cụm vbden — "Lịch sử thao tác văn bản" (khác hẳn
-- "Vết hiệu chỉnh"/vbden_edit_history_documents V1.0.60, vốn chỉ ghi FIELD nào bị đổi; bảng này ghi
-- HÀNH ĐỘNG người dùng đã bấm: tiếp nhận/chuyển xử lý/thu hồi/trả lại/kết thúc xử lý/...).
--
-- Cùng khuôn "audit trail append-only, mỗi dòng = 1 sự kiện" đã dùng cho vbdi_user_action_logs —
-- action_type là GIÁ TRỊ của 1 cột (không phải TÊN cột như mô hình pivot Domino gốc), Java tự nhóm
-- lại theo (parent_id, username) khi hiển thị (xem ListVbdenUserActionLogUseCase).
CREATE TABLE vbden_user_action_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    parent_id UUID NOT NULL,
    username VARCHAR(100),
    fullname VARCHAR(300),
    unit VARCHAR(300),
    identifier VARCHAR(100),
    action_type VARCHAR(100),
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

CREATE INDEX IX_vbden_user_action_logs_parent_id ON vbden_user_action_logs(parent_id);
CREATE INDEX IX_vbden_user_action_logs_username ON vbden_user_action_logs(username);
CREATE INDEX IX_vbden_user_action_logs_created_on ON vbden_user_action_logs(created_on);
CREATE INDEX IX_vbden_user_action_logs_deleted_on ON vbden_user_action_logs(deleted_on);

COMMENT ON TABLE vbden_user_action_logs IS 'Bảng vật lý ĐỘC LẬP cho "Lịch sử thao tác văn bản" của văn bản đến (mirror vbdi_user_action_logs) - mỗi dòng = 1 sự kiện thao tác, trỏ parent_id về documents.id (document_type=''vbden'')';
COMMENT ON COLUMN vbden_user_action_logs.parent_id IS 'id văn bản đến cha (documents.id, document_type=''vbden'')';
COMMENT ON COLUMN vbden_user_action_logs.username IS 'Username người thực hiện - SERVER-AUTHORITATIVE qua CurrentUserProvider, client không đặt được';
COMMENT ON COLUMN vbden_user_action_logs.action_type IS 'Loại thao tác tự do (tiepnhan/vaosovanbanden/chuyenxuly/thuhoivanban/laylaivanban/xulylai/tralaivanban/ketthucxuly/vanthuketthucxuly/capnhatphoihop/capnhatykien/choykienxinykien/...) - không giới hạn danh sách cố định';
