-- Yêu cầu người dùng 25/08/2026 ("tách draft_process sang bảng riêng, bảng vật lý như
-- duthao_documents, không lưu chung với duthao_documents"): tách document lịch sử xử lý
-- (document_type = 'draft_process', DT-007) khỏi {@code duthao_documents} sang bảng vật lý
-- HOÀN TOÀN RIÊNG — cùng khuôn với các lần tách trước:
--   V1.0.27 đảo ngược partition của "documents" → V1.0.28 tạo duthao_documents (duthao +
--   draft_process chung bảng) → V1.0.30 NÀY tách nốt draft_process ra bảng riêng.
-- Sau migration này: duthao_documents CHỈ chứa document_type='duthao';
-- draft_process_documents CHỈ chứa document_type='draft_process'.
--
-- Cấu trúc CỐ Ý giống hệt V1.0.28 (cùng kiến trúc BL-101 Dynamic Data Engine) - xem entity
-- DraftProcessDocument (com.td.domain.duthao) + DraftProcessDocumentRepository/
-- PostgresDraftProcessDocumentRepository + AddDraftProcessDocumentUseCase.
--
-- DỮ LIỆU CŨ: môi trường đang ở giai đoạn test (đã thống nhất từ lần tách V1.0.28 — không cần
-- tương thích ngược), KHÔNG tự di chuyển các dòng document_type='draft_process' còn sót trong
-- duthao_documents. Nếu cần giữ lịch sử test, copy tay:
--   INSERT INTO draft_process_documents (title, document_type, status, content, tags_json,
--       attributes_json, metadata_json, dynamic_fields)
--   SELECT title, document_type, status, content, tags_json, attributes_json, metadata_json,
--          dynamic_fields FROM duthao_documents WHERE document_type = 'draft_process';
CREATE TABLE draft_process_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(300) NOT NULL,
    document_type VARCHAR(100),
    status VARCHAR(50),
    content TEXT,
    tags_json TEXT NOT NULL DEFAULT '[]',
    attributes_json TEXT NOT NULL DEFAULT '{}',
    metadata_json TEXT NOT NULL DEFAULT '{}',
    dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    version_no BIGINT NOT NULL DEFAULT 1,
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

CREATE INDEX IX_draft_process_documents_title ON draft_process_documents(title);
CREATE INDEX IX_draft_process_documents_document_type ON draft_process_documents(document_type);
CREATE INDEX IX_draft_process_documents_status ON draft_process_documents(status);
CREATE INDEX IX_draft_process_documents_created_on ON draft_process_documents(created_on);
CREATE INDEX IX_draft_process_documents_last_modified_on ON draft_process_documents(last_modified_on);
CREATE INDEX IX_draft_process_documents_deleted_on ON draft_process_documents(deleted_on);
CREATE INDEX idx_draft_process_documents_dynamic_fields ON draft_process_documents USING GIN (dynamic_fields);

COMMENT ON TABLE draft_process_documents IS 'Bảng vật lý ĐỘC LẬP cho lịch sử xử lý dự thảo (document_type=draft_process, DT-007) - tách khỏi duthao_documents theo yêu cầu người dùng 25/08/2026 - mỗi action workflow (chuyenduyet/chuyenkyduyet/ký/trả lại/chuyển người khác/...) ghi 1 dòng vết xử lý trỏ parentid về dự thảo cha';
COMMENT ON COLUMN draft_process_documents.dynamic_fields IS 'Field động (key-value): parentid/action/status/oldstatus/receiver/receiver_username/opinion/creator - đúng tên field Domino DraftProcesses.java';