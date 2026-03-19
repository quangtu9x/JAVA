-- Add explicit node_type for organization tree hierarchy validation.

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS node_type VARCHAR(30);

UPDATE organizations
SET node_type = CASE
    WHEN level <= 1 THEN 'agency_level'
    WHEN level = 2 THEN 'agency'
    WHEN level = 3 THEN 'unit'
    ELSE 'department'
END
WHERE node_type IS NULL;

ALTER TABLE organizations
    ALTER COLUMN node_type SET DEFAULT 'agency_level';

ALTER TABLE organizations
    ALTER COLUMN node_type SET NOT NULL;

ALTER TABLE organizations
    DROP CONSTRAINT IF EXISTS CK_organizations_node_type;

ALTER TABLE organizations
    ADD CONSTRAINT CK_organizations_node_type
        CHECK (node_type IN ('agency_level', 'agency', 'unit', 'department'));

CREATE INDEX IF NOT EXISTS IX_organizations_node_type
    ON organizations(node_type);
