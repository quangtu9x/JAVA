-- BL-104: Document Numbering Service
CREATE TABLE numbering_configs (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_type VARCHAR(50) NOT NULL,
    format        VARCHAR(100) NOT NULL,
    reset_policy  VARCHAR(20) NOT NULL DEFAULT 'YEARLY',
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_numbering_configs_document_type ON numbering_configs(document_type);

CREATE TABLE numbering_counters (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_type VARCHAR(50) NOT NULL,
    year          INT NOT NULL,
    scope_key     VARCHAR(100) NOT NULL DEFAULT '_',
    current_value BIGINT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_numbering_counters_scope ON numbering_counters(document_type, year, scope_key);

INSERT INTO numbering_configs (document_type, format, reset_policy) VALUES
    ('vbden', '{seq}/{year}', 'YEARLY'),
    ('vbdi', '{seq}/{year}-VBD', 'YEARLY');

COMMENT ON TABLE numbering_configs IS 'Cau hinh format sinh so van ban theo tung document_type (BL-104)';
COMMENT ON COLUMN numbering_configs.format IS 'Template ho tro {seq}, {seq:0N} (zero-pad N chu so), {year}';
COMMENT ON TABLE numbering_counters IS 'Bo dem sinh so van ban - tang nguyen tu qua UPSERT (INSERT ... ON CONFLICT DO UPDATE ... RETURNING), an toan duoi concurrent access nho row lock cua Postgres, khong can pessimistic lock o tang ung dung';
COMMENT ON COLUMN numbering_counters.scope_key IS 'Don vi/to chuc khi can scope theo don vi (vd. so di); mac dinh "_" khi chi scope theo nam (vd. so den)';
