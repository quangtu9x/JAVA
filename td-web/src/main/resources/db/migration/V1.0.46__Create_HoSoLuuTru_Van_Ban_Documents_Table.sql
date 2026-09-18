-- Port module "Hồ sơ lưu trữ" (hosoluutru) - docs/analysis/16-...md muc 6.2/8.1. Bang vat ly DOC LAP
-- cho van ban gan vao 1 ho so luu tru (form Domino vanban) - xem entity HoSoLuuTruVanBanDocument.
CREATE TABLE hosoluutru_van_ban_documents (
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

CREATE INDEX IX_hosoluutru_van_ban_documents_title ON hosoluutru_van_ban_documents(title);
CREATE INDEX IX_hosoluutru_van_ban_documents_document_type ON hosoluutru_van_ban_documents(document_type);
CREATE INDEX IX_hosoluutru_van_ban_documents_status ON hosoluutru_van_ban_documents(status);
CREATE INDEX IX_hosoluutru_van_ban_documents_created_on ON hosoluutru_van_ban_documents(created_on);
CREATE INDEX IX_hosoluutru_van_ban_documents_deleted_on ON hosoluutru_van_ban_documents(deleted_on);
CREATE INDEX idx_hosoluutru_van_ban_documents_dynamic_fields ON hosoluutru_van_ban_documents USING GIN (dynamic_fields);

COMMENT ON TABLE hosoluutru_van_ban_documents IS 'Van ban gan vao 1 ho so luu tru (form Domino vanban) - dynamic_fields.hoso_id lien ket cha';
