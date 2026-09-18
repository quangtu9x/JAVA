-- Bổ sung khả năng lưu field động (JSONB) cho AppUser (Người dùng) và Category (Danh mục), tái
-- dùng đúng cơ chế Dynamic Data Engine đã xây cho `documents` (BL-101) và vừa nối cho `organizations`
-- (BL-109). Rà soát toàn bộ entity còn lại trong hệ thống (NumberingCounter, NumberingConfig,
-- DocumentLog, UserRoleAssignment, RolePermission, UserDataScope, AppPermission, AppRole) xác nhận
-- đây là 2 entity CÒN LẠI thực sự cần: có Create/Update UseCase qua API (user-editable) và là đối
-- tượng nghiệp vụ hợp lý để FE tự thêm field tuỳ biến — các entity còn lại là bảng hệ thống/join/
-- audit log, cố tình không thêm.
ALTER TABLE app_users
    ADD COLUMN dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX idx_app_users_dynamic_fields ON app_users USING GIN (dynamic_fields);

ALTER TABLE categories
    ADD COLUMN dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX idx_categories_dynamic_fields ON categories USING GIN (dynamic_fields);
