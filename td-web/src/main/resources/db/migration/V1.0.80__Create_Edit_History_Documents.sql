-- 30/10/2026 (cutover qlvb/add — "No static resource qlvb/add" trên td-web): vết hiệu chỉnh DÙNG CHUNG
-- cho mọi màn còn gọi LogVariable.getChangedFieldDetails mặc định (danh mục, người dùng, đơn vị, nhóm vai
-- trò, sổ văn bản, hosocv, vbcddh, ...) — trước đây tự POST document form="log" lên Domino qlvb/add và
-- VetHieuChinh.jsx đọc lại qua qlvb/danhmuc/GetRes, cả 2 chiều rơi 404 trên tenant Java.
-- Cùng khuôn vbden_edit_history_documents (V1.0.60) nhưng parent_id là chuỗi (id bản ghi cha có thể
-- nằm ở bảng/DB khác nhau: tenant, control-plane) + database_type để biết nguồn. Append-only.
-- duthao/vbdi/vbden vẫn dùng bảng riêng của module.
CREATE TABLE edit_history_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    parent_id VARCHAR(100) NOT NULL,
    database_type VARCHAR(50),
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

CREATE INDEX IX_edit_history_documents_parent_id ON edit_history_documents(parent_id);
CREATE INDEX IX_edit_history_documents_created_on ON edit_history_documents(created_on);

COMMENT ON TABLE edit_history_documents IS 'Vết hiệu chỉnh dùng chung (port LogVariable.getChangedFieldDetails -> qlvb/add Domino) cho các màn không có bảng vết riêng - mỗi lần Lưu có field đổi ghi 1 dòng {field:{oldvalue,newvalue}}';
COMMENT ON COLUMN edit_history_documents.parent_id IS 'Id bản ghi bị hiệu chỉnh (chuỗi - UUID Java hoặc UNID cũ)';
COMMENT ON COLUMN edit_history_documents.database_type IS 'databasetype FE gửi (qlvb/tudien/hosocv/vbcddh/...) - chỉ để tra cứu, không dùng lọc khi đọc';
