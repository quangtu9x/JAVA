-- BL-117: registry tenant thật (database-per-tenant) — thay thế hoàn toàn YAML tĩnh
-- `app.multitenancy.tenants` của BL-303 cũ. Đọc động lúc khởi động (TenantDataSourceConfig) và qua
-- API PlatformTenantsController lúc runtime.
--
-- LƯU Ý: `encrypted_password` là mật khẩu Postgres của TENANT đó (không phải mật khẩu người dùng
-- cuối), mã hoá 2 CHIỀU bằng TenantCredentialCipher (Encryptors.text — spring-security-crypto) chứ
-- KHÔNG PHẢI hash 1 chiều — bắt buộc vì app cần giải mã lại để mở JDBC connection thật.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE tenants (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           VARCHAR(100) NOT NULL,
    display_name        VARCHAR(300) NOT NULL,
    host                VARCHAR(255) NOT NULL,
    port                INT          NOT NULL,
    database_name       VARCHAR(100) NOT NULL,
    username            VARCHAR(100) NOT NULL,
    encrypted_password  TEXT         NOT NULL,
    driver_class_name   VARCHAR(200) NOT NULL DEFAULT 'org.postgresql.Driver',
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by          UUID,
    created_on          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by    UUID,
    last_modified_on    TIMESTAMP WITH TIME ZONE,
    deleted_on          TIMESTAMP WITH TIME ZONE,
    deleted_by          UUID
);

CREATE UNIQUE INDEX UX_tenants_tenant_id ON tenants(tenant_id) WHERE deleted_on IS NULL;
CREATE INDEX IX_tenants_active          ON tenants(active);
CREATE INDEX IX_tenants_deleted_on      ON tenants(deleted_on);

COMMENT ON TABLE  tenants                    IS 'Registry tenant thuc (database-per-tenant, BL-117) - moi row = 1 database Postgres vat ly rieng';
COMMENT ON COLUMN tenants.tenant_id          IS 'Ma tenant, duy nhat, khop claim JWT tenant_id (vd H05) - BAT BIEN sau khi tao';
COMMENT ON COLUMN tenants.database_name      IS 'Ten database Postgres cua tenant nay - BAT BIEN sau khi tao (doi = phai tao tenant moi)';
COMMENT ON COLUMN tenants.encrypted_password IS 'Mat khau Postgres cua tenant, ma hoa 2 chieu (TenantCredentialCipher) - KHONG phai hash 1 chieu';
COMMENT ON COLUMN tenants.active             IS 'false = tenant bi vo hieu hoa - request bi TU CHOI (khong fallback ve tenant mac dinh, tranh ro ri du lieu cheo tenant)';
