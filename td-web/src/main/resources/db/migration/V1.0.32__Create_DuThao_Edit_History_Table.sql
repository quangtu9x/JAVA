-- Yêu cầu người dùng 27/08/2026 ("onFinish có gọi api qlvb/add?databasetype=duthao (cũ) để lưu vết
-- hiệu chỉnh của dự thảo -> port sang java ... tạo bảng lưu vết riêng"): bảng vật lý ĐỘC LẬP thứ 3
-- của cụm dự thảo, tách hẳn khỏi {@code duthao_documents} và {@code draft_process_documents}:
--   - V1.0.28 tạo duthao_documents (document chính document_type='duthao')
--   - V1.0.30 tách draft_process_documents (vết xử lý workflow / ý kiến phối hợp, DT-007)
--   - V1.0.31 NÀY tạo duthao_edit_history_documents: VẾT HIỆU CHỈNH FIELD do người dùng sửa form
--     (port LogVariable.getChangedFieldDetails tự POST qlvb/add phía Domino).
-- Mỗi dòng = 1 lần người dùng bấm Lưu có field đổi: {field: {oldvalue,newvalue}} JSONB trong
-- edit_obj — đúng mô hình document con Domino (form="log", res=<id cha>, edit_obj, creator,
-- created_date); bên Java đổi UNID-con thành cột typed parent_id. Append-only (không sửa/xoá),
-- trait audit trail BL-103.
CREATE TABLE duthao_edit_history_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    parent_id UUID NOT NULL,
    form_edit VARCHAR(100),
    edit_obj JSONB NOT NULL DEFAULT '{}'::jsonb,
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

CREATE INDEX IX_duthao_edit_history_parent_id ON duthao_edit_history_documents(parent_id);
CREATE INDEX IX_duthao_edit_history_created_on ON duthao_edit_history_documents(created_on);
CREATE INDEX IX_duthao_edit_history_deleted_on ON duthao_edit_history_documents(deleted_on);
CREATE INDEX idx_duthao_edit_history_edit_obj ON duthao_edit_history_documents USING GIN (edit_obj);

COMMENT ON TABLE duthao_edit_history_documents IS 'Bảng vật lý ĐỘC LẬP cho vết hiệu chỉnh dự thảo (port LogVariable.getChangedFieldDetails -> qlvb/add Domino), yêu cầu người dùng 27/08/2026 - mỗi lần Lưu có field đổi ghi 1 dòng {field:{oldvalue,newvalue}} trỏ parent_id về dự thảo cha';
COMMENT ON COLUMN duthao_edit_history_documents.edit_obj IS 'Map field-> {oldvalue,newvalue} của lần hiệu chỉnh - đúng khuôn VetHieuChinh.jsx đang render';
COMMENT ON COLUMN duthao_edit_history_documents.creator_name IS 'Tên hiển thị người hiệu chỉnh (tương đương item creator Domino) - TUỲ CHỌN vì chỉ mang giá trị trình bày; danh tính thật là user_creator + cột audit created_by';
