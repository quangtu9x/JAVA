-- 16/10/2026: Đồng bộ Elasticsearch — hàng đợi thay đổi ghi NGAY TẠI DATABASE (xem
-- docs/architecture/13-dong-bo-elasticsearch.md).
--
-- Vì sao trigger mà không gọi index trong code ứng dụng: dữ liệu văn bản được ghi từ RẤT nhiều đường
-- (hàng chục use case td-web, td-woker/tdgateway ghi thẳng JDBC khi nhận văn bản, script chuyển đổi
-- Domino, sửa tay). Gọi index trong code chắc chắn sót → ES lệch DB. Trigger chạy TRONG CÙNG transaction
-- với lệnh ghi: commit thì có dòng hàng đợi, rollback thì không — không thể lệch, không mất thay đổi
-- kể cả khi Elasticsearch/worker đang dừng (hàng đợi giữ lại, worker chạy lại sẽ bù).
--
-- Hàng đợi CHỈ lưu (loại, id): worker đọc TRẠNG THÁI HIỆN TẠI của dòng lúc xử lý (dòng còn → upsert,
-- dòng mất → xoá khỏi ES), nên "operation" chỉ để truy vết. Id hàng đợi (tăng dần) dùng làm version
-- external của ES → bản ghi cũ đến muộn không đè được bản mới.
--
-- Migration chạy cho MỌI tenant lúc td-web khởi động (TenantFlywayMigrationService) — tích hợp ngay khi
-- triển khai. Không nạp dữ liệu cũ ở đây: worker tự nạp toàn bộ khi tạo index lần đầu (bootstrap).

CREATE TABLE IF NOT EXISTS search_index_queue (
    id           BIGSERIAL    PRIMARY KEY,
    entity_type  VARCHAR(50)  NOT NULL,
    entity_id    UUID         NOT NULL,
    operation    CHAR(1)      NOT NULL,            -- I/U/D (chỉ để truy vết), R = nạp lại/đối soát
    enqueued_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    attempts     INT          NOT NULL DEFAULT 0,
    next_try_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error   TEXT
);

CREATE INDEX IF NOT EXISTS ix_search_index_queue_due ON search_index_queue (next_try_at, id);
CREATE INDEX IF NOT EXISTS ix_search_index_queue_entity ON search_index_queue (entity_type, entity_id);

COMMENT ON TABLE search_index_queue IS 'Hàng đợi đồng bộ Elasticsearch, ghi bởi trigger search_index_enqueue() trong cùng transaction với dữ liệu; td-woker (SearchIndexSyncService) tiêu thụ. Xem docs/architecture/13-dong-bo-elasticsearch.md';

CREATE OR REPLACE FUNCTION search_index_enqueue() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO search_index_queue (entity_type, entity_id, operation) VALUES (TG_ARGV[0], OLD.id, 'D');
        RETURN OLD;
    END IF;
    INSERT INTO search_index_queue (entity_type, entity_id, operation)
    VALUES (TG_ARGV[0], NEW.id, CASE WHEN TG_OP = 'INSERT' THEN 'I' ELSE 'U' END);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 1 cặp trigger / bảng: INSERT/DELETE luôn ghi; UPDATE chỉ ghi khi dòng THỰC SỰ đổi (bỏ các lệnh UPDATE
-- không đổi gì). "documents" là bảng PARTITION (V1.0.26) — trigger cấp dòng trên bảng cha tự áp cho mọi
-- partition (PostgreSQL 11+). Thêm bảng mới cần index: thêm 1 migration tạo cặp trigger tương tự + khai
-- entity ở worker (worker.search-sync.entities).
DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT * FROM (VALUES
        ('documents',              'documents'),
        ('vbdi',                   'vbdi_documents'),
        ('duthao',                 'duthao_documents'),
        ('hosocv',                 'hosocv_documents'),
        ('phieutrinhtd',           'phieutrinhtd_documents'),
        ('vbcddh',                 'vbcddh_documents'),
        ('hosoluutru',             'hosoluutru_documents')
    ) AS v(entity_type, table_name)
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_search_index_%s_ins_del ON %I', t.entity_type, t.table_name);
        EXECUTE format('DROP TRIGGER IF EXISTS trg_search_index_%s_upd ON %I', t.entity_type, t.table_name);
        EXECUTE format('CREATE TRIGGER trg_search_index_%s_ins_del AFTER INSERT OR DELETE ON %I FOR EACH ROW EXECUTE PROCEDURE search_index_enqueue(%L)',
            t.entity_type, t.table_name, t.entity_type);
        -- So sánh qua to_jsonb: "OLD.* IS DISTINCT FROM NEW.*" lỗi nếu bảng có cột kiểu json (không có
        -- toán tử "=") — lỗi trong trigger làm hỏng CHÍNH lệnh UPDATE nghiệp vụ.
        EXECUTE format('CREATE TRIGGER trg_search_index_%s_upd AFTER UPDATE ON %I FOR EACH ROW WHEN (to_jsonb(OLD) IS DISTINCT FROM to_jsonb(NEW)) EXECUTE PROCEDURE search_index_enqueue(%L)',
            t.entity_type, t.table_name, t.entity_type);
    END LOOP;
END $$;
