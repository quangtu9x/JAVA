-- Template SQL cho feature departments theo pattern categories.
-- File này không nằm trong db/migration nên sẽ không được Flyway tự chạy.
-- Khi dùng thật, copy file này sang td-web/src/main/resources/db/migration/
-- và đổi version cho phù hợp.

CREATE TABLE departments (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code             VARCHAR(100) NOT NULL,
    name             VARCHAR(300) NOT NULL,
    description      TEXT,
    parent_id        UUID         REFERENCES departments(id),
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

CREATE UNIQUE INDEX UX_departments_code ON departments(code) WHERE deleted_on IS NULL;
CREATE INDEX IX_departments_parent_id ON departments(parent_id);
CREATE INDEX IX_departments_level ON departments(level);
CREATE INDEX IX_departments_is_active ON departments(is_active);
CREATE INDEX IX_departments_sort_order ON departments(sort_order);
CREATE INDEX IX_departments_deleted_on ON departments(deleted_on);