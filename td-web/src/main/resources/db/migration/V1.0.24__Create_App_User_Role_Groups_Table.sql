-- Bổ sung 19/08/2026 (bugfix — sửa lại thiết kế BL-111): FK đơn `app_users.role_group_id`
-- (V1.0.18) SAI giả định "Domino chỉ gán 1 nhóm vai trò/user" — xác nhận lại qua phản hồi trực tiếp
-- "1 user = nhiều role group". Thay bằng bảng nối N:N `app_user_role_groups`, cùng convention
-- role_group_roles/role_group_permissions.

CREATE TABLE app_user_role_groups (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    app_user_id      UUID         NOT NULL,
    role_group_id    UUID         NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID,
    CONSTRAINT FK_app_user_role_groups_user
        FOREIGN KEY (app_user_id) REFERENCES app_users(id),
    CONSTRAINT FK_app_user_role_groups_group
        FOREIGN KEY (role_group_id) REFERENCES role_groups(id)
);

CREATE UNIQUE INDEX UX_app_user_role_groups_pair
    ON app_user_role_groups(app_user_id, role_group_id) WHERE deleted_on IS NULL;
CREATE INDEX IX_app_user_role_groups_user_id
    ON app_user_role_groups(app_user_id);
CREATE INDEX IX_app_user_role_groups_group_id
    ON app_user_role_groups(role_group_id);
CREATE INDEX IX_app_user_role_groups_deleted_on
    ON app_user_role_groups(deleted_on);

-- Chuyển dữ liệu gán nhóm hiện có (FK đơn cũ) sang bảng nối mới TRƯỚC khi xoá cột cũ — không mất
-- dữ liệu đã gán từ trước (nếu môi trường nào đã có).
INSERT INTO app_user_role_groups (app_user_id, role_group_id, is_active)
SELECT id, role_group_id, TRUE
FROM app_users
WHERE role_group_id IS NOT NULL;

DROP INDEX IF EXISTS idx_app_users_role_group_id;
ALTER TABLE app_users DROP COLUMN role_group_id;
