-- Bugfix 04/09/2026 (người dùng báo lỗi thật khi "Gửi lại toàn bộ" ở màn hình "Gửi văn bản":
-- insert outbox_events FAIL với "null value in column 'event_type' violates not-null constraint").
--
-- TÁCH RIÊNG thành migration MỚI (không sửa lại V1.0.40 đã áp dụng thành công — sửa 1 migration đã
-- chạy sẽ làm Flyway từ chối khởi động vì lệch checksum, đúng bài học từ chính lần sửa trước).
--
-- Nguyên nhân: 1 vài database tenant có bảng "outbox_events" tồn tại SẴN TỪ TRƯỚC (khác nguồn gốc
-- với migration này — có thể 1 lần thử nghiệm/prototype cũ dựng bảng theo ĐÚNG tên cột đề xuất ở
-- tài liệu thiết kế gốc docs/architecture/08 §6.2, vd cột "event_type" — KHÁC tên cột thật entity
-- Java đang dùng là "task_type"). V1.0.40 chỉ THÊM cột còn thiếu (ADD COLUMN IF NOT EXISTS), KHÔNG
-- dọn cột thừa/lạ — cột "event_type" (hoặc cột lạ khác tương tự) vẫn NOT NULL mà không ai điền giá
-- trị (entity Java không biết tới cột này) → insert luôn thất bại, rollback CẢ giao dịch "Gửi văn
-- bản" (không chỉ riêng bước publish).
--
-- Quét ĐỘNG (không đoán tên cụ thể — phòng còn cột lạ khác chưa phát hiện) mọi cột NOT NULL KHÔNG
-- thuộc danh sách cột entity OutboxEvent đang dùng, gỡ ràng buộc NOT NULL — an toàn vì chỉ nới lỏng
-- ràng buộc (không xoá cột/dữ liệu), cột lạ đó từ nay coi như không dùng tới.
DO $$
DECLARE
    col RECORD;
BEGIN
    FOR col IN
        SELECT column_name FROM information_schema.columns
        WHERE table_name = 'outbox_events'
          AND is_nullable = 'NO'
          AND column_name NOT IN (
            'id', 'aggregate_type', 'aggregate_id', 'task_type', 'correlation_id', 'source',
            'payload_json', 'status', 'attempts', 'next_try_at', 'last_error',
            'created_by', 'created_on', 'last_modified_by', 'last_modified_on', 'deleted_on', 'deleted_by'
          )
    LOOP
        EXECUTE format('ALTER TABLE outbox_events ALTER COLUMN %I DROP NOT NULL', col.column_name);
    END LOOP;
END $$;
