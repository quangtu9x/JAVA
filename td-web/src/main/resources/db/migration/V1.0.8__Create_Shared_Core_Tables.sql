-- Shared Core schema: organizations, users, roles, permissions and data scopes.

CREATE TABLE organizations (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(100) NOT NULL,
    name             VARCHAR(300) NOT NULL,
    description      TEXT,
    parent_id        UUID         REFERENCES organizations(id),
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

CREATE TABLE app_users (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    keycloak_subject VARCHAR(200) NOT NULL,
    username         VARCHAR(100) NOT NULL,
    full_name        VARCHAR(300) NOT NULL,
    email            VARCHAR(200),
    organization_id  UUID,
    position_id      UUID,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID,
    CONSTRAINT FK_app_users_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE TABLE app_roles (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(100) NOT NULL,
    name             VARCHAR(200) NOT NULL,
    description      TEXT,
    is_system_role   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID
);

CREATE TABLE app_permissions (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(100) NOT NULL,
    name             VARCHAR(200) NOT NULL,
    module_key       VARCHAR(100) NOT NULL,
    description      TEXT,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID
);

CREATE TABLE app_role_permissions (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id          UUID         NOT NULL,
    permission_id    UUID         NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID,
    CONSTRAINT FK_app_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES app_roles(id),
    CONSTRAINT FK_app_role_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES app_permissions(id)
);

CREATE TABLE app_user_roles (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL,
    role_id          UUID         NOT NULL,
    organization_id  UUID,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID,
    CONSTRAINT FK_app_user_roles_user
        FOREIGN KEY (user_id) REFERENCES app_users(id),
    CONSTRAINT FK_app_user_roles_role
        FOREIGN KEY (role_id) REFERENCES app_roles(id),
    CONSTRAINT FK_app_user_roles_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE TABLE app_user_data_scopes (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL,
    scope_module     VARCHAR(100) NOT NULL,
    scope_type       VARCHAR(50)  NOT NULL,
    scope_org_id     UUID,
    scope_value      VARCHAR(300),
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by       UUID,
    created_on       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on       TIMESTAMP WITH TIME ZONE,
    deleted_by       UUID,
    CONSTRAINT FK_app_user_data_scopes_user
        FOREIGN KEY (user_id) REFERENCES app_users(id),
    CONSTRAINT FK_app_user_data_scopes_org
        FOREIGN KEY (scope_org_id) REFERENCES organizations(id)
);

CREATE UNIQUE INDEX UX_organizations_code
    ON organizations(code) WHERE deleted_on IS NULL;
CREATE INDEX IX_organizations_parent_id
    ON organizations(parent_id);
CREATE INDEX IX_organizations_level
    ON organizations(level);
CREATE INDEX IX_organizations_is_active
    ON organizations(is_active);
CREATE INDEX IX_organizations_deleted_on
    ON organizations(deleted_on);

CREATE UNIQUE INDEX UX_app_users_keycloak_subject
    ON app_users(keycloak_subject) WHERE deleted_on IS NULL;
CREATE UNIQUE INDEX UX_app_users_username
    ON app_users(username) WHERE deleted_on IS NULL;
CREATE INDEX IX_app_users_org_id
    ON app_users(organization_id);
CREATE INDEX IX_app_users_is_active
    ON app_users(is_active);
CREATE INDEX IX_app_users_deleted_on
    ON app_users(deleted_on);

CREATE UNIQUE INDEX UX_app_roles_code
    ON app_roles(code) WHERE deleted_on IS NULL;
CREATE INDEX IX_app_roles_is_active
    ON app_roles(is_active);
CREATE INDEX IX_app_roles_deleted_on
    ON app_roles(deleted_on);

CREATE UNIQUE INDEX UX_app_permissions_code
    ON app_permissions(code) WHERE deleted_on IS NULL;
CREATE INDEX IX_app_permissions_module_key
    ON app_permissions(module_key);
CREATE INDEX IX_app_permissions_is_active
    ON app_permissions(is_active);
CREATE INDEX IX_app_permissions_deleted_on
    ON app_permissions(deleted_on);

CREATE UNIQUE INDEX UX_app_role_permissions_pair
    ON app_role_permissions(role_id, permission_id) WHERE deleted_on IS NULL;
CREATE INDEX IX_app_role_permissions_role_id
    ON app_role_permissions(role_id);
CREATE INDEX IX_app_role_permissions_permission_id
    ON app_role_permissions(permission_id);
CREATE INDEX IX_app_role_permissions_deleted_on
    ON app_role_permissions(deleted_on);

CREATE UNIQUE INDEX UX_app_user_roles_pair
    ON app_user_roles(user_id, role_id, organization_id) WHERE deleted_on IS NULL;
CREATE INDEX IX_app_user_roles_user_id
    ON app_user_roles(user_id);
CREATE INDEX IX_app_user_roles_role_id
    ON app_user_roles(role_id);
CREATE INDEX IX_app_user_roles_org_id
    ON app_user_roles(organization_id);
CREATE INDEX IX_app_user_roles_deleted_on
    ON app_user_roles(deleted_on);

CREATE INDEX IX_app_user_data_scopes_user_id
    ON app_user_data_scopes(user_id);
CREATE INDEX IX_app_user_data_scopes_scope_module
    ON app_user_data_scopes(scope_module);
CREATE INDEX IX_app_user_data_scopes_scope_type
    ON app_user_data_scopes(scope_type);
CREATE INDEX IX_app_user_data_scopes_scope_org_id
    ON app_user_data_scopes(scope_org_id);
CREATE INDEX IX_app_user_data_scopes_is_active
    ON app_user_data_scopes(is_active);
CREATE INDEX IX_app_user_data_scopes_deleted_on
    ON app_user_data_scopes(deleted_on);
