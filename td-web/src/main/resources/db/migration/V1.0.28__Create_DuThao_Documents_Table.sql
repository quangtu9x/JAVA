-- BL-230 (DuThao Core), yêu cầu người dùng 24/08/2026 ("tạo bảng riêng, để độc lập dữ liệu"): bảng
-- vật lý HOÀN TOÀN TÁCH BIỆT khỏi "documents" (không phải partition/bảng con — xem V1.0.27 đảo
-- ngược cách làm partition trước đó) cho toàn bộ "cụm dự thảo" — CẢ {@code duthao} (document chính)
-- VÀ {@code draft_process} (DT-007, lịch sử xử lý, discriminator {@code document_type} giống hệt
-- cách "documents" phân biệt module, chỉ khác: bảng này CHỈ 2 document_type đó, không module nào
-- khác chạm vào).
--
-- Cấu trúc CỐ Ý giống "documents" gốc (V1.0.3 + V1.0.12) - cùng kiến trúc BL-101 (Dynamic Data
-- Engine, field nghiệp vụ biến thiên trong dynamic_fields JSONB) - xem entity DuThaoDocument
-- (com.td.domain.duthao) + repository DuThaoDocumentRepository/PostgresDuThaoDocumentRepository
-- (com.td.application.duthao / com.td.infrastructure.persistence.repository).
CREATE TABLE duthao_documents (
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

CREATE INDEX IX_duthao_documents_title ON duthao_documents(title);
CREATE INDEX IX_duthao_documents_document_type ON duthao_documents(document_type);
CREATE INDEX IX_duthao_documents_status ON duthao_documents(status);
CREATE INDEX IX_duthao_documents_created_on ON duthao_documents(created_on);
CREATE INDEX IX_duthao_documents_last_modified_on ON duthao_documents(last_modified_on);
CREATE INDEX IX_duthao_documents_deleted_on ON duthao_documents(deleted_on);
CREATE INDEX idx_duthao_documents_dynamic_fields ON duthao_documents USING GIN (dynamic_fields);

COMMENT ON TABLE duthao_documents IS 'Bảng vật lý ĐỘC LẬP cho cụm dự thảo (document_type=duthao/draft_process) - BL-230, yêu cầu người dùng 24/08/2026 - tách hẳn khỏi "documents" dùng chung của mọi module khác';
COMMENT ON COLUMN duthao_documents.tags_json IS 'JSON array for document tags';
COMMENT ON COLUMN duthao_documents.attributes_json IS 'JSON object for dynamic business attributes';
COMMENT ON COLUMN duthao_documents.metadata_json IS 'JSON object for system metadata';
COMMENT ON COLUMN duthao_documents.dynamic_fields IS 'Field động (key-value) suy luận kiểu từ hậu tố tên field (_date, _number, _obj, _aut, _rdr, _tlist), tương đương NotesDocument Item động phía Domino';
