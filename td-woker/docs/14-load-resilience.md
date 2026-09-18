# 14 — Chống quá tải, độ tin cậy và vận hành RabbitMQ

Tài liệu này là checklist triển khai cho Worker. Các giá trị dưới đây là cấu hình mặc định an toàn để bắt đầu; DBA/SRE phải hiệu chỉnh theo tải đo được.

## 1. Connection pool và database

`worker.db` là checkpoint database của Worker. Mỗi listener Rabbit có `concurrency` và có thể co giãn đến `max-concurrency`. Khi Rabbit bật, ứng dụng kiểm tra lúc khởi động:

- `1 <= concurrency <= max-concurrency`;
- `prefetch` phải nằm trong `1..10`;
- `worker.db.hikari.maximum-pool-size >= tổng max-concurrency` của các queue;
- rate limit không được âm.

Nếu sai, Worker không khởi động để tránh nhận message vượt năng lực checkpoint DB. Hikari có `connection-timeout-ms=30000` và `validation-timeout-ms=5000`; pool tenant cũng có hai timeout tương ứng. Hikari trả lỗi hữu hạn thay vì để request treo khi DB không phản hồi.

Trước khi rollout, tính ngân sách connection cho **mỗi database**:

```text
checkpoint DB: số_worker × worker.db.hikari.maximum-pool-size + connection quản trị
tenant DB i:   số_worker_tối_đa_chạm_tenant_i × worker.tenant.pool-maximum-size + connection ứng dụng khác
```

Hai tổng này phải nhỏ hơn `max_connections` của PostgreSQL sau khi trừ phần dự phòng DBA. Không cộng pool của những tenant vào checkpoint DB: chúng là các database vật lý khác nhau.

`JdbcCheckpointStore.initializeUnits()` có hai chế độ, đều idempotent nhờ unique key/`WHERE NOT EXISTS`:

- `WORKER_DB_UNIT_INSERT_MODE=batch` (mặc định): gọi `JdbcTemplate.batchUpdate()` **ngay khi một envelope đến**. Nó không đợi các message khác hay đợi batch-size “đầy”; `WORKER_DB_UNIT_INSERT_BATCH_SIZE` chỉ chia một envelope rất lớn thành các JDBC batch nhỏ.
- `WORKER_DB_UNIT_INSERT_MODE=single`: gọi từng `INSERT`, phù hợp để so sánh/khắc phục sự cố nhưng tốn round-trip DB hơn khi một message chứa nhiều unit.

## 2. Backpressure RabbitMQ

Các biến cấu hình:

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `WORKER_RABBIT_CONCURRENCY` | 4 | consumer ban đầu của queue |
| `WORKER_RABBIT_MAX_CONCURRENCY` | 8 | trần consumer của queue |
| `WORKER_RABBIT_PREFETCH` | 5 | message chưa ACK tối đa mỗi consumer |
| `WORKER_RABBIT_RATE_LIMIT_PER_SECOND` | 0 | trần tổng message/s của một queue; `0` là không giới hạn |

Rate limit được chia sẻ giữa các consumer của cùng queue, vì thế `20` thực sự là khoảng 20 message/s/queue, không phải 20 cho mỗi thread. Cơ chế này cố ý chặn listener trước khi dispatch, nhờ đó broker giữ message thay vì Worker giữ chúng trong RAM.

Khi database tenant yếu, bắt đầu với `concurrency=1`, `max-concurrency=1`, `prefetch=1`, rate limit thấp; sau đó tăng theo số connection và latency thực tế. `max-inflight-units` là admission control cho executor, không được dùng để suy ra connection pool của mọi tenant.

## 3. ACK, idempotency, retry và DLQ

- Listener dùng manual ACK. Chỉ `basicAck` sau khi `MessageFlowRunner` đã ghi checkpoint và policy ACK cho phép.
- Checkpoint `worker_units` có unique key `tenantId|messageId|unitId`; claim là atomic. VBDI receiver dùng `send_log_id` primary key và `INSERT ... ON CONFLICT DO NOTHING`.
- Lỗi hạ tầng trả `TRANSIENT_ERROR`: Rabbit requeue có giới hạn `max-redeliveries`, sau đó `basicNack(requeue=false)` vào DLQ. Lỗi envelope vào DLQ ngay.
- Retry nghiệp vụ theo từng unit ở checkpoint DB với exponential backoff, mặc định 5s, 10s, 20s... và tối đa 5 phút. Sau số lần `maxAttempts`, unit thành `FAILED` để điều tra/replay có kiểm soát.

### Tự phục hồi sau deploy/sửa dependency

`WAITING_DEPENDENCY` giữ task khi chưa có `TaskHandler` hoặc handler ném `TaskDependencyException` (ví dụ dịch vụ phụ thuộc chưa sẵn sàng). Worker tự kiểm tra lại theo `WORKER_DEPENDENCY_RETRY_INTERVAL` (mặc định 5 phút) và không tiêu `maxAttempts`; sau khi deploy handler hoặc dependency hồi phục, unit tự chạy tiếp. `FAILED` và DLQ vẫn được giữ nguyên để tránh tự lặp vô hạn với dữ liệu sai; cần replay có kiểm soát sau khi sửa dữ liệu/quy tắc nghiệp vụ.

Không cấu hình TTL của main queue như một cơ chế retry: TTL chỉ nên dùng khi có queue retry chuyên dụng. Main queue Worker hiện không TTL để message bình thường không tự bị dead-letter.

## 4. Monitoring và alert

Scrape `/actuator/prometheus`. Thiết lập RabbitMQ Prometheus plugin hoặc exporter để có metric queue/DLQ. Ví dụ PrometheusRule (đổi `job`/queue theo môi trường):

```yaml
groups:
  - name: td-worker
    rules:
      - alert: TdWorkerDlqBacklog
        expr: rabbitmq_queue_messages_ready{queue=~".*\\.dlq"} > 0
        for: 5m
        labels: { severity: warning }
        annotations: { summary: "Worker DLQ có message" }
      - alert: TdWorkerRetryBacklog
        expr: worker_retry_due > 100
        for: 10m
        labels: { severity: warning }
        annotations: { summary: "Retry checkpoint đang tăng" }
      - alert: TdWorkerFailedUnits
        expr: increase(worker_units_final_total{status="failed"}[15m]) > 10
        for: 5m
        labels: { severity: critical }
        annotations: { summary: "Nhiều unit Worker thất bại" }
      - alert: TdWorkerPoolSaturated
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.85
        for: 10m
        labels: { severity: warning }
        annotations: { summary: "Hikari pool gần cạn" }
```

Alert DLQ phải có runbook: kiểm tra lỗi envelope/credential/DB, sửa nguyên nhân, sau đó replay có kiểm soát và dựa vào idempotency, không purge DLQ trước khi lưu mẫu lỗi.
