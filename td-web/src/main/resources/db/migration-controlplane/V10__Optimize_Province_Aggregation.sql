-- Tối ưu kho tổng hợp toàn tỉnh (V9) cho dữ liệu lớn — xem docs/analysis/26 §6.

-- Thời điểm dữ liệu kho THỰC SỰ thay đổi (khác last_success_on: chạy xong mà không đổi gì) — làm "phiên bản"
-- cho bộ nhớ đệm kết quả truy vấn (ProvinceQueryCache): không đổi dữ liệu thì không tính lại.
ALTER TABLE province_sync_state ADD COLUMN IF NOT EXISTS last_data_change_on TIMESTAMP WITH TIME ZONE;

-- Danh sách tìm kiếm khi KHÔNG lọc loại văn bản + lọc khoảng ngày cho thống kê: sắp/lọc theo ngày.
CREATE INDEX IF NOT EXISTS ix_province_documents_date
    ON province_documents (doc_date DESC, created_on DESC) WHERE deleted = FALSE;

-- Đồng bộ toàn bộ: ghi lại id đã thấy ở nguồn để đánh dấu xoá phần còn lại, thay vì cập nhật synced_on của MỌI
-- dòng (upsert nay bỏ qua dòng không đổi). Bảng nháp, không cần WAL.
CREATE UNLOGGED TABLE IF NOT EXISTS province_sync_seen (
    tenant_id   VARCHAR(100) NOT NULL,
    document_id UUID         NOT NULL,
    PRIMARY KEY (tenant_id, document_id)
);
