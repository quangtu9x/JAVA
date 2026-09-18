# TD WORKER — Generic Distributed Worker Platform

Worker **ĐỘC LẬP hoàn toàn** khỏi project Spring Boot (td-web). Xây dựng theo docs/promt.txt:
generic Worker Platform (Worker Core hoàn to không phụ thuộ nghiệp vụ), multi-tenant,
multi-unit message, idempotency/checkpoint persistent, RabbitMQ + Kafka, PostgreSQL/MinIO/HTTP
connection pool, graceful shutdown, structured logging, metrics, health.

## Kiến trúc tổng quan

```
RabbitMQ/Kafka ──► InboundMessageRouter ──► MessageFlowRunner ──► UnitDispatcher (executor pool)
                                              │  │  │
                                              │  │  └─► TaskHandlerRegistry ─► task handlers
                                              │  └────► CheckpointStore (PostgreSQL worker_units)
                                              └───────► RetryPoller (retry theo unit, lease crash-recovery)
```

Tài liệu riêng của Worker nằm ở [`docs/`](docs/README.md) — xem land trước tiếp:

| Tài liệu | Nội trang |
|---|---|
| [docs/README.md](docs/README.md) | Danh sách toàn bộ tài liệu Worker |
| [docs/01-architecture.md](docs/01-architecture.md) | Worker architecture / module layout |
| [docs/02-worker-core.md](docs/02-worker-core.md) | Worker Core (envelope, flow, dispatcher) |
| [docs/03-task-handler-model.md](docs/03-task-handler-model.md) | Task/Handler model + thêm task mới |
| [docs/04-rabbitmq-kafka-flow.md](docs/04-rabbitmq-kafka-flow.md) | RabbitMQ/Kafka flow + ACK policy |
| [docs/06-multi-tenant.md](docs/06-multi-tenant.md) | Multi-tenant database-per-tenant |
| [docs/07-idempotency-checkpoint.md](docs/07-idempotency-checkpoint.md) | Idempotency/checkpoint/retry/DLQ |
| [docs/09-configuration.md](docs/09-configuration.md) | Config (+ .env.example) |
| [docs/10-deployment.md](docs/10-deployment.md) | Deployment độc lập (Docker/build) |
| [docs/11-vbdi-reference.md](docs/11-vbdi-reference.md) | VBDI reference implementation |

## Build / Test

```powershell
cd JAVA/td-woker
mvn clean test           # unit test + context test (H2, broker off)
mvn -DskipTests package  # → target/td-worker.jar
```

## Run dev (local)

1. Tạo database Worker: `CREATE DATABASE tdworker;` (Postgres).
2. Copy `.env.example` thành `.env`, rồi cập nhật cấu hình DB. Spring Boot tự nạp
   `.env`; biến môi trường của hệ điều hành vẫn được ưu tiên cao hơn.
3. Chạy thì chọn 1:
   - `mvn spring-boot:run`
   - `java -jar target/td-worker.jar`

Health/metrics tại cổng actuator (đúng `WORKER_MANAGEMENT_PORT`):
- `GET /actuator/health`
- `GET /actuator/prometheus` — Worker metrics (`worker_*`)

## Producer: gửi message

Publish JSON envelope vào exchange `worker.exchange` (RabbitMQ) / topic `worker.tasks` (Kafka),
routing key `worker.<taskType>` (Rabbit). Mẫu:

```json
{
  "messageId": "msg-<uuid>",
  "taskType": "vbdi.delivery",
  "correlationId": "corr-<uuid>",
  "source": "td-web",
  "units": [
    {
      "unitId": "u-1",
      "tenantId": "H05",
      "payload": { "sendLogId": "sl-1", "documentId": "doc-1", "recipientGroup": "internal_unit", "recipientIdentifier": "H06" }
    }
  ]
}
```

> KHÔNG hard-code credential/connection trong code — mọi giá trị qua biến môi trường
> (`.env` → `application.yml` → `WorkerProperties`).
