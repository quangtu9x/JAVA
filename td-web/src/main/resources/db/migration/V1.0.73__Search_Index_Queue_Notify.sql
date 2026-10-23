-- 16/10/2026: đánh thức worker NGAY khi có thay đổi thay vì để worker quét định kỳ mọi tenant (xem
-- docs/architecture/13-dong-bo-elasticsearch.md §2.1).
--
-- pg_notify trong trigger:
--   * PostgreSQL CHỈ gửi thông báo khi transaction COMMIT (rollback thì không) — đúng lúc dữ liệu đã thấy được.
--   * Các thông báo trùng (cùng kênh + nội dung) trong 1 transaction được GỘP thành 1 — 1 lệnh sửa 1000 dòng
--     của 1 bảng chỉ sinh 1 thông báo.
--   * Thông báo chỉ là TÍN HIỆU "có việc": dữ liệu vẫn nằm trong search_index_queue, nên lỡ thông báo (worker
--     đang khởi động lại, mất kết nối) KHÔNG mất thay đổi — worker có lượt quét dự phòng.
-- Nội dung thông báo = entity_type (chỉ để log/truy vết).

CREATE OR REPLACE FUNCTION search_index_enqueue() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO search_index_queue (entity_type, entity_id, operation) VALUES (TG_ARGV[0], OLD.id, 'D');
        PERFORM pg_notify('search_index', TG_ARGV[0]);
        RETURN OLD;
    END IF;
    INSERT INTO search_index_queue (entity_type, entity_id, operation)
    VALUES (TG_ARGV[0], NEW.id, CASE WHEN TG_OP = 'INSERT' THEN 'I' ELSE 'U' END);
    PERFORM pg_notify('search_index', TG_ARGV[0]);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
