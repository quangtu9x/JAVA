-- 09/09/2026 (3, yêu cầu người dùng "sao không dùng luôn dynamic_fields như các bảng khác mà chỉ
-- chỉnh mình actions = jsonb"): V1.0.58 đã lỡ ÁP DỤNG cột "actions" (JSONB) riêng cho
-- file_access_logs trước khi kịp đổi sang dùng lại đúng kiến trúc dynamic_fields chung — KHÔNG được
-- sửa lại nội dung V1.0.58 (checksum đã khoá, xem lỗi build "Migration checksum mismatch for
-- migration version 1.0.58" - bài học Y HỆT V1.0.55/V1.0.56 trước đó, xem 2 migration đó). Migration
-- NÀY xử lý việc đổi ý bằng ALTER, đúng cách duy nhất khi 1 migration đã chạy ở môi trường thật.
--
-- Đích đến: bỏ cột "actions" tự chế, DÙNG LẠI ĐÚNG cột "dynamic_fields" chung của MỌI bảng document
-- khác (BL-101 Dynamic Data Engine — cùng tên/cùng kiểu với vbdi_documents.dynamic_fields,
-- vbden_res_documents.dynamic_fields, ...) — xem quy ước chung "Dynamic fields / JSONB" tại
-- ARCHITECTURE.md § Best Practices, và FileAccessLog#dynamicFieldsJson. Không đổi UNIQUE KEY lần
-- này (đã đúng từ V1.0.58: module, document_id, file_id, username) nên KHÔNG cần gộp dòng trùng như
-- V1.0.58 — chỉ cần chuyển 1-1 giá trị "actions" (mảng) vào khoá "actions" bên trong object
-- dynamic_fields mới.
ALTER TABLE file_access_logs ADD COLUMN dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE file_access_logs SET dynamic_fields = jsonb_build_object('actions', actions);

ALTER TABLE file_access_logs DROP COLUMN actions;

COMMENT ON COLUMN file_access_logs.dynamic_fields IS 'Field nghiệp vụ biến thiên (BL-101 Dynamic Data Engine, CÙNG kiến trúc dynamic_fields của vbdi_documents/vbden_res_documents/...) - hiện chỉ có khoá "actions": mảng các loại thao tác đã từng ghi nhận trên tổ hợp (module,document_id,file_id,username), vd {"actions":["view","download"]} - APPEND thao tác mới nếu chưa có (merge bằng jsonb_set + operator "@>"/"||" ngay trong UPSERT, xem PostgresFileAccessLogRepository#recordAccess), KHÔNG tách dòng riêng theo action';
COMMENT ON TABLE file_access_logs IS 'Log xem/tải tệp đính kèm - DÙNG CHUNG mọi module (vbdi/vbden/duthao/hosocv/...). Model UPSERT theo (module,document_id,file_id,username): 1 dòng/tổ hợp (tệp, người dùng) BẤT KỂ loại thao tác, first_accessed_on bất biến, last_accessed_on/access_count/dynamic_fields.actions cập nhật mỗi lần xem/tải lại - tránh phình bảng theo số lượt xem lẫn theo số loại thao tác.';
