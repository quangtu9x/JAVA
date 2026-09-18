-- Mở rộng BL-240 (HoSoCV Core), yêu cầu người dùng ("tạo bảng mới, sử dụng jsonb (dynamic field)
-- như các module đã thực hiện"): bảng vật lý ĐỘC LẬP cho hồ sơ công việc, đúng tiền lệ V1.0.28
-- (duthao_documents)/V1.0.29 (vbdi_documents) — tách hẳn khỏi "documents" dùng chung, thay cho
-- thiết kế ban đầu của BL-240 (document_type='hosocv' trong bảng documents chung).
--
-- Cấu trúc CỐ Ý giống "documents"/"duthao_documents" - cùng kiến trúc BL-101 (Dynamic Data Engine,
-- field nghiệp vụ biến thiên trong dynamic_fields JSONB) - xem entity HoSoCVDocument
-- (com.td.domain.hosocv) + repository HoSoCVDocumentRepository/PostgresHoSoCVDocumentRepository
-- (com.td.application.hosocv / com.td.infrastructure.persistence.repository).
CREATE TABLE hosocv_documents (
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

CREATE INDEX IX_hosocv_documents_title ON hosocv_documents(title);
CREATE INDEX IX_hosocv_documents_document_type ON hosocv_documents(document_type);
CREATE INDEX IX_hosocv_documents_status ON hosocv_documents(status);
CREATE INDEX IX_hosocv_documents_created_on ON hosocv_documents(created_on);
CREATE INDEX IX_hosocv_documents_last_modified_on ON hosocv_documents(last_modified_on);
CREATE INDEX IX_hosocv_documents_deleted_on ON hosocv_documents(deleted_on);
CREATE INDEX idx_hosocv_documents_dynamic_fields ON hosocv_documents USING GIN (dynamic_fields);

COMMENT ON TABLE hosocv_documents IS 'Bảng vật lý ĐỘC LẬP cho hồ sơ công việc (document_type=hosocv) - mở rộng BL-240, tách khỏi "documents" dùng chung của mọi module khác';
COMMENT ON COLUMN hosocv_documents.tags_json IS 'JSON array for document tags';
COMMENT ON COLUMN hosocv_documents.attributes_json IS 'JSON object for dynamic business attributes';
COMMENT ON COLUMN hosocv_documents.metadata_json IS 'JSON object for system metadata';
COMMENT ON COLUMN hosocv_documents.dynamic_fields IS 'Field động (key-value) suy luận kiểu từ hậu tố tên field (_date, _number, _obj, _aut, _rdr, _tlist), tương đương NotesDocument Item động phía Domino';

-- Cutover dữ liệu: BL-240 ban đầu (06/08/2026) đã ghi 1 số bản ghi hosocv vào "documents" dùng
-- chung (document_type='hosocv'). Môi trường hiện tại là dev/staging (BL-001: Big-bang cutover,
-- chưa có dữ liệu thật) nên chuyển thẳng thay vì giữ song song 2 nguồn.
INSERT INTO hosocv_documents (
    id, title, document_type, status, content, tags_json, attributes_json, metadata_json,
    dynamic_fields, version_no, created_by, created_on, last_modified_by, last_modified_on,
    deleted_on, deleted_by
)
SELECT
    id, title, document_type, status, content, tags_json, attributes_json, metadata_json,
    dynamic_fields, version_no, created_by, created_on, last_modified_by, last_modified_on,
    deleted_on, deleted_by
FROM documents
WHERE LOWER(document_type) = 'hosocv';

DELETE FROM documents WHERE LOWER(document_type) = 'hosocv';
