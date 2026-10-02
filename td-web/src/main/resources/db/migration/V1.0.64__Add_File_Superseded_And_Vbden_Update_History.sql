-- Yeu cau nguoi dung 24/09/2026 (tiep bugfix "Cap nhat VB" - van ban den gui lai co bo sung tep):
-- "tam tep cu chua xoa di, chi hien thi tep moi cap nhat; luu lai vet ngay gio cap nhat, co lien ket
-- toi van ban da cap nhat, the hien duoc nhung tep cu da cap nhat nhung KHONG cho phep tai ve, chi
-- hien thi ten".
--
-- (1) file_metadata.superseded_at/superseded_by: tep "DA DUOC THAY THE" boi 1 lan cap nhat - KHONG xoa
--     dong/KHONG xoa object MinIO (giu nguyen du lieu goc), chi an khoi MOI truy van qua JPA
--     (FileMetadataEntity co @SQLRestriction("superseded_at IS NULL")) => khong liet ke, khong tai,
--     khong xem, khong sua/xoa/ky duoc nua. Ten tep cu chi con hien thi qua snapshot trong
--     vbden_update_history_documents.update_obj (khong kem duong dan tai).
--     NULL = tep hien hanh (moi dong hien co giu nguyen hanh vi, khong can backfill).
ALTER TABLE file_metadata ADD COLUMN superseded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE file_metadata ADD COLUMN superseded_by UUID;

CREATE INDEX IX_file_metadata_document_active ON file_metadata(document_id) WHERE superseded_at IS NULL;

COMMENT ON COLUMN file_metadata.superseded_at IS 'Thoi diem tep bi thay the boi 1 lan cap nhat van ban (NULL = tep hien hanh). Tep da thay the bi an khoi moi truy van JPA (khong liet ke/tai/xem), KHONG bi xoa vat ly.';
COMMENT ON COLUMN file_metadata.superseded_by IS 'Id ban ghi lich su cap nhat da thay the tep nay (vd vbden_update_history_documents.id).';

-- (2) vbden_update_history_documents: LICH SU CAP NHAT van ban den (moi lan "Cap nhat VB" gop noi dung
--     + tep tu ban gui lai vao van ban da vao so = 1 dong) - mirror khuon vbden_edit_history_documents
--     (V1.0.60): append-only, update_obj JSONB (quy uoc JSONB cho bang moi, ARCHITECTURE.md muc 6).
CREATE TABLE vbden_update_history_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    parent_id UUID NOT NULL,
    source_document_id UUID,
    action VARCHAR(50) NOT NULL,
    update_obj JSONB NOT NULL DEFAULT '{}'::jsonb,
    creator_name VARCHAR(300),
    user_creator VARCHAR(100),
    version_no BIGINT NOT NULL DEFAULT 1,
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

CREATE INDEX IX_vbden_update_history_parent_id ON vbden_update_history_documents(parent_id);
CREATE INDEX IX_vbden_update_history_source_document_id ON vbden_update_history_documents(source_document_id);
CREATE INDEX IX_vbden_update_history_created_on ON vbden_update_history_documents(created_on);
CREATE INDEX IX_vbden_update_history_deleted_on ON vbden_update_history_documents(deleted_on);

COMMENT ON TABLE vbden_update_history_documents IS 'Lich su cap nhat van ban den tu ban gui lai/thay the (nut Cap nhat VB - CapNhatVanBanThayTheThuHoiUseCase): moi dong = 1 lan cap nhat vao van ban da vao so (parent_id), tu van ban nguon (source_document_id). Append-only.';
COMMENT ON COLUMN vbden_update_history_documents.action IS 'capnhat (gui lai, bussiness_doc_type=2) | thaythe_thuhoi (bussiness_doc_type 1/4, van ban cu dang xu ly do)';
COMMENT ON COLUMN vbden_update_history_documents.update_obj IS 'Snapshot {source:{...}, source_files:[{file_id,name,size}], new_files:[{name,size}], kept_files:[{name,size}], superseded_files:[{file_id,name,size}], changed_fields:{field:{oldvalue,newvalue}}}';
