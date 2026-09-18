-- Yêu cầu người dùng 04/09/2026 ("hoàn thiện chức năng Gửi văn bản để gửi sang rabbitmq hoặc
-- kafka... viết class chung để sau có thể tái dùng"): bảng outbox GENERIC (KHÔNG riêng cho vbdi) —
-- đúng "Outbox pattern" đã thiết kế ở
-- docs/architecture/08-thay-the-domino-router-voi-rabbitmq-minio-postgresql.md §3.1/§6.2 và cụ thể
-- hoá cho vbdi ở docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md §6.1: ghi 1 dòng
-- TRONG CÙNG transaction với nghiệp vụ (vd AddVbdiSendLogUseCase) — OutboxDispatcherScheduler
-- (td-infrastructure) đọc PENDING định kỳ rồi publish qua RabbitMQ/Kafka (chọn bằng cấu hình
-- app.messaging.broker), tránh publish "ảo" khi transaction nghiệp vụ rollback.
--
-- Bảng này áp dụng cho MỌI tenant (chạy qua cơ chế tự động migrate mọi tenant lúc khởi động, xem
-- docs/architecture/06-database-per-tenant-multi-tenant.md) — mỗi tenant có outbox RIÊNG (nguồn sự
-- thật cho message publish RA từ chính tenant đó).
--
-- Bugfix 04/09/2026 (người dùng báo lỗi thật khi chạy): 1 vài database tenant đã có SẴN 1 bảng tên
-- "outbox_events" TRƯỚC migration này (khác schema — thiếu hẳn next_try_at/last_error/... — nhiều
-- khả năng do thử nghiệm/thao tác tay từ trước, KHÔNG phải do chính migration này tạo dở dang) —
-- "CREATE TABLE IF NOT EXISTS" chỉ no-op khi bảng đã tồn tại, KHÔNG bổ sung cột còn thiếu, khiến
-- CREATE INDEX ngay sau đó lỗi "column does not exist". Chuyển hẳn sang "CREATE TABLE (cột tối
-- thiểu) + ALTER TABLE ADD COLUMN IF NOT EXISTS cho từng cột còn lại" — tự phục hồi ĐÚNG schema dù
-- bảng cũ thiếu bất kỳ cột nào, không phụ thuộc giả định "bảng cũ nếu có thì đã đúng cấu trúc".
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4()
);

ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS aggregate_type VARCHAR(50);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS aggregate_id UUID;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS task_type VARCHAR(100);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(100);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS source VARCHAR(50);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS payload_json JSONB;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_try_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS last_error TEXT;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS last_modified_by UUID;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS last_modified_on TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS deleted_on TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS deleted_by UUID;

-- Bảng có thể đã tồn tại TRƯỚC migration (xem ghi chú trên) với các cột NOT NULL bỏ trống —
-- backfill giá trị mặc định hợp lý rồi mới ép NOT NULL, tránh lỗi "column contains null values"
-- nếu ALTER TABLE ADD COLUMN ở trên chạy trên bảng đã có sẵn dòng dữ liệu cũ không khớp cột mới.
UPDATE outbox_events SET aggregate_type = 'unknown' WHERE aggregate_type IS NULL;
UPDATE outbox_events SET aggregate_id = uuid_generate_v4() WHERE aggregate_id IS NULL;
UPDATE outbox_events SET task_type = 'unknown' WHERE task_type IS NULL;
UPDATE outbox_events SET payload_json = '[]'::jsonb WHERE payload_json IS NULL;
ALTER TABLE outbox_events ALTER COLUMN aggregate_type SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN aggregate_id SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN task_type SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN payload_json SET NOT NULL;

-- Truy vấn chính của OutboxDispatcherScheduler: WHERE status='PENDING' AND next_try_at <= now()
-- ORDER BY created_on — index tổng hợp (status, next_try_at) đủ cho cả filter lẫn không cần sort
-- riêng (Postgres có thể dùng index scan theo next_try_at trong phạm vi status='PENDING').
CREATE INDEX IF NOT EXISTS IX_outbox_events_status_next_try_at ON outbox_events(status, next_try_at);
CREATE INDEX IF NOT EXISTS IX_outbox_events_aggregate ON outbox_events(aggregate_type, aggregate_id);

COMMENT ON TABLE outbox_events IS 'Outbox pattern GENERIC (không riêng module nào) - ghi trong cùng transaction với nghiệp vụ, OutboxDispatcherScheduler publish ra RabbitMQ/Kafka định kỳ (yêu cầu người dùng 04/09/2026)';
COMMENT ON COLUMN outbox_events.task_type IS 'vd "vbdi.delivery" - quyết định routing key (Rabbit)/handler bên Worker consume';
COMMENT ON COLUMN outbox_events.payload_json IS 'Mảng units[] đã serialize JSON (xem com.td.application.messaging.MessageUnitSpec) - scheduler đọc lại nguyên văn, không biết ý nghĩa nghiệp vụ';
COMMENT ON COLUMN outbox_events.status IS 'PENDING (chờ/đang thử lại) / SENT (đã publish thành công) / FAILED (quá số lần thử tối đa)';
