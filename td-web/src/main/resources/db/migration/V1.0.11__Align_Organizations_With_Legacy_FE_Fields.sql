-- Align organizations schema with legacy FE payload fields.

ALTER TABLE organizations
    RENAME COLUMN code TO identifier;

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS system INT NOT NULL DEFAULT 0;

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS receiver_id VARCHAR(100);

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS receiver VARCHAR(200);

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS receiver_position VARCHAR(200);

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS parent VARCHAR(300);

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS parentid VARCHAR(64);

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS servername VARCHAR(300);

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS server_id VARCHAR(64);

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS ipserver VARCHAR(100);

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS dbpath VARCHAR(100);

UPDATE organizations
SET parentid = UPPER(REPLACE(parent_id::text, '-', ''))
WHERE parentid IS NULL
  AND parent_id IS NOT NULL;

UPDATE organizations child
SET parent = p.name
FROM organizations p
WHERE child.parent_id = p.id
  AND (child.parent IS NULL OR child.parent = '');
