-- 17/10/2026: trích nội dung tệp đính kèm (Apache Tika + OCR Tesseract) để tìm kiếm toàn văn trong tệp — thay
-- "Văn bản đã bóc tách"/OCR chạy trên Elasticsearch của Domino (docs/architecture/14-trich-noi-dung-tep.md).
--
-- Luồng: file_metadata đổi → trigger đưa (entity_type='file', id tệp) vào search_index_queue (dùng chung hàng đợi +
-- NOTIFY của V1.0.72/V1.0.73) → td-woker làn "trích tệp" tải tệp từ MinIO, trích chữ, ghi vào bảng dưới, rồi đưa văn
-- bản cha vào hàng đợi để nạp lại Elasticsearch kèm nội dung tệp.

CREATE TABLE IF NOT EXISTS file_content_extracts (
    file_id        UUID          PRIMARY KEY,
    document_id    UUID,
    -- DONE: có nội dung | EMPTY: không có chữ | SKIPPED: bỏ qua (quá lớn / loại không hỗ trợ / đã bị thay thế)
    -- FAILED: lỗi sau khi thử hết số lần
    status         VARCHAR(20)   NOT NULL,
    content        TEXT,
    content_chars  INT           NOT NULL DEFAULT 0,
    truncated      BOOLEAN       NOT NULL DEFAULT FALSE,
    ocr_used       BOOLEAN       NOT NULL DEFAULT FALSE,
    content_type   VARCHAR(255),
    extractor      VARCHAR(100),
    error          TEXT,
    extracted_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_file_content_extracts_document ON file_content_extracts (document_id);

COMMENT ON TABLE file_content_extracts IS 'Nội dung chữ trích từ tệp đính kèm (file_metadata) bởi td-woker (Tika/OCR) — dùng cho tìm kiếm toàn văn (Elasticsearch files_text) và xem "Nội dung trích xuất". Xem docs/architecture/14-trich-noi-dung-tep.md';

-- INSERT/DELETE luôn đưa vào hàng đợi; UPDATE CHỈ khi đổi cột ảnh hưởng nội dung/gắn kết — KHÔNG kích khi chỉ
-- download_count/last_downloaded_at đổi (mỗi lượt tải tệp).
DROP TRIGGER IF EXISTS trg_search_index_file_ins_del ON file_metadata;
DROP TRIGGER IF EXISTS trg_search_index_file_upd ON file_metadata;
CREATE TRIGGER trg_search_index_file_ins_del AFTER INSERT OR DELETE ON file_metadata FOR EACH ROW EXECUTE PROCEDURE search_index_enqueue('file');
CREATE TRIGGER trg_search_index_file_upd AFTER UPDATE ON file_metadata FOR EACH ROW WHEN (OLD.file_path IS DISTINCT FROM NEW.file_path OR OLD.stored_filename IS DISTINCT FROM NEW.stored_filename OR OLD.document_id IS DISTINCT FROM NEW.document_id OR OLD.superseded_at IS DISTINCT FROM NEW.superseded_at OR OLD.bucket_name IS DISTINCT FROM NEW.bucket_name) EXECUTE PROCEDURE search_index_enqueue('file');
