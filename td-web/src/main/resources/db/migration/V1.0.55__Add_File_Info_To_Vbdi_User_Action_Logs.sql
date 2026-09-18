-- 09/09/2026 (yêu cầu người dùng "ghi cả log nếu người dùng có thao tác xem hoặc tải tệp đính kèm"):
-- mở rộng NGAY bảng vbdi_user_action_logs (V1.0.36) thay vì tạo bảng mới — tận dụng đúng model
-- "audit trail append-only, mỗi dòng = 1 sự kiện" đã có sẵn (repository/entity/index đều dùng
-- được nguyên vẹn), chỉ thêm 2 cột NULLABLE để biết sự kiện đó gắn với TỆP ĐÍNH KÈM nào — các dòng
-- log CŨ (thao tác không liên quan file, vd guivanban/thuhoivanban/...) giữ nguyên 2 cột này = NULL.
--
-- action_type tái dùng NGUYÊN VẸN (không thêm cột riêng) — chỉ thêm 2 giá trị mới "xemtep"/"taitep"
-- (xem AddVbdiFileAccessLogUseCase) — đúng bản chất cột này vốn đã "tự do, không giới hạn danh sách
-- cố định" (xem comment cột action_type, V1.0.36).
ALTER TABLE vbdi_user_action_logs
    ADD COLUMN file_id UUID NULL,
    ADD COLUMN file_name VARCHAR(500) NULL;

CREATE INDEX IX_vbdi_user_action_logs_file_id ON vbdi_user_action_logs(file_id);

COMMENT ON COLUMN vbdi_user_action_logs.file_id IS 'Id tệp đính kèm (file_metadata.id) - CHỈ có giá trị khi action_type là sự kiện xem/tải tệp ("xemtep"/"taitep"), NULL cho mọi loại thao tác khác (guivanban/thuhoivanban/...)';
COMMENT ON COLUMN vbdi_user_action_logs.file_name IS 'Tên gốc tệp TẠI THỜI ĐIỂM ghi log (snapshot, tra server-side qua GetFileUseCase - KHÔNG tin client) - phòng trường hợp file bị đổi tên/xoá sau đó, lịch sử vẫn hiển thị đúng tên lúc xem/tải';
