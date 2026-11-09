-- 30/10/2026 ("thêm chẩn đoán vào lúc ghi nhật ký lỗi, chi tiết để dễ debug"): log lỗi "No static
-- resource qlvb/add|search" chỉ có path + username, request_body rỗng -> không biết màn hình/chức năng
-- nào gọi. request_context = JSON ngữ cảnh: query string, content type, trang FE (X-FE-Page), nơi gọi
-- (X-FE-Caller), Referer, User-Agent, tham số form/multipart (đã che field nhạy cảm, cắt độ dài).
ALTER TABLE error_log ADD COLUMN IF NOT EXISTS request_context TEXT;
