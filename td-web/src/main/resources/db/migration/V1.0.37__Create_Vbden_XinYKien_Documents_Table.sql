-- N08 ("cho ý kiến" TVUB/TVNB/BCH/BTV/TTĐU), yêu cầu người dùng 31/08/2026 ("văn bản xin ý kiến sẽ
-- tạo riêng form (domino)... nên tạo bảng riêng"): bảng vật lý HOÀN TOÀN TÁCH BIỆT khỏi "documents"
-- (không phải partition/bảng con) cho toàn bộ "cụm xin ý kiến" — CẢ {@code vbden_xinykien} ("văn bản
-- đến giả" chính, Form="xinykien" phía Domino) VÀ {@code vbden_xinykien_res} (lịch sử "cho ý kiến",
-- discriminator {@code document_type} giống hệt cách "documents" phân biệt module — ĐÚNG tiền lệ
-- {@code vbdi_documents} (V1.0.29, gộp CẢ vbdi + vbdi_res trong 1 bảng), KHÁC tiền lệ
-- {@code duthao_documents}/{@code draft_process_documents} (V1.0.28/V1.0.30, TÁCH RIÊNG 2 bảng) —
-- chọn theo mẫu vbdi vì cùng bản chất "1 document chính + 1 loại lịch sử duy nhất đi kèm", không có
-- nhiều loại lịch sử tách biệt như cụm dự thảo (draft_process gộp cả DT-007 lẫn phối hợp).
--
-- Cấu trúc CỐ Ý giống "documents"/"duthao_documents"/"vbdi_documents" gốc - cùng kiến trúc BL-101
-- (Dynamic Data Engine, field nghiệp vụ biến thiên trong dynamic_fields JSONB) - xem entity
-- XinYKienDocument (com.td.domain.vbden) + repository XinYKienDocumentRepository/
-- PostgresXinYKienDocumentRepository (com.td.application.vbden / com.td.infrastructure.persistence.repository).
CREATE TABLE vbden_xinykien_documents (
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

CREATE INDEX IX_vbden_xinykien_documents_title ON vbden_xinykien_documents(title);
CREATE INDEX IX_vbden_xinykien_documents_document_type ON vbden_xinykien_documents(document_type);
CREATE INDEX IX_vbden_xinykien_documents_status ON vbden_xinykien_documents(status);
CREATE INDEX IX_vbden_xinykien_documents_created_on ON vbden_xinykien_documents(created_on);
CREATE INDEX IX_vbden_xinykien_documents_last_modified_on ON vbden_xinykien_documents(last_modified_on);
CREATE INDEX IX_vbden_xinykien_documents_deleted_on ON vbden_xinykien_documents(deleted_on);
CREATE INDEX idx_vbden_xinykien_documents_dynamic_fields ON vbden_xinykien_documents USING GIN (dynamic_fields);

COMMENT ON TABLE vbden_xinykien_documents IS 'Bảng vật lý ĐỘC LẬP cho cụm "xin ý kiến" (document_type=vbden_xinykien/vbden_xinykien_res, N08) - tách hẳn khỏi "documents" dùng chung của vbden gốc và mọi module khác';
COMMENT ON COLUMN vbden_xinykien_documents.tags_json IS 'JSON array for document tags';
COMMENT ON COLUMN vbden_xinykien_documents.attributes_json IS 'JSON object for dynamic business attributes';
COMMENT ON COLUMN vbden_xinykien_documents.metadata_json IS 'JSON object for system metadata';
COMMENT ON COLUMN vbden_xinykien_documents.dynamic_fields IS 'Field động (key-value) suy luận kiểu từ hậu tố tên field (_date, _number, _obj, _aut, _rdr, _tlist), tương đương NotesDocument Item động phía Domino';
