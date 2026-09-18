-- BL-116 (04/09/2026, yêu cầu người dùng: "Lãnh đạo phân công tự điền mặc định khi Nhập mới"):
-- Domino thật (DanhSachSo.java) mỗi "Sổ văn bản" mang theo 1 lãnh đạo mặc định (assigner/user_assigner/
-- assigner_position) — port trước đây (BL-116) chỉ giữ lại "assigner" (tên tự do), cố ý bỏ 2 cột còn
-- lại vì chưa có yêu cầu cụ thể (xem docs/migration-backlog/04-migration-status.md). Bổ sung lại 2
-- cột còn thiếu — user_assigner_id (UUID, tham chiếu MỀM tới app_users.id, dùng làm khoá cho ô chọn
-- "Lãnh đạo" ở màn quản trị "Sổ văn bản" — cùng lý do Organizations.receiver_id đã làm ở V1.0.11:
-- ô chọn (TDSelect) khớp theo id, không phải theo tên) + user_assigner_username (String, dùng TRỰC
-- TIẾP khi tự điền "Lãnh đạo phân công" lúc Nhập mới, khớp đúng field user_assigner Domino gốc) +
-- assigner_position (snapshot chức vụ hiển thị, khớp assigner_position Domino gốc).

ALTER TABLE document_registers
    ADD COLUMN IF NOT EXISTS user_assigner_id UUID;

ALTER TABLE document_registers
    ADD COLUMN IF NOT EXISTS user_assigner_username VARCHAR(100);

ALTER TABLE document_registers
    ADD COLUMN IF NOT EXISTS assigner_position VARCHAR(300);
