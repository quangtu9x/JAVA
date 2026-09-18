-- Vai trò người dùng: hoàn thiện CRUD cho app_roles/app_permissions (trước chỉ đọc, xem BL-108/
-- BL-110 migration-status.md) + mô hình hoá đúng 3 tầng nghiệp vụ Domino gốc (Quyền → Vai trò →
-- Nhóm vai trò gán cho user), thay vì gán thẳng user↔AppRole (UserRoleAssignment giữ nguyên, chưa
-- dùng tới — quyết định giữ đúng 3 tầng đã được xác nhận, không rút gọn còn 2 tầng).

-- "Vai trò" (app_roles) Domino gốc có thêm sort_order + field cấu hình menu (menu_top/menu_vbden/
-- menu_duthao/.../access_level) chưa có cột tường minh nào — tái dùng cơ chế Dynamic Data Engine
-- (BL-101/BL-109/BL-110) thay vì thêm 12+ cột text rời rạc.
ALTER TABLE app_roles
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX idx_app_roles_dynamic_fields ON app_roles USING GIN (dynamic_fields);

-- "Nhóm vai trò" (Domino form group_roles) — đơn vị THỰC SỰ được gán cho user (group_roles_id),
-- chứa 1 danh sách "vai trò" con (role_group_roles) + 1 danh sách "quyền" cấp trực tiếp
-- (role_group_permissions, độc lập với danh sách vai trò — đúng 2 cơ chế song song của Domino gốc).
CREATE TABLE role_groups (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name             VARCHAR(200) NOT NULL,
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    -- Domino field "limit": "1"=Toàn bộ (mặc định), "0"=Quản trị tổng.
    full_access      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID
);

CREATE TABLE role_group_roles (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_group_id    UUID         NOT NULL,
    role_id          UUID         NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID,
    CONSTRAINT FK_role_group_roles_group
        FOREIGN KEY (role_group_id) REFERENCES role_groups(id),
    CONSTRAINT FK_role_group_roles_role
        FOREIGN KEY (role_id) REFERENCES app_roles(id)
);

CREATE TABLE role_group_permissions (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_group_id    UUID         NOT NULL,
    permission_id    UUID         NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID,
    CONSTRAINT FK_role_group_permissions_group
        FOREIGN KEY (role_group_id) REFERENCES role_groups(id),
    CONSTRAINT FK_role_group_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES app_permissions(id)
);

CREATE UNIQUE INDEX UX_role_groups_name
    ON role_groups(name) WHERE deleted_on IS NULL;
CREATE INDEX IX_role_groups_is_active
    ON role_groups(is_active);
CREATE INDEX IX_role_groups_deleted_on
    ON role_groups(deleted_on);

CREATE UNIQUE INDEX UX_role_group_roles_pair
    ON role_group_roles(role_group_id, role_id) WHERE deleted_on IS NULL;
CREATE INDEX IX_role_group_roles_group_id
    ON role_group_roles(role_group_id);
CREATE INDEX IX_role_group_roles_role_id
    ON role_group_roles(role_id);
CREATE INDEX IX_role_group_roles_deleted_on
    ON role_group_roles(deleted_on);

CREATE UNIQUE INDEX UX_role_group_permissions_pair
    ON role_group_permissions(role_group_id, permission_id) WHERE deleted_on IS NULL;
CREATE INDEX IX_role_group_permissions_group_id
    ON role_group_permissions(role_group_id);
CREATE INDEX IX_role_group_permissions_permission_id
    ON role_group_permissions(permission_id);
CREATE INDEX IX_role_group_permissions_deleted_on
    ON role_group_permissions(deleted_on);

-- Domino: user chỉ thuộc ĐÚNG 1 "nhóm vai trò" tại 1 thời điểm (field group_roles_id số ít, không
-- phải danh sách) — dùng FK trực tiếp trên app_users thay vì bảng gán N:N (giống organization_id/
-- position_id đã có sẵn trên AppUser), không cần bảng trung gian.
ALTER TABLE app_users
    ADD COLUMN role_group_id UUID REFERENCES role_groups(id);

CREATE INDEX idx_app_users_role_group_id ON app_users(role_group_id);

