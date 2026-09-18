-- Port module "Hồ sơ lưu trữ" (hosoluutru) - docs/analysis/16-...md muc 6.5/8.1. Bang vat ly DOC LAP
-- gop 3 danh muc dung chung: kyhieuhoso/chatluong/loaihinhtailieu (cung 1 UI Domino, phan biet bang
-- cot document_type) - xem entity HoSoLuuTruDanhMucDocument.
CREATE TABLE hosoluutru_danh_muc_documents (
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

CREATE INDEX IX_hosoluutru_danh_muc_documents_document_type ON hosoluutru_danh_muc_documents(document_type);
CREATE INDEX IX_hosoluutru_danh_muc_documents_deleted_on ON hosoluutru_danh_muc_documents(deleted_on);
CREATE INDEX idx_hosoluutru_danh_muc_documents_dynamic_fields ON hosoluutru_danh_muc_documents USING GIN (dynamic_fields);

COMMENT ON TABLE hosoluutru_danh_muc_documents IS '3 danh muc dung chung cua hosoluutru: kyhieuhoso/chatluong/loaihinhtailieu, phan biet bang document_type';
