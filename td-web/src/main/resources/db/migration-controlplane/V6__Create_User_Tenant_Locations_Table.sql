-- BL-118 (bugfix NGHIEM TRONG, 20/08/2026): cache "username nay dang o database cua tenant nao"
-- (xem javadoc UserTenantLocation.java) - sua goc bug "Them moi bao thanh cong nhung luu khong dung
-- database cua don vi": Keycloak JWT khong mang claim tenant_id nen TenantResolver truoc day LUON
-- fallback ve tenant mac dinh cho MOI request.

CREATE TABLE user_tenant_locations (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    username            VARCHAR(100) NOT NULL,
    tenant_id           VARCHAR(100) NOT NULL,
    created_by          UUID,
    created_on          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by    UUID,
    last_modified_on    TIMESTAMP WITH TIME ZONE,
    deleted_on          TIMESTAMP WITH TIME ZONE,
    deleted_by          UUID
);

CREATE UNIQUE INDEX UX_user_tenant_locations_username ON user_tenant_locations(username) WHERE deleted_on IS NULL;
CREATE INDEX IX_user_tenant_locations_tenant_id        ON user_tenant_locations(tenant_id);

COMMENT ON TABLE user_tenant_locations IS 'Cache username -> tenant_id (database vat ly nao dang chua AppUser nay) - nguon su that van la AppUser vat ly, day chi la cache de tranh quet lai moi request';
