-- Yêu cầu người dùng 22/10/2026 (đính kèm dùng chung, chỉ xóa object MinIO khi hết đơn vị tham chiếu):
-- job dọn dẹp SharedFileGcJob (td-woker) kiểm tra lại file_metadata của MỌI tenant theo file_path trước
-- khi xóa object - cần index để không quét toàn bảng ở từng tenant.
CREATE INDEX IF NOT EXISTS idx_file_metadata_file_path ON file_metadata (file_path);
