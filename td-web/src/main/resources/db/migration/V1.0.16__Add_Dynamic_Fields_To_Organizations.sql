-- Bổ sung khả năng lưu field động (JSONB) cho Organization/Cây cơ cấu tổ chức, tái dùng đúng cơ
-- chế Dynamic Data Engine đã xây cho `documents` (BL-101: FieldTypeInferrer/DynamicDocumentFieldsService)
-- thay vì phải thêm migration cột mới mỗi lần cần 1 field FE tự định nghĩa (vd mailin, clerical,
-- identifier_other, cfg_* kiểu Domino cũ chưa có chỗ lưu — xem BL-108).
ALTER TABLE organizations
    ADD COLUMN dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX idx_organizations_dynamic_fields ON organizations USING GIN (dynamic_fields);
