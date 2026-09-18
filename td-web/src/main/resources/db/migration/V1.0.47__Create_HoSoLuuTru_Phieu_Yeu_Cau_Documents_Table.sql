-- Port module "Hồ sơ lưu trữ" (hosoluutru) - docs/analysis/16-...md muc 6.3/8.1. Bang vat ly DOC LAP
-- cho phieu yeu cau khai thac (muon/tra) tai lieu luu tru (form Domino phieuyeucau) - xem entity
-- HoSoLuuTruPhieuYeuCauDocument.
CREATE TABLE hosoluutru_phieu_yeu_cau_documents (
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

CREATE INDEX IX_hosoluutru_phieu_yeu_cau_documents_title ON hosoluutru_phieu_yeu_cau_documents(title);
CREATE INDEX IX_hosoluutru_phieu_yeu_cau_documents_status ON hosoluutru_phieu_yeu_cau_documents(status);
CREATE INDEX IX_hosoluutru_phieu_yeu_cau_documents_created_on ON hosoluutru_phieu_yeu_cau_documents(created_on);
CREATE INDEX IX_hosoluutru_phieu_yeu_cau_documents_deleted_on ON hosoluutru_phieu_yeu_cau_documents(deleted_on);
CREATE INDEX idx_hosoluutru_phieu_yeu_cau_documents_dynamic_fields ON hosoluutru_phieu_yeu_cau_documents USING GIN (dynamic_fields);

COMMENT ON TABLE hosoluutru_phieu_yeu_cau_documents IS 'Phieu yeu cau khai thac (muon/tra) tai lieu luu tru (form Domino phieuyeucau) - dynamic_fields.hoso_id lien ket ho so muon';
COMMENT ON COLUMN hosoluutru_phieu_yeu_cau_documents.status IS '"0" cho giai quyet / "1" chap nhan / "2" nguoi muon tu thu hoi / "3" khong chap nhan';
