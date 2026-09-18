-- =====================================================================
-- TD WORKER - Schema RIÊNG của Worker (database tdworker, KHÔNG đụng schema td-web).
-- Chạy bằng Flyway (worker.db.schema-management=flyway) khi khởi động Worker.
-- =====================================================================

-- Checkpoint/idempotency của TỪNG unit trong 1 message.
-- unique_key = tenantId + '|' + messageId + '|' + unitId  (XÁC ĐỊNH idempotency persistent).
-- Trạng thái: PENDING -> PROCESSING -> DONE | RETRY -> PROCESSING ... -> FAILED
CREATE TABLE worker_units (
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

-- Ràng buộc idempotency: worker khác / instance khác / redelivery không thể tạo dòng trùng.
CREATE UNIQUE INDEX ux_worker_units_unique_key ON worker_units(unique_key);
-- Poller retry theo unit: tìm RETRY đã tới hạn.
CREATE INDEX ix_worker_units_retry ON worker_units(status, next_retry_at);
-- Giám sát theo loại task.
CREATE INDEX ix_worker_units_task_type ON worker_units(task_type, status);

-- Message gốc đã nhận (audit + để mọi worker tái dựng unit khi cần; INSERT ON CONFLICT DO NOTHING).
CREATE TABLE worker_messages (
    message_id    VARCHAR(200) PRIMARY KEY,
    task_type     VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(200),
    source        VARCHAR(100),
    envelope_json TEXT         NOT NULL,
    received_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE worker_units IS 'Checkpoint/idempotency theo từng unit - nguồn sự thật về trạng thái xử lý, độc lập với broker';
COMMENT ON COLUMN worker_units.unique_key IS 'tenantId|messageId|unitId - khoá idempotency persistent';
COMMENT ON COLUMN worker_units.status IS 'PENDING/PROCESSING/DONE/FAILED/RETRY';
COMMENT ON COLUMN worker_units.lease_expires_at IS 'Hết hạn lease -> worker khác được claim lại (crash recovery, không xử lý kép trừ khi lease thật sự chết)';