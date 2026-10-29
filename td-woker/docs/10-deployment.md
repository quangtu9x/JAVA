# 10 — Deployment độc lập

## 1. Build

```powershell
cd JAVA/td-woker
mvn clean package -DskipTests
# → target/td-worker.jar
```

Hoặc Docker:

```bash
docker build -t td-worker:1.0.0 .
```

## 2. Run

```powershell
java -jar target/td-worker.jar
# hoặc
mvn spring-boot:run
```

Docker Compose (phát triển/smoke): `docker compose up -d --build` — gồm worker-db (Postgres
`tdworker`), rabbitmq (management UI port 15672), worker (port 8090).

## 3. Yêu cầu hạ tầng TRƯỚC khi chạy thật

1. Database `tdworker` (checkpoint store) được DBA tạo sẵn — Worker tự tạo BẢNG bên trong.
2. Control-plane (`tdwebapi_controlplane`) reachable + cùng `TENANT_CREDENTIAL_ENCRYPTION_KEY/SALT`
   với `td-web` (docs/architecture/11 §13.3).
3. Firewall/pg_hba: từ máy Worker → control-plane + **MỌI** database tenant
   (docs/architecture/11 §13.2). Tenant mới sau này cũng phải mở cho IP Worker.
4. RabbitMQ broker (nếu bật) / Kafka broker (nếu bật).
5. **`td-web` migrate tenant TRƯỚC** khi Worker xử lý tenant đó — Worker KHÔNG migrate tenant
   (docs/architecture/11 §13.4, §13.5).

## 4. Chạy nhiều instance

Triển khai tối thiểu 2 instance trên ≥2 máy (docs/architecture/11 §13.7). Mỗi instance:

- `WORKER_INSTANCE_ID` tuỳ chọn (tiền tố dễ đọc; Worker tự nối hậu tố ngẫu nhiên nên copy cùng giá
  trị/scale replica vẫn an toàn — từ 23/10/2026).
- Đồng bộ giờ (NTP) vẫn nên bật cho log, nhưng lease đã tính theo đồng hồ DB nên lệch giờ không gây
  xử lý kép.
- Kiểm tra `max_connections` PostgreSQL: mỗi máy thêm ≈ 6 kết nối/DB tenant + 10 kết nối DB worker.
- Xem bảng đánh giá an toàn theo loại việc: [05-multi-instance.md §5](05-multi-instance.md).
- Cùng `WORKER_DB_URL` (checkpoint dùng chung), cùng broker.
- Chịu được mất kết nối tạm thời khi khởi động (Hikari connection-timeout, Rabbit retry,
  `TenantRegistry` fail mềm).

## 5. Giám sát

- `GET http://<worker>:8090/actuator/health` (có `/health/liveness`, `/health/readiness`).
- `GET http://<worker>:8090/actuator/prometheus` — metrics `worker_*`.
- RabbitMQ management UI (`/queue`, xem queue depth/unacked/DLQ).
- Kafka consumer lag qua CLI/UI.

## 6. Lệnh khắc phục nhanh

| Triệu chứng | Hướng xử lý |
|---|---|
| Unit FAILED hàng loạt | Kiểm tra `countByStatus`/log; tenant không có pool → kiểm tra registry/control-plane/mạng |
| Message vào DLQ | Đọc envelope log; sửa producer hoặc khởi động lại từ DLQ |
| Worker chậm | Xem `/actuator/prometheus` `worker_inflight_units`/`worker_retry_due`; tăng thread/instance theo nhịp |

Tài liệu tiếp: [11-vbdi-reference.md](11-vbdi-reference.md)