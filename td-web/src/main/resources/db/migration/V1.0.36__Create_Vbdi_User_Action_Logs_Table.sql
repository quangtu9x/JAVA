-- Yêu cầu người dùng 30/08/2026 ("port qlvb/vbdi?databasetype=vbdi&action=getloguseraction sang
-- java"): bảng vật lý ĐỘC LẬP cho "Lịch sử thao tác văn bản" (LogUserAction.jsx) — port cặp action
-- Domino loguseraction (ghi)/getloguseraction (đọc), cùng dùng 1 class Domino
-- vn.com.tandan.xsp.services.vbdi.LogUserAction.java, lưu ở database phụ "vbdi_res" (KHÁC database
-- chính "vbdi"), Form="user_action".
--
-- Khác biệt CHỦ ĐÍCH với mô hình lưu trữ Domino gốc: Domino dùng 1 document/(document_id,username)
-- rồi PIVOT mỗi action_type thành 1 field riêng chứa danh sách timestamp nối ";" (áp dụng
-- appendToTextList khi lặp lại) — mô hình "wide table" nhân tạo, khó mở rộng thêm loại action mới
-- (phải thêm cột). Ở đây theo ĐÚNG khuôn "audit trail append-only, mỗi dòng = 1 sự kiện" đã dùng cho
-- vbdi_send_log_documents (V1.0.33)/vbdi_edit_history_documents (V1.0.35) — mỗi lần gọi ghi log =
-- 1 dòng MỚI (action_type là GIÁ TRỊ của 1 cột, không phải TÊN cột) — Java tự nhóm lại theo
-- (parent_id, username) khi cần hiển thị dạng "1 dòng/1 người" (xem ListVbdiUserActionLogUseCase),
-- không cần thêm cột mỗi khi có action_type nghiệp vụ mới.
CREATE TABLE vbdi_user_action_logs (
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

CREATE INDEX IX_vbdi_user_action_logs_parent_id ON vbdi_user_action_logs(parent_id);
CREATE INDEX IX_vbdi_user_action_logs_username ON vbdi_user_action_logs(username);
CREATE INDEX IX_vbdi_user_action_logs_created_on ON vbdi_user_action_logs(created_on);
CREATE INDEX IX_vbdi_user_action_logs_deleted_on ON vbdi_user_action_logs(deleted_on);

COMMENT ON TABLE vbdi_user_action_logs IS 'Bảng vật lý ĐỘC LẬP cho "Lịch sử thao tác văn bản" (LogUserAction.jsx) - port cặp action Domino loguseraction/getloguseraction (database phụ vbdi_res, Form=user_action) - mỗi dòng = 1 sự kiện thao tác (khác Domino pivot theo field, xem ghi chú trên), trỏ parent_id về vbdi_documents.id';
COMMENT ON COLUMN vbdi_user_action_logs.parent_id IS 'id văn bản đi cha (vbdi_documents.id)';
COMMENT ON COLUMN vbdi_user_action_logs.username IS 'Username người thực hiện - SERVER-AUTHORITATIVE qua CurrentUserProvider, client không đặt được';
COMMENT ON COLUMN vbdi_user_action_logs.action_type IS 'Loại thao tác tự do (guivanban/guilaivanban/thuhoivanban/laylaivanban/sendOnSave/guibosung/ganvanbanden/new_ganvanbanden/vaosonhieuvanbandi/...) - đúng field động Domino gốc, không giới hạn danh sách cố định';
