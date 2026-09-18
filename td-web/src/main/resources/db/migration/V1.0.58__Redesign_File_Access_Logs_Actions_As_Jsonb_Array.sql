-- 09/09/2026 (2, yêu cầu người dùng "1 người dùng xem/tải cùng 1 tệp thì lưu chung 1 dòng, action
-- lưu dạng mảng, số lần tính chung, FE nhóm theo người dùng"): V1.0.56 (unique key có "action", 1
-- dòng/tổ hợp tệp+người dùng+LOẠI THAO TÁC) đã lỡ ÁP DỤNG vào 1 số DB khi test trước khi kịp đổi
-- thiết kế — KHÔNG được sửa lại nội dung V1.0.56 (checksum đã khoá, xem lỗi build "Migration
-- checksum mismatch for migration version 1.0.56" - bài học Y HỆT V1.0.55 trước đó). Migration NÀY
-- xử lý việc đổi ý bằng ALTER, đúng cách duy nhất khi 1 migration đã chạy ở môi trường thật.
--
-- Đích đến: 1 dòng/tổ hợp (module, document_id, file_id, username) BẤT KỂ loại thao tác — cột
-- "actions" JSONB chứa mảng các loại thao tác đã xảy ra (vd ["view","download"]), xem quy ước chung
-- "Dynamic fields / JSONB" tại ARCHITECTURE.md § Best Practices, và
-- PostgresFileAccessLogRepository#recordAccess (merge bằng operator jsonb "@>"/"||" ngay trong UPSERT).
--
-- Vì đổi UNIQUE KEY (bỏ "action") nên PHẢI gộp lại dữ liệu cũ trước: 1 user có thể đã có 2 dòng
-- (action=view và action=download) trên CÙNG 1 tệp ở thiết kế V1.0.56 — nếu không gộp, thêm lại
-- unique constraint mới sẽ báo lỗi trùng khoá ngay. Dùng bảng tạm để gộp (đơn giản, an toàn hơn
-- window function nhiều câu lệnh) — bảng này CHỈ là log/audit (không có FK trỏ tới), đổi id khi gộp
-- KHÔNG ảnh hưởng nghiệp vụ nào khác.
CREATE TEMP TABLE file_access_logs_merged AS
SELECT
    module,
    document_id,
    file_id,
    username,
    (array_agg(file_name ORDER BY last_accessed_on DESC))[1] AS file_name,
    (array_agg(fullname ORDER BY last_accessed_on DESC))[1] AS fullname,
    (array_agg(unit ORDER BY last_accessed_on DESC))[1] AS unit,
    jsonb_agg(DISTINCT action) AS actions,
    MIN(first_accessed_on) AS first_accessed_on,
    MAX(last_accessed_on) AS last_accessed_on,
    SUM(access_count) AS access_count
FROM file_access_logs
GROUP BY module, document_id, file_id, username;

TRUNCATE TABLE file_access_logs;

ALTER TABLE file_access_logs DROP CONSTRAINT UQ_file_access_logs_key;
ALTER TABLE file_access_logs DROP COLUMN action;
ALTER TABLE file_access_logs ADD COLUMN actions JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE file_access_logs
    ADD CONSTRAINT UQ_file_access_logs_key UNIQUE (module, document_id, file_id, username);

INSERT INTO file_access_logs
    (id, module, document_id, file_id, file_name, actions, username, fullname, unit,
     first_accessed_on, last_accessed_on, access_count)
SELECT
    uuid_generate_v4(), module, document_id, file_id, file_name, actions, username, fullname, unit,
    first_accessed_on, last_accessed_on, access_count
FROM file_access_logs_merged;

DROP TABLE file_access_logs_merged;

COMMENT ON TABLE file_access_logs IS 'Log xem/tải tệp đính kèm - DÙNG CHUNG mọi module (vbdi/vbden/duthao/hosocv/...). Model UPSERT theo (module,document_id,file_id,username): 1 dòng/tổ hợp (tệp, người dùng) BẤT KỂ loại thao tác, first_accessed_on bất biến, last_accessed_on/access_count/actions cập nhật mỗi lần xem/tải lại - tránh phình bảng theo số lượt xem lẫn theo số loại thao tác.';
COMMENT ON COLUMN file_access_logs.actions IS 'Mảng JSONB các loại thao tác đã từng ghi nhận trên tổ hợp (module,document_id,file_id,username), vd ["view","download"] - APPEND thao tác mới nếu chưa có (merge bằng operator jsonb "@>"/"||" ngay trong UPSERT), KHÔNG tách dòng riêng theo action';
COMMENT ON COLUMN file_access_logs.first_accessed_on IS 'Thời điểm xem/tải ĐẦU TIÊN của tổ hợp (module,document_id,file_id,username) - BẤT BIẾN, không ghi đè, bất kể loại thao tác nào xảy ra trước';
COMMENT ON COLUMN file_access_logs.access_count IS 'Tổng số lần xem/tải CỘNG DỒN mọi loại thao tác - tăng 1 mỗi lần ghi (kể cả lần đầu, bắt đầu = 1)';
