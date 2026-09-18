-- Error Tracking: DB RIENG, tach hoan toan khoi DB nghiep vu (xem
-- com.td.infrastructure.errortracking.ErrorTrackingDataSourceConfig). Flyway migrate bang nay chay
-- tren datasource rieng (app.error-tracking.datasource.*), KHONG chung migration history voi
-- "flyway_schema_history" cua DB nghiep vu (db/migration).
--
-- log_type phan biet BUSINESS (exception cu the, gan voi 1 request) vs SYSTEM (su co ha tang: request
-- cham, deadlock...) - xem com.td.infrastructure.errortracking.LogType/Severity. Du lieu cu hon
-- app.error-tracking.retention-days (mac dinh 30 ngay) duoc ErrorLogCleanupScheduler tu dong xoa.
CREATE TABLE IF NOT EXISTS error_log (
    error_id          VARCHAR(64) PRIMARY KEY,
    log_type          VARCHAR(20) NOT NULL DEFAULT 'BUSINESS',
    severity          VARCHAR(20) NOT NULL DEFAULT 'ERROR',
    occurred_at       TIMESTAMPTZ NOT NULL,
    http_status       INTEGER,
    http_method       VARCHAR(10),
    request_path      VARCHAR(500),
    trace_id          VARCHAR(100),
    username          VARCHAR(200),
    remote_addr       VARCHAR(64),
    exception_class   VARCHAR(500),
    message           TEXT,
    stack_trace       TEXT,
    request_body      TEXT,
    duration_ms       BIGINT
);

-- occurred_at: phuc vu ca sap xep/loc theo thoi gian (UI quan tri) lan cleanup job hang ngay.
CREATE INDEX IF NOT EXISTS idx_error_log_occurred_at ON error_log (occurred_at);
CREATE INDEX IF NOT EXISTS idx_error_log_trace_id ON error_log (trace_id);
-- Phuc vu loc nhanh theo phan loai tren UI quan tri (phan biet log he thong / log nghiep vu, va muc do).
CREATE INDEX IF NOT EXISTS idx_error_log_log_type ON error_log (log_type);
CREATE INDEX IF NOT EXISTS idx_error_log_severity ON error_log (severity);
