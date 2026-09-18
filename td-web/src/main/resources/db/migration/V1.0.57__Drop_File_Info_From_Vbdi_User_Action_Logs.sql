-- 09/09/2026 (2, yêu cầu người dùng "log xem tệp đính kèm cần thiết cho các module khác nữa... tối
-- ưu lưu trữ"): dọn lại 2 cột file_id/file_name mà V1.0.55 đã thêm vào vbdi_user_action_logs —
-- thiết kế đó đã bị THAY THẾ bởi bảng generic file_access_logs (V1.0.56, model UPSERT chống phình
-- bảng, dùng chung mọi module thay vì chỉ vbdi). Entity VbdiUserActionLog KHÔNG còn tham chiếu 2
-- cột này (xem javadoc lớp đó) — DROP để tránh schema "mồ côi" (cột tồn tại nhưng không ứng dụng
-- nào đọc/ghi). KHÔNG sửa lại nội dung V1.0.55 (đã áp dụng, checksum bị khoá) — xử lý bằng migration
-- kế tiếp là cách ĐÚNG duy nhất khi cần đổi ý sau khi 1 migration đã chạy ở môi trường thật.
ALTER TABLE vbdi_user_action_logs
    DROP COLUMN IF EXISTS file_id,
    DROP COLUMN IF EXISTS file_name;

DROP INDEX IF EXISTS IX_vbdi_user_action_logs_file_id;
