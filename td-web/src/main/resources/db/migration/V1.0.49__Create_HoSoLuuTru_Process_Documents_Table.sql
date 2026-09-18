-- Port module "Hồ sơ lưu trữ" (hosoluutru) - docs/analysis/16-...md muc 8.1. Bang vet xu ly NOI BO
-- hosoluutru (hien chi dung cho LTLS, process_type=chuyenltls) - xem entity HoSoLuuTruProcessDocument.
-- LUU Y: vet "tiep nhan"/"tu choi" ho so (quatrinhchuyenluutru) ghi vao hosocv_process_documents DA CO
-- (process_type=chuyenluutru), KHONG ghi vao bang nay - xem javadoc entity.
CREATE TABLE hosoluutru_process_documents (
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

CREATE INDEX IX_hosoluutru_process_documents_created_on ON hosoluutru_process_documents(created_on);
CREATE INDEX idx_hosoluutru_process_documents_dynamic_fields ON hosoluutru_process_documents USING GIN (dynamic_fields);

COMMENT ON TABLE hosoluutru_process_documents IS 'Vet xu ly noi bo hosoluutru (hien chi LTLS, process_type=chuyenltls) - append-only';
