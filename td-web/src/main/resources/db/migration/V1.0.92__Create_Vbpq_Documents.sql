-- 30/10/2026 (port module "Văn bản pháp quy" - vanbanphapquy, Domino vanbanphapquy.nsf): FE gọi
-- qlvb/view/query|search|get|add|delete?databasetype=vanbanphapquy + qlvb/chiasetep/xoatailieu đều 404 trên Java.
-- coquan_id/coquan_identifier là cơ quan sở hữu (lọc danh sách theo cây cơ quan + chặn sửa khác cơ quan) nên để
-- cột typed; field biến thiên (sokyhieu, ngayky_date, coquanbanhanh, nguoiky, chucvu, loaivanban, linhvuc,
-- trichyeu, ghichu, capbanhanh*...) trong dynamic_fields. Tệp: file_metadata. Xoá = xoá mềm.
CREATE TABLE vbpq_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(300) NOT NULL DEFAULT '',
    coquan_id UUID,
    coquan_identifier VARCHAR(100),
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

CREATE INDEX IX_vbpq_documents_coquan_identifier ON vbpq_documents(UPPER(coquan_identifier));
CREATE INDEX IX_vbpq_documents_created_on ON vbpq_documents(created_on);
CREATE INDEX IX_vbpq_documents_deleted_on ON vbpq_documents(deleted_on);

COMMENT ON TABLE vbpq_documents IS 'Văn bản pháp quy (Domino vanbanphapquy.nsf, form vanbanphapquy)';
COMMENT ON COLUMN vbpq_documents.coquan_id IS 'Cơ quan sở hữu (agency) - server tự gán theo người tạo';
COMMENT ON COLUMN vbpq_documents.dynamic_fields IS 'sokyhieu, ngayky_date, coquanbanhanh, nguoiky, chucvu, loaivanban, linhvuc, trichyeu, ghichu, capbanhanh(_id/_identifier), coquan...';
