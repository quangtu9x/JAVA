-- Add optional form field for categories so clients can persist UI/source context.

ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS form VARCHAR(100);

CREATE INDEX IF NOT EXISTS IX_categories_form
    ON categories(form);

COMMENT ON COLUMN categories.form IS 'Truong phan loai/bieu mau danh muc, tuy chon';