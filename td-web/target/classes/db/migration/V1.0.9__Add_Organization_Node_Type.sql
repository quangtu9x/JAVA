-- Add constraint and index on form column for organization tree hierarchy validation.

ALTER TABLE organizations
    DROP CONSTRAINT IF EXISTS CK_organizations_form;

ALTER TABLE organizations
    ADD CONSTRAINT CK_organizations_form
        CHECK (form IN ('agency_level', 'agency', 'unit', 'department'));

CREATE INDEX IF NOT EXISTS IX_organizations_form
    ON organizations(form);