-- Seed 34 "quyền" (permission) theo đúng catalog cứng hiện có ở FE
-- (ThanhHoaDEV/src/app/pages/qlvb/quyen/Page.jsx, biến Dataquyen) — giữ nguyên code/tên/nhóm để
-- không phá vỡ bất kỳ chỗ nào đang CheckRole() theo các mã này.
INSERT INTO app_permissions (code, name, module_key, is_active) VALUES
    ('QCapNhatVBDen', 'Cập nhật văn bản đến của cơ quan', 'Vào sổ văn bản', TRUE),
    ('QCapNhatVBDi', 'Cập nhật văn bản đi của cơ quan', 'Vào sổ văn bản', TRUE),
    ('QCapNhatVBDenDV', 'Cập nhật văn bản đến của đơn vị', 'Vào sổ văn bản', TRUE),
    ('QCapNhatVBDiDV', 'Cập nhật văn bản đi của đơn vị', 'Vào sổ văn bản', TRUE),
    ('QCapNhatVBDenPB', 'Quyền cập nhật văn bản đến phòng ban', 'Vào sổ văn bản', TRUE),
    ('QCapNhatVBDiPB', 'Quyền cập nhật văn bản đi phòng ban', 'Vào sổ văn bản', TRUE),
    ('QPhanCong', 'Phân công xử lý văn bản đến của cơ quan', 'Phân Xử lý văn bản', TRUE),
    ('QPhanPhoi', 'Phân phối văn bản đến của cơ quan', 'Phân Xử lý văn bản', TRUE),
    ('QPhanCongDV', 'Phân công xử lý văn bản đến của đơn vị', 'Phân Xử lý văn bản', TRUE),
    ('QPhanPhoiDV', 'Phân phối văn bản đến của đơn vị', 'Phân Xử lý văn bản', TRUE),
    ('QPhanCongPhong', 'Phân công xử lý văn bản đến phòng ban', 'Phân Xử lý văn bản', TRUE),
    ('CVQuanTri', 'Quản trị hệ thống', 'Quản trị', TRUE),
    ('CVQuanTriDV', 'Quản trị đơn vị', 'Quản trị', TRUE),
    ('QCapNhatTTDM', 'Quản trị danh mục hệ thống', 'Quản trị', TRUE),
    ('QKyVB', 'Quyền ký văn bản', 'Ký văn bản', TRUE),
    ('QInSo', 'In số văn bản', 'Khai thác thông tin tổng hợp', TRUE),
    ('QKTTTTH', 'Khai thác thông tin tổng hợp', 'Khai thác thông tin tổng hợp', TRUE),
    ('QTheoDoiVBNhiemVu', 'Theo dõi văn bản là nhiệm vụ', 'Khai thác thông tin tổng hợp', TRUE),
    ('QXemVBDi', 'Xem tất cả văn bản đi toàn cơ quan', 'Xem văn bản đi', TRUE),
    ('QXemVBDiDV', 'Xem tất cả văn bản đi của đơn vị', 'Xem văn bản đi', TRUE),
    ('QXemVBDiPB', 'Xem tất cả văn bản đi của phòng ban', 'Xem văn bản đi', TRUE),
    ('QXemVBDen', 'Xem tất cả văn bản đến toàn cơ quan', 'Xem văn bản đến', TRUE),
    ('QXemVBDenDV', 'Xem tất cả văn bản đến của đơn vị', 'Xem văn bản đến', TRUE),
    ('QXemVBDenPB', 'Xem tất cả văn bản đến của phòng ban', 'Xem văn bản đến', TRUE),
    ('QXemDuThao', 'Xem tất cả dự thảo toàn cơ quan', 'Xem dự thảo', TRUE),
    ('QXemDuThaoDV', 'Xem tất cả dự thảo của đơn vị', 'Xem dự thảo', TRUE),
    ('QXemDuThaoPB', 'Xem tất cả dự thảo của phòng ban', 'Xem dự thảo', TRUE),
    ('QXemHSCV', 'Xem tất cả HSCV của cơ quan', 'Xem hồ sơ', TRUE),
    ('QXemHSCVDV', 'Xem tất cả HSCV của đơn vị', 'Xem hồ sơ', TRUE),
    ('QXemHSCVPB', 'Xem tất cả HSCV của phòng ban', 'Xem hồ sơ', TRUE),
    ('QXemPhieuTrinh', 'Xem tất cả phiếu trình toàn cơ quan', 'Xem phiếu trình', TRUE),
    ('QXemPhieuTrinhDV', 'Xem tất cả phiếu trình của đơn vị', 'Xem phiếu trình', TRUE),
    ('QXemPhieuTrinhPB', 'Xem tất cả phiếu trình của phòng ban', 'Xem phiếu trình', TRUE),
    ('QXuLyVB', 'Quyền xử lý văn bản', 'Xử lý văn bản', TRUE);
