-- "Ban sao" identity (khong theo tenant) cua file_metadata - danh cho tep dinh kem gan voi DANH TINH
-- (AppUser/Organization...) thay vi gan voi tenant nghiep vu dang xu ly request. Xem javadoc
-- IdentityFileMetadata (td-domain, com.td.domain.sharedcore) cho boi canh day du bug da gap (anh chu
-- ky nguoi dung "bien mat" vi file_metadata cu la tenant-scoped).
--
-- Bang nay ton tai trong SCHEMA cua MOI tenant (Flyway ap dung dong bo migration nay cho tat ca) nhung
-- ung dung CHI BAO GIO doc/ghi qua ket noi "identityDataSource" (luon tro tenant mac dinh, xem
-- MultiPersistenceUnitJpaConfig#identityEntityManagerFactory) - giong het cach app_users/organizations
-- da hoat dong tu truoc, KHONG phai thiet ke moi.
CREATE TABLE identity_file_metadata (
    id                 UUID         PRIMARY KEY,
    document_id        UUID         NOT NULL,
    module             VARCHAR(100) NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    stored_filename    VARCHAR(255) NOT NULL UNIQUE,
    file_path          VARCHAR(500) NOT NULL,
    file_size          BIGINT       NOT NULL,
    content_type       VARCHAR(255),
    file_extension     VARCHAR(20),
    bucket_name        VARCHAR(100) NOT NULL,
    uploaded_by        UUID,
    uploaded_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description        TEXT,
    is_primary         BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_identity_file_metadata_document_id ON identity_file_metadata(document_id);
CREATE INDEX idx_identity_file_metadata_document_id_is_primary ON identity_file_metadata(document_id, is_primary);

COMMENT ON TABLE identity_file_metadata IS
    'Metadata tep dinh kem gan voi danh tinh (AppUser/Organization...), doc/ghi qua identityDataSource - KHONG theo tenant. Object vat ly dung chung 1 MinioService/bucket voi file_metadata (khong tenant-scoped).';
COMMENT ON COLUMN identity_file_metadata.module IS
    'Phan biet nhieu tinh nang dung chung bang nay (vd "user-signature") - chi dung tach thu muc MinIO, khong dung loc truy van (truy van luon theo document_id).';
