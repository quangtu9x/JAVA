-- BL-117 (thay thế BL-115): database "control-plane" — 1 database Postgres RIÊNG BIỆT HOÀN TOÀN,
-- KHÔNG thuộc bất kỳ tenant nào, chứa các bảng thật sự GLOBAL dùng chung mọi tenant. Trước đây
-- (BL-115) đây là schema `shared` bên trong CÙNG 1 database vật lý với tenant (search_path 2 tầng) —
-- từ BL-117, multi-tenant đổi hẳn sang database-per-tenant thật nên bảng này cần tách sang database
-- riêng biệt hoàn toàn (search_path không còn ý nghĩa khi mỗi tenant là 1 database khác nhau). Nội
-- dung DDL dưới đây COPY NGUYÊN VẸN từ `db/migration-shared/V1__Create_Categories_Table.sql` (đã
-- retired, xem docs/architecture/06-database-per-tenant-multi-tenant.md) — không đổi 1 dòng nào để
-- giữ đúng cấu trúc bảng đã áp dụng thật.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Bảng danh mục hỗ trợ cây phân cấp (self-referencing hierarchy) — dùng chung: "Cơ quan ngoài tỉnh"
-- (BL-113, form=external_unit_level/external_unit) + 8+ danh mục dùng chung phẳng (BL-114, vd
-- form=document_type/position/method/holidays/sample...).
CREATE TABLE categories (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(100) NOT NULL,
    name             VARCHAR(300) NOT NULL,
    description      TEXT,
    parent_id        UUID         REFERENCES categories(id),
    level            INT          NOT NULL DEFAULT 0,
    full_path        TEXT         NOT NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    form             VARCHAR(100),
    dynamic_fields   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID
);

CREATE UNIQUE INDEX UX_categories_code     ON categories(code) WHERE deleted_on IS NULL;
CREATE INDEX IX_categories_parent_id       ON categories(parent_id);
CREATE INDEX IX_categories_level           ON categories(level);
CREATE INDEX IX_categories_is_active       ON categories(is_active);
CREATE INDEX IX_categories_sort_order      ON categories(sort_order);
CREATE INDEX IX_categories_deleted_on      ON categories(deleted_on);
CREATE INDEX IX_categories_created_on      ON categories(created_on);
CREATE INDEX IX_categories_last_modified   ON categories(last_modified_on);
CREATE INDEX IX_categories_form            ON categories(form);
CREATE INDEX idx_categories_dynamic_fields ON categories USING GIN (dynamic_fields);

COMMENT ON TABLE  categories                IS 'Danh muc dung chung TOAN HE THONG (global, database control-plane rieng) - khong nhan ban theo tenant';
COMMENT ON COLUMN categories.code           IS 'Ma danh muc, duy nhat, duoc chuan hoa HOA';
COMMENT ON COLUMN categories.name           IS 'Ten hien thi';
COMMENT ON COLUMN categories.parent_id      IS 'ID danh muc cha (null = goc)';
COMMENT ON COLUMN categories.level          IS 'Do sau trong cay (0 = goc)';
COMMENT ON COLUMN categories.full_path      IS 'Duong dan day du: Goc / Cha / Con';
COMMENT ON COLUMN categories.sort_order     IS 'Thu tu hien thi trong cung cap';
COMMENT ON COLUMN categories.is_active      IS 'Trang thai kich hoat';
COMMENT ON COLUMN categories.form           IS 'Truong phan loai/bieu mau danh muc (vd external_unit_level, document_type...)';
COMMENT ON COLUMN categories.dynamic_fields IS 'Field kieu Domino cu chua co cot tuong minh (JSONB, Dynamic Data Engine)';
