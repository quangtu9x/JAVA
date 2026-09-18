-- BL-118 (19/08/2026): tệp đính kèm cho "danh mục dùng chung" (Category) — dùng cho Banner/Footer/
-- Hướng dẫn sử dụng (form 'banner'/'footer'/'help'), chuyển từ Domino cũ (qlvb/danhmuc/LuuDanhMuc +
-- TDAttachment) sang Java. Bảng RIÊNG với file_metadata (tenant-scoped, phục vụ Document) vì nội
-- dung Category dùng CHUNG cho mọi tenant — xem javadoc CategoryFileMetadata.java. Không có FK tới
-- categories (control-plane không dùng FK giữa các bảng nghiệp vụ khác nhau, cùng quy ước
-- db_servers/tenants).

CREATE TABLE category_file_metadata (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    category_id         UUID         NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    stored_filename      VARCHAR(255) NOT NULL,
    file_path           VARCHAR(500) NOT NULL,
    file_size           BIGINT       NOT NULL,
    content_type        VARCHAR(255),
    file_extension      VARCHAR(20),
    bucket_name         VARCHAR(100) NOT NULL,
    description         TEXT,
    created_by          UUID,
    created_on          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by    UUID,
    last_modified_on    TIMESTAMP WITH TIME ZONE,
    deleted_on          TIMESTAMP WITH TIME ZONE,
    deleted_by          UUID
);

CREATE UNIQUE INDEX UX_category_file_metadata_stored_filename ON category_file_metadata(stored_filename);
CREATE INDEX IX_category_file_metadata_category_id            ON category_file_metadata(category_id);

COMMENT ON TABLE category_file_metadata IS 'Metadata tep dinh kem cho Category dung chung (Banner/Footer/Huong dan su dung) - noi dung vat ly luu MinIO (bucket dung chung), khong tenant-scoped';
