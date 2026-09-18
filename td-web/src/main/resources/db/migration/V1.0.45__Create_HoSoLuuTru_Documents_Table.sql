-- Port module "Hồ sơ lưu trữ" (hosoluutru) - docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md
-- muc 8.1. Bang vat ly DOC LAP cho ho so luu tru chinh (form Domino hoso/hoso_chuaduyet), dung khuon
-- V1.0.43 (hosocv_documents) - kien truc BL-101 (Dynamic Data Engine, field nghiep vu bien thien trong
-- dynamic_fields JSONB) - xem entity HoSoLuuTruDocument (com.td.domain.hosoluutru) + repository
-- HoSoLuuTruDocumentRepository/PostgresHoSoLuuTruDocumentRepository.
CREATE TABLE hosoluutru_documents (
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

CREATE INDEX IX_hosoluutru_documents_title ON hosoluutru_documents(title);
CREATE INDEX IX_hosoluutru_documents_document_type ON hosoluutru_documents(document_type);
CREATE INDEX IX_hosoluutru_documents_status ON hosoluutru_documents(status);
CREATE INDEX IX_hosoluutru_documents_created_on ON hosoluutru_documents(created_on);
CREATE INDEX IX_hosoluutru_documents_last_modified_on ON hosoluutru_documents(last_modified_on);
CREATE INDEX IX_hosoluutru_documents_deleted_on ON hosoluutru_documents(deleted_on);
CREATE INDEX idx_hosoluutru_documents_dynamic_fields ON hosoluutru_documents USING GIN (dynamic_fields);

COMMENT ON TABLE hosoluutru_documents IS 'Ho so luu tru chinh (form Domino hoso/hoso_chuaduyet) - bang vat ly DOC LAP, tao khi hosocv "Chuyen luu tru" hoac CRUD truc tiep trong hosoluutru';
COMMENT ON COLUMN hosoluutru_documents.status IS 'Luu truc tiep gia tri form Domino rut gon: hoso_chuaduyet (cho tiep nhan) / hoso (da tiep nhan)';
COMMENT ON COLUMN hosoluutru_documents.dynamic_fields IS 'Field dong (key-value) suy luan kieu tu hau to ten field (_date, _number, _obj, _aut, _rdr, _tlist), tuong duong NotesDocument Item dong phia Domino';
