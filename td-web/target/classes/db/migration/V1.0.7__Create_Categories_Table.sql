-- Bảng danh mục hỗ trợ cây phân cấp (self-referencing hierarchy)
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
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID
);

-- Code phải duy nhất trong các bản ghi chưa bị xóa
CREATE UNIQUE INDEX UX_categories_code    ON categories(code) WHERE deleted_on IS NULL;
CREATE INDEX IX_categories_parent_id      ON categories(parent_id);
CREATE INDEX IX_categories_level          ON categories(level);
CREATE INDEX IX_categories_is_active      ON categories(is_active);
CREATE INDEX IX_categories_sort_order     ON categories(sort_order);
CREATE INDEX IX_categories_deleted_on     ON categories(deleted_on);
CREATE INDEX IX_categories_created_on     ON categories(created_on);
CREATE INDEX IX_categories_last_modified  ON categories(last_modified_on);

COMMENT ON TABLE  categories                IS 'Danh mục phân cấp dùng chung toàn hệ thống';
COMMENT ON COLUMN categories.code           IS 'Mã danh mục, duy nhất, được chuẩn hóa HOA';
COMMENT ON COLUMN categories.name           IS 'Tên hiển thị';
COMMENT ON COLUMN categories.parent_id      IS 'ID danh mục cha (null = gốc)';
COMMENT ON COLUMN categories.level          IS 'Độ sâu trong cây (0 = gốc)';
COMMENT ON COLUMN categories.full_path      IS 'Đường dẫn đầy đủ: Gốc / Cha / Con';
COMMENT ON COLUMN categories.sort_order     IS 'Thứ tự hiển thị trong cùng cấp';
COMMENT ON COLUMN categories.is_active      IS 'Trạng thái kích hoạt';
