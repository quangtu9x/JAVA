-- Yêu cầu người dùng 27/08/2026 ("thay đổi quản lý tệp đính kèm theo id cha (draft_document),
-- nhưng draft_process vẫn thể hiện được đúng lần gắn kèm"): đảo ngược thiết kế trước đó (tệp gắn
-- trực tiếp vào id của TỪNG vết draft_process) — nay tệp LUÔN lưu theo document_id = id DỰ THẢO
-- CHA (khớp mọi module khác đang dùng /api/v1/documents/{id}/files), thêm cột process_id (nullable)
-- để đánh dấu tệp được đính kèm vào ĐÚNG LẦN xử lý nào (GanKemTepDuThaoUseCase ghi khi upload đi
-- kèm với 1 vết draft_process cụ thể). process_id NULL = tệp không gắn với vết xử lý nào (mọi
-- module khác ngoài duthao, hoặc tệp gắn thẳng vào dự thảo ngoài luồng "xu ly dinh kem").
ALTER TABLE file_metadata ADD COLUMN IF NOT EXISTS process_id UUID;

CREATE INDEX IF NOT EXISTS idx_file_metadata_document_id_process_id
    ON file_metadata(document_id, process_id);

COMMENT ON COLUMN file_metadata.process_id IS 'Id vết xử lý (vd draft_process_documents.id) mà tệp được đính kèm cùng lúc - NULL nếu tệp không gắn với 1 lần xử lý cụ thể nào. Cùng document_id (id dự thảo cha), KHÔNG thay thế document_id.';
