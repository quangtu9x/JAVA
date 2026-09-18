-- BL-108: User Management — Core CRUD
-- Mở rộng app_users với các field hồ sơ người dùng còn thiếu (đối chiếu
-- docs/field-mapping/03-user-field-mapping.md), chỉ thêm mới — không đổi field hiện có.
--
-- Cố tình KHÔNG thêm agency/agency_id/unit/unit_id/department/department_id/parentids/parents
-- (dữ liệu tổ chức) như bản Domino gốc: các field đó suy ra được từ organization_id (đã có sẵn)
-- bằng cách đi lên organizations.parent_id, tránh lặp lại nghiệp vụ cascade-update nặng nề
-- ("DoiTenToChuc_TrongNguoiDung") mà Domino phải làm mỗi khi đổi tên 1 node cơ cấu tổ chức.

ALTER TABLE app_users
    ADD COLUMN level          INTEGER,
    ADD COLUMN sort_order     INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN phone_number   VARCHAR(30),
    ADD COLUMN gender         VARCHAR(20),
    ADD COLUMN birthday_date  DATE,
    ADD COLUMN address        VARCHAR(500),
    ADD COLUMN user_id_no     VARCHAR(30),
    ADD COLUMN cap_lanh_dao   VARCHAR(30),
    ADD COLUMN identifier     VARCHAR(100),
    ADD COLUMN is_sim         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN parent_user_id UUID,
    ADD CONSTRAINT FK_app_users_parent_user
        FOREIGN KEY (parent_user_id) REFERENCES app_users (id);

-- Cho phép tạo hồ sơ người dùng trước khi có tài khoản Keycloak thật (gắn keycloak_subject sau,
-- ở 1 đợt tích hợp riêng) — nới lỏng ràng buộc, không ảnh hưởng dữ liệu hiện có.
ALTER TABLE app_users
    ALTER COLUMN keycloak_subject DROP NOT NULL;
