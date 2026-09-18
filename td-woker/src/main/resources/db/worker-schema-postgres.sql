-- PostgreSQL - script idempotent tương đương db/migration-worker/V1 (dùng khi
-- worker.db.schema-management=script; production khuyến nghị dùng flyway).
CREATE TABLE IF NOT EXISTS worker_units (
    id                 BIGSERIAL PRIMARY KEY,
    unique_key         VARCHAR(512)  NOT NULL,
    tenant_id          VARCHAR(100)  NOT NULL,
    message_id         VARCHAR(200)  NOT NULL,
    unit_id            VARCHAR(200)  NOT NULL,
    task_type          VARCHAR(100)  NOT NULL,
    correlation_id     VARCHAR(200),
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempts           INT           NOT NULL DEFAULT 0,
    max_attempts       INT           NOT NULL DEFAULT 5,
    next_retry_at      TIMESTAMP WITH TIME ZONE,
    claimed_by         VARCHAR(200),
    claimed_at         TIMESTAMP WITH TIME ZONE,
    lease_expires_at   TIMESTAMP WITH TIME ZONE,
    payload_json       TEXT,
    last_error         TEXT,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_worker_units_unique_key ON worker_units(unique_key);
CREATE INDEX IF NOT EXISTS ix_worker_units_retry ON worker_units(status, next_retry_at);
CREATE INDEX IF NOT EXISTS ix_worker_units_task_type ON worker_units(task_type, status);

CREATE TABLE IF NOT EXISTS worker_messages (
    message_id    VARCHAR(200) PRIMARY KEY,
    task_type     VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(200),
    source        VARCHAR(100),
    envelope_json TEXT         NOT NULL,
    received_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);