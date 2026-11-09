-- 30/10/2026 (port module "Quản lý tài liệu" - chiasetep, Domino chiasetep.nsf): FE trỏ base URL sang Java
-- nên mọi qlvb/chiasetep/*, qlvb/add|get|view/query?databasetype=chiasetep đều 404 "No static resource".
-- 1 bảng cho cả thư mục (form='thumuc') và tài liệu (form='tailieu'), cây theo parent_id ('#' = thư mục gốc,
-- còn lại = id thư mục cha) - đúng mô hình Domino (vwThu_Muc_By_ParentID / vwTai_Lieu_By_ParentID).
-- Cột typed cho các khoá lọc thường xuyên (form/parent_id/user_creator); phần biến thiên (ten, cap,
-- nguoichinhsua(hide/_obj), nguoixem(hide/_obj), sokyhieu, ngayky_date, coquanbanhanh, trichyeu, ghichu,
-- idvbden/idvbdi/idphieutrinh...) nằm trong dynamic_fields (ARCHITECTURE.md mục 6). Tệp: file_metadata
-- (api/v1/documents/{id}/files). Xoá = xoá mềm.
CREATE TABLE chiasetep_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    form VARCHAR(50) NOT NULL,
    parent_id VARCHAR(100),
    title VARCHAR(300) NOT NULL DEFAULT '',
    user_creator VARCHAR(100),
    dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    version_no BIGINT NOT NULL DEFAULT 1,
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

CREATE INDEX IX_chiasetep_documents_form_parent ON chiasetep_documents(form, parent_id);
CREATE INDEX IX_chiasetep_documents_user_creator ON chiasetep_documents(LOWER(user_creator));
CREATE INDEX IX_chiasetep_documents_created_on ON chiasetep_documents(created_on);
CREATE INDEX IX_chiasetep_documents_deleted_on ON chiasetep_documents(deleted_on);

COMMENT ON TABLE chiasetep_documents IS 'Quản lý tài liệu / chia sẻ tệp (Domino chiasetep.nsf): thư mục (form=thumuc) + tài liệu (form=tailieu)';
COMMENT ON COLUMN chiasetep_documents.parent_id IS '# = thư mục gốc; id thư mục cha (thư mục con / tài liệu)';
COMMENT ON COLUMN chiasetep_documents.user_creator IS 'Tài khoản chủ sở hữu (người tạo)';
COMMENT ON COLUMN chiasetep_documents.dynamic_fields IS 'ten, cap, nguoichinhsua/nguoichinhsuahide/nguoichinhsua_obj, nguoixem/nguoixemhide/nguoixem_obj, sokyhieu, ngayky_date, coquanbanhanh, trichyeu, ghichu, idvbden/idvbdi/idphieutrinh...';
