# 08 — Database / MinIO / HTTP Connection

## 1. Database

| Nguồn | Pool | Cấu hình |
|---|---|---|
| Checkpoint store (database RIÊNG `tdworker`) | `WorkerDataSourceConfig` (Hikari) | `worker.db.hikari.*` |
| Control-plane (bảng `tenants`, CHỈ ĐỌC) | `TenantRegistry` (Hikari, max 5) | `worker.control-plane.*` |
| Mỗi database tenant | `TenantDataSourceManager` (Hikari lười) | `worker.tenant.*` |

- `worker.db.schema-management=flyway` (production): Worker migrate database CỦA MÌNH
  (`db/migration-worker`), KHÔNG bao giờ migrate database tenant.
- `worker.db.schema-management=script` (test/H2): chạy `worker-schema-h2.sql`.

## 2. MinIO

- Worker truy cập MinIO là TUỲ CHỌN: chỉ khi handler cần kiểm tra `objectKey` tham chiếu
  trong envelope ("message nhẹ", docs/architecture/08 §3.2). Worker KHÔNG tải file về máy —
  chỉ FE (qua td-web) mới tải khi người dùng bấm xem (docs/architecture/11 §13.2).
- `WorkerMinioClient.objectExists(objectKey)`; bật bằng `worker.minio.enabled` (mặc định false).

## 3. HTTP client pool

- `WorkerHttpClient` dùng Apache HttpClient 5 `PoolingHttpClientConnectionManager` —
  connection reuse/keep-alive, eviction idle/expired.
- Cấu hình: `worker.http.max-connections`, `max-connections-per-route`, `connect-timeout`,
  `socket-timeout`, `connection-ttl`.
- Handler gọi API ngoài qua `get(url)` / `postJson(url, body)`; timeout nghiệp vụ tự handler
  set, timeout unit của Core bao quanh toàn bộ call.

## 4. Mối quan hệ kích thước pool (backpressure)

Chuỗi giới hạn được nối liền để tránh quá tải hạ tầng:

```
UnitDispatcher (thread = max-inflight-units)
   → admission semaphore (số unit xử lý song song tối đa)
   → TenantDataSourceManager (max-pools, evict LRU, mỗi pool max-connections)
   → WorkerHttpClient (max-connections / per-route)
```

- Khi executor+semaphore đầy: `dispatch` trả empty → `RetryPoller` xử lý sau — không chặn
  thread broker; broker giữ prefetch nhỏ để nhịp vào.
- Khi chạm `max-pools`: pool tenant lâu không dùng bị evict (LRU) — vẫn đủ chỗ cho tenant
  đang hoạt động.
- RabbitMQ: `worker.rabbit.queues[].prefetch` giới hạn số message chưa ACK / consumer.
- Kafka: concurrency ≤ số partition để tận dụng tối đa mà không tráo thứ tự.

## 5. Graceful shutdown

Thứ tự stop (SmartLifecycle + shutdown hook):
1. Listener Rabbit/Kafka dừng nhận message mới (containers dừng trước).
2. `UnitDispatcher.stop()` drain executor (chờ tối đa `shutdown-grace`, sau đó hủy task còn lại
   — lease hết hạn bảo vệ).
3. `WorkerShutdownHooks` đóng pool tenant + control-plane.
4. Message đang xử lý dở khi dừng: vì chưa được settle đầy đủ nên ở trạng thái PROCESSING với
   lease đang đếm; Worker khác/poller sẽ đón khi lease hết hạn.

Tài liệu tiếp: [09-configuration.md](09-configuration.md)