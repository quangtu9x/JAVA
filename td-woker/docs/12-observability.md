# 12 — Observability: Logging / Metrics / Health

## 1. Structured logging

- `CorrelationContext` (MDC) tự động set/clear quanh mỗi message/unit với các key BẮT BUỘC:
  `correlationId`, `messageId`, `tenantId`, `unitId`, `taskType`.
- `logback-spring.xml`:
  - Console (dev): pattern có in các MDC key.
  - File (production): **JSON** (LogstashEncoder) — đưa thẳng vào ELK/Loki; rolling theo ngày/100MB.
- File log mặc định `logs/td-worker.log` (đổi via `LOG_FILE`).

Ví dụ log unit thất bại:

```text
Unit u1 (task vbdi.delivery, tenant H05, attempt 2/3) -> RETRY; error=...; nextRetryAt=...
```

## 2. Metrics (Micrometer → Prometheus)

Expose tại `GET /actuator/prometheus`:

| Metric | Mô tả |
|---|---|
| `worker_messages_received_total` | số message nhận từ broker |
| `worker_messages_failed_parse_total` | số message parse lỗi (→ DLQ) |
| `worker_units_final_total{task_type,status}` | unit về DONE/FAILED (counter) |
| `worker_unit_execution_seconds` | thời gian xử lý 1 unit (percentile 50/90/99) |
| `worker_retry_due` | unit RETRY đã tới hạn chưa xử lý (backlog) |
| `worker_inflight_units` | unit đang xử lý (gauge — backpressure) |
| `worker_tenant_pools` | số pool tenant đang mở (gauge) |

Kèm các metrics hệ thống mặc định của Boot/JVM (jvm_*, hikaricp_*, rabbit*).

## 3. Health / Readiness

- `GET /actuator/health` (`/health/liveness`, `/health/readiness`).
- `CheckpointStoreHealthIndicator`: DB checkpoint down → `DOWN` (Worker không thể claim ⇒
  không nên nhận message).
- `TenantRegistryHealthIndicator`: control-plane lỗi / chưa có tenant → `DOWN`/`OUT_OF_SERVICE`
  kèm reason — nhưng Worker vẫn khởi động (fail mềm theo docs/architecture/11 §13.5).

## 4. Truy vấn vận hành (checkpoint)

`worker_units` là nguồn để vận hành trả lời "job nào đang treo/lỗi":

```sql
-- Thống kê theo task
SELECT task_type, status, count(*) FROM worker_units GROUP BY 1,2;
-- Unit RETRY quá lâu
SELECT * FROM worker_units WHERE status='RETRY' AND next_retry_at < now() - interval '1 hour';
-- Unit FAILED (DLQ-equivalent) gần đây
SELECT * FROM worker_units WHERE status='FAILED' ORDER BY updated_at DESC LIMIT 50;
```

`worker_messages` lưu envelope gốc để tái dựng gửi lại.

Tài liệu tiếp: [13-testing.md](13-testing.md)