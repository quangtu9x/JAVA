# 09 — Configuration

## 1. Nguyên tắc

- KHÔNG hard-code connection string / credential / environment-specific config.
- Mọi giá trị mặc định trong `application.yml` chỉ là giá trị PHÁT TRIỂN; giá trị thật qua
  biến môi trường (xem `.env.example`).
- `WorkerProperties` (`@ConfigurationProperties(prefix = "worker")`) là nguồn duy nhất
  đọc cấu hình trong code.

## 2. Các nhóm cấu hình

| Nhóm | Key | Mục đích |
|---|---|---|
| Core | `worker.core.max-inflight-units`, `unit-timeout`, `message-timeout`, `retries-per-unit`, `retry-initial-backoff`, `retry-max-backoff`, `retry-poll-interval`, `claim-lease-seconds`, `pending-stale-timeout`, `ack-policy`, `shutdown-grace` | concurrency/timeout/retry/lease/ACK |
| DB Worker | `worker.db.*` (`url`, `username`, `password`, `schema-management`, `hikari.*`) | checkpoint store riêng |
| Control-plane | `worker.control-plane.*` (`url`, `username`, `password`, `credential-encryption-key/salt`, `refresh-interval`) | registry tenant (chỉ đọc) |
| Tenant pool | `worker.tenant.*` (`max-pools`, `pool-*`) | pool mỗi database tenant |
| HTTP | `worker.http.*` | HTTP client pool |
| MinIO | `worker.minio.*` | tùy chọn kiểm tra object |
| RabbitMQ | `worker.rabbit.*` (`enabled`, `exchange`, `dlx-suffix`, `dlq-suffix`, `max-redeliveries`, `queues[]`) | topology + listener |
| Kafka | `worker.kafka.*` (`enabled`, `group-id-prefix`, `auto-offset-reset`, `concurrency`, `dlq-topic-suffix`, `topics[]`) | consumer group |
| Tasks | `worker.tasks.vbdi.*` (`enabled`, `verify-object-exists`, `reply-topic`, `received-table`) | reference implementation |

## 3. Ví dụ env (xem `.env.example`)

```powershell
WORKER_MANAGEMENT_PORT=8090
WORKER_DB_URL=jdbc:postgresql://localhost:5432/tdworker
WORKER_DB_USERNAME=postgres
WORKER_DB_PASSWORD=postgres
CONTROL_PLANE_DATABASE_URL=jdbc:postgresql://localhost:5432/tdwebapi_controlplane
CONTROL_PLANE_DATABASE_USERNAME=postgres
CONTROL_PLANE_DATABASE_PASSWORD=postgres
TENANT_CREDENTIAL_ENCRYPTION_KEY=<cùng giá trị td-web>
TENANT_CREDENTIAL_ENCRYPTION_SALT=<cùng giá trị td-web, hex hợp lệ>
RABBITMQ_HOST=localhost
WORKER_RABBIT_ENABLED=true
WORKER_INSTANCE_ID=worker-1
```

## 4. Lưu ý bảo mật

- `TENANT_CREDENTIAL_ENCRYPTION_KEY/SALT` là secret thật — phải phân phối an toàn tới máy
  Worker (secret manager/vault, KHÔNG chép qua email/chat/git — docs/architecture/11 §13.3).
- Salt phải là chuỗi HEX hợp lệ (yêu cầu `Encryptors.text`).

## Đồng bộ Elasticsearch (`worker.search-sync`, 16/10/2026)

Biến `WORKER_SEARCH_SYNC_*` và `ELASTICSEARCH_URIS/USERNAME/PASSWORD/API_KEY`: bảng đầy đủ ở
[21-dong-bo-elasticsearch.md](21-dong-bo-elasticsearch.md) §3. Để trống `ELASTICSEARCH_URIS` thì bộ đồng bộ không
chạy, nhưng hàng đợi trong DB tenant vẫn được ghi; khai cấu hình sau rồi khởi động lại worker, hàng đợi sẽ tự bù.

Tài liệu tiếp: [10-deployment.md](10-deployment.md)
