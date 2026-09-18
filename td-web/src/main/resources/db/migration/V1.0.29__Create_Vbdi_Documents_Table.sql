-- BL-220 (VBDi Core), yêu cầu người dùng (tách văn bản đi khỏi bảng dùng chung): bảng vật lý HOÀN
-- TOÀN TÁCH BIỆT khỏi "documents" (không phải partition/bảng con) cho toàn bộ "cụm văn bản đi" — CẢ
-- {@code vbdi} (document chính) VÀ {@code vbdi_res} (bản ghi trình ký, discriminator
-- {@code document_type} giống hệt cách "documents" phân biệt module, chỉ khác: bảng này CHỈ 2
-- document_type đó, không module nào khác chạm vào).
--
-- Cấu trúc CỐ Ý giống "documents" gốc (V1.0.3 + V1.0.12) - cùng kiến trúc BL-101 (Dynamic Data
-- Engine, field nghiệp vụ biến thiên trong dynamic_fields JSONB) - xem entity VbdiDocument
-- (com.td.domain.vbdi) + repository VbdiDocumentRepository/PostgresVbdiDocumentRepository
-- (com.td.application.vbdi / com.td.infrastructure.persistence.repository).
CREATE TABLE vbdi_documents (
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

CREATE INDEX IX_vbdi_documents_title ON vbdi_documents(title);
CREATE INDEX IX_vbdi_documents_document_type ON vbdi_documents(document_type);
CREATE INDEX IX_vbdi_documents_status ON vbdi_documents(status);
CREATE INDEX IX_vbdi_documents_created_on ON vbdi_documents(created_on);
CREATE INDEX IX_vbdi_documents_last_modified_on ON vbdi_documents(last_modified_on);
CREATE INDEX IX_vbdi_documents_deleted_on ON vbdi_documents(deleted_on);
CREATE INDEX idx_vbdi_documents_dynamic_fields ON vbdi_documents USING GIN (dynamic_fields);

COMMENT ON TABLE vbdi_documents IS 'Bảng vật lý ĐỘC LẬP cho cụm văn bản đi (document_type=vbdi/vbdi_res) - BL-220, yêu cầu người dùng - tách hẳn khỏi "documents" dùng chung của mọi module khác';
COMMENT ON COLUMN vbdi_documents.tags_json IS 'JSON array for document tags';
COMMENT ON COLUMN vbdi_documents.attributes_json IS 'JSON object for dynamic business attributes';
COMMENT ON COLUMN vbdi_documents.metadata_json IS 'JSON object for system metadata';
COMMENT ON COLUMN vbdi_documents.dynamic_fields IS 'Field động (key-value) suy luận kiểu từ hậu tố tên field (_date, _number, _obj, _aut, _rdr, _tlist), tương đương NotesDocument Item động phía Domino';