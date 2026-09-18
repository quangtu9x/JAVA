-- BL-250 (VBCDDH Core), bổ sung 08/09/2026 (yêu cầu người dùng "port sang JAVA, tuân thủ kiến trúc,
-- tạo bảng riêng, sử dụng dynamic fields"): bảng vật lý HOÀN TOÀN TÁCH BIỆT khỏi "documents" (không
-- phải partition/bảng con) cho toàn bộ "cụm vbcddh" — CẢ {@code vbcddh} (văn bản chỉ đạo + nhiệm vụ,
-- phân biệt bằng field động "form") VÀ {@code vbcddh_res} (bản ghi tiến độ/kết quả xử lý), đúng tiền
-- lệ vbdi_documents (V1.0.29, BL-220).
--
-- Cấu trúc CỐ Ý giống "documents" gốc (V1.0.3 + V1.0.12) và "vbdi_documents" (V1.0.29) - cùng kiến
-- trúc BL-101 (Dynamic Data Engine, field nghiệp vụ biến thiên trong dynamic_fields JSONB) - xem
-- entity VbcddhDocument (com.td.domain.vbcddh) + repository VbcddhDocumentRepository/
-- PostgresVbcddhDocumentRepository (com.td.application.vbcddh / com.td.infrastructure.persistence.repository).
CREATE TABLE vbcddh_documents (
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

CREATE INDEX IX_vbcddh_documents_title ON vbcddh_documents(title);
CREATE INDEX IX_vbcddh_documents_document_type ON vbcddh_documents(document_type);
CREATE INDEX IX_vbcddh_documents_status ON vbcddh_documents(status);
CREATE INDEX IX_vbcddh_documents_created_on ON vbcddh_documents(created_on);
CREATE INDEX IX_vbcddh_documents_last_modified_on ON vbcddh_documents(last_modified_on);
CREATE INDEX IX_vbcddh_documents_deleted_on ON vbcddh_documents(deleted_on);
CREATE INDEX idx_vbcddh_documents_dynamic_fields ON vbcddh_documents USING GIN (dynamic_fields);

COMMENT ON TABLE vbcddh_documents IS 'Bảng vật lý ĐỘC LẬP cho cụm vbcddh (document_type=vbcddh/vbcddh_res) - BL-250 bổ sung 08/09/2026, yêu cầu người dùng - tách hẳn khỏi "documents" dùng chung của mọi module khác';
COMMENT ON COLUMN vbcddh_documents.tags_json IS 'JSON array for document tags';
COMMENT ON COLUMN vbcddh_documents.attributes_json IS 'JSON object for dynamic business attributes';
COMMENT ON COLUMN vbcddh_documents.metadata_json IS 'JSON object for system metadata';
COMMENT ON COLUMN vbcddh_documents.dynamic_fields IS 'Field động (key-value) suy luận kiểu từ hậu tố tên field (_date, _number, _obj, _aut, _rdr, _tlist), tương đương NotesDocument Item động phía Domino';
