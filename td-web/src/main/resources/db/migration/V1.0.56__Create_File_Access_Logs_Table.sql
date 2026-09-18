-- 09/09/2026 (yêu cầu người dùng "log xem tệp đính kèm cần thiết cho các module khác nữa... tránh
-- tạo nhiều bản ghi nếu người dùng xem/tải 1 tệp, chỉ lưu thời gian đầu tiên và cuối cùng"):
-- bảng GENERIC dùng chung cho MỌI module (vbdi/vbden/duthao/hosocv/...) — "module" phân biệt tệp
-- đính kèm này thuộc module nào (tránh nhầm lẫn khi hiển thị, vì file_metadata vốn generic không có
-- cột phân biệt module). Không dùng model "append-only, mỗi dòng = 1 sự kiện" (kiểu
-- vbdi_user_action_logs) vì xem/tải là hành động TẦN SUẤT CAO, lặp lại nhiều lần trên CÙNG 1 tệp —
-- sẽ khiến bảng phình vô hạn theo lượt xem, không phải theo số tệp/số người dùng thực tế.
--
-- (Đánh số V1.0.56 thay vì V1.0.55 — bản đầu 09/09/2026 dự định số V1.0.55 nhưng ĐÃ ĐỔI THIẾT KẾ
-- giữa chừng trước khi kịp deploy rộng; do V1.0.55 gốc (mở rộng vbdi_user_action_logs) đã lỡ áp dụng
-- vào 1 số DB khi test, không thể sửa nội dung migration đã áp dụng — phải giữ nguyên V1.0.55 gốc
-- và đánh số MỚI cho thiết kế thay thế này, xem V1.0.57 dọn lại 2 cột thừa của V1.0.55 gốc.)
--
-- Model UPSERT: UNIQUE (module, document_id, file_id, username, action) — 1 dòng/1 tổ hợp
-- (tệp, người dùng, loại thao tác) DUY NHẤT, ghi đè last_accessed_on + tăng access_count mỗi lần
-- xem/tải lại (xem PostgresFileAccessLogRepository#recordAccess, INSERT ... ON CONFLICT DO UPDATE
-- nguyên tử — cùng kỹ thuật numbering_counters, V1.0.14). Số dòng bị CHẶN TRÊN bởi
-- (số tệp × số người dùng × 2 loại thao tác), không tăng theo số lượt xem — tối ưu cả lưu trữ lẫn
-- hiệu năng ghi (1 statement/lần, dùng đúng unique index, không cần SELECT-rồi-UPDATE 2 bước).
CREATE TABLE file_access_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    module VARCHAR(50) NOT NULL,
    document_id UUID NOT NULL,
    file_id UUID NOT NULL,
    file_name VARCHAR(500),
    action VARCHAR(20) NOT NULL,
    username VARCHAR(100) NOT NULL,
    fullname VARCHAR(300),
    unit VARCHAR(500),
    first_accessed_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_accessed_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    access_count INT NOT NULL DEFAULT 1,
    CONSTRAINT UQ_file_access_logs_key UNIQUE (module, document_id, file_id, username, action)
);

-- Truy vấn chính: "toàn bộ lượt xem/tải của 1 văn bản" (danh sách hiển thị FE, lọc theo module để
-- tránh document_id trùng lẫn (dù về lý thuyết UUID gần như không trùng xuyên bảng) — luôn CÓ CẢ 2
-- điều kiện trong mọi câu đọc, nên gộp làm 1 index duy nhất thay vì 2 index riêng module/document_id.
CREATE INDEX IX_file_access_logs_module_document ON file_access_logs(module, document_id);
-- Truy vấn phụ: "ai đã xem/tải tệp X" (không lọc theo văn bản/module) - vd trang quản trị.
CREATE INDEX IX_file_access_logs_file_id ON file_access_logs(file_id);

COMMENT ON TABLE file_access_logs IS 'Log xem/tải tệp đính kèm - DÙNG CHUNG mọi module (vbdi/vbden/duthao/hosocv/...). Model UPSERT theo (module,document_id,file_id,username,action): 1 dòng/tổ hợp, first_accessed_on bất biến, last_accessed_on + access_count cập nhật mỗi lần xem/tải lại - tránh phình bảng theo số lượt xem.';
COMMENT ON COLUMN file_access_logs.module IS 'Module sở hữu document_id (vbdi/vbden/duthao/hosocv/...) - file_metadata dùng chung mọi module, không tự phân biệt được, phải lưu tường minh để tránh nhầm lẫn khi hiển thị FE';
COMMENT ON COLUMN file_access_logs.file_name IS 'Tên gốc tệp - LÀM MỚI mỗi lần ghi (tra server-side qua GetFileUseCase, không tin client) - phản ánh tên tại lần xem GẦN NHẤT';
COMMENT ON COLUMN file_access_logs.action IS 'Loại thao tác: "view" (xem) hoặc "download" (tải)';
COMMENT ON COLUMN file_access_logs.fullname IS 'Tên đầy đủ người thực hiện - server-authoritative (UserDisplayInfoService), LÀM MỚI mỗi lần ghi';
COMMENT ON COLUMN file_access_logs.unit IS 'Cơ quan/đơn vị/phòng ban người thực hiện (nối " / ", xem UserDisplayInfoService) - LÀM MỚI mỗi lần ghi, phản ánh tổ chức TẠI THỜI ĐIỂM xem gần nhất';
COMMENT ON COLUMN file_access_logs.first_accessed_on IS 'Thời điểm xem/tải ĐẦU TIÊN của tổ hợp (module,document_id,file_id,username,action) - BẤT BIẾN, không ghi đè';
COMMENT ON COLUMN file_access_logs.last_accessed_on IS 'Thời điểm xem/tải GẦN NHẤT - cập nhật mỗi lần ghi';
COMMENT ON COLUMN file_access_logs.access_count IS 'Số lần xem/tải - tăng 1 mỗi lần ghi (kể cả lần đầu, bắt đầu = 1)';
