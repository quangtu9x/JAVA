# 01 — Worker Architecture

## 1. Vị trí

`td-woker` là project **độc lập hoàn toàn** khỏi project Spring Boot (`JAVA/td-web`, `td-application`, ...).
Không phải module Maven của `td-webapi-parent`; không có dependency nào khiến Worker phải chạy
cùng td-web; không sửa bất kỳ file/config/document nào của project tham khảo.

```
C:\code_projects\HSCV\JAVA\td-woker
├── pom.xml                     # Spring Boot 3.2.0, Java 17 (giống project tham khảo)
├── src/main/java/com/td/worker
│   ├── core/                   # Worker Core (generic, không biết nghiệp vụ)
│   │   ├── envelope/           # MessageEnvelope, MessageUnit, EnvelopeParser
│   │   ├── task/               # TaskHandler SPI, TaskHandlerRegistry, TaskContext
│   │   ├── checkpoint/         # CheckpointStore (PostgreSQL worker_units), UnitStatus
│   │   ├── dispatch/           # UnitDispatcher (executor + semaphore), RetryPoller
│   │   ├── flow/               # MessageFlowRunner (vòng đời 1 message)
│   │   └── logging/            # CorrelationContext (MDC correlationId/messageId/tenantId/unitId)
│   ├── infrastructure/
│   │   ├── tenant/             # TenantRegistry, TenantDataSourceManager (pool/tenant), WorkerTenantContext
│   │   ├── http/               # HTTP client pool (Apache HttpClient 5)
│   │   └── minio/              # MinIO client (tùy chọn)
│   ├── transport/
│   │   ├── rabbit/             # RabbitConfig (topology + listener), RabbitPublisher
│   │   └── kafka/              # KafkaConfig (consumer group + DLQ)
│   ├── tasks/                  # CÁC MODULE NGHIỆP VỤ THEO TỪNG LOẠI TASK
│   │   ├── sample/             # sample.echo (minh họa)
│   │   └── vbdi/               # VBDI reference implementation
│   └── config/                 # WorkerProperties, DataSource config, Metrics, Health, Shutdown
└── docs/                       # tài liệu riêng của Worker
```

## 2. Luồng xử lý 1 message

```
RabbitMQ queue / Kafka topic
        │  (manual ACK / AckMode.RECORD)
        ▼
InboundMessageRouter.route(rawBody)
        │  EnvelopeParser.parse → MessageEnvelope
        │  (lỗi parse → DLQ; lỗi hạ tầng → requeue/no-commit)
        ▼
MessageFlowRunner.run(envelope)
        1. storeMessage (idempotent theo messageId)
        2. initializeUnits (idempotent theo tenantId|messageId|unitId)
        3. for each unit → UnitDispatcher.dispatch (claim atomic + lease)
        4. chờ hoàn tất (timeout unit/message)
        5. evaluateAckPolicy → FlowResult.canAck
        ▼
UnitDispatcher.runUnit: TaskHandler.execute (chạy trong WorkerTenantContext.runAs(tenantId))
        ├─ success → store.complete(uniqueKey) → DONE
        └─ failure → store.fail(uniqueKey, nextRetryAt) → RETRY (attempts < max) hoặc FAILED
        ▼
RetryPoller (định kỳ):
        ├─ RETRY đã tới hạn → claim lại + xử lý (retry THEO UNIT, không phải toàn message)
        ├─ PROCESSING lease hết hạn → RETRY (crash recovery, Worker B tiếp tục)
        └─ PENDING quá lâu → RETRY
```

## 3. Quyết định kiến trúc kế thừa từ project Spring Boot (chỉ đọc tham khảo)

| Chủ đề | Quyết định của project tham khảo | Worker áp dụng |
|---|---|---|
| Ngôn ngữ/runtime | Java 17, Spring Boot 3.2.0 | Giống hệt (pom.xml) |
| Multi-tenant | Database-per-tenant; registry bảng `tenants` ở DB control-plane; key/salt AES `Encryptors.text` | `TenantRegistry` đọc bảng `tenants` (CHỈ ĐỌC); `TenantCredentialCipher` dùng CÙNG key/salt (`TENANT_CREDENTIAL_ENCRYPTION_KEY/SALT`) |
| Connection pool | HikariCP, `stringtype=unspecified`, auto-commit thấp | HikariCP; pool mỗi tenant tạo lười, giới hạn `max-pools` (evict LRU) |
| Messages | RabbitMQ topic + DLX/DLQ + batching (docs 08); at-least-once + idempotent consumer | `worker.exchange` topic; DLX/DLQ; checkpoint là nguồn sự thật |
| Checkpoint/retry | "outbox + idempotency bằng PostgreSQL" (docs 08 §3) | `worker_units` uniqueKey `tenantId|messageId|unitId`; retry per-unit bằng DB |
| Graceful shutdown | "drain khi restart, replay từ outbox" (docs 08 §9.3) | `UnitDispatcher.stop()` drain executor; message unacked sẽ redeliver |
| Health/metrics | Actuator `/actuator/health`, `/actuator/prometheus` | Actuator riêng cổng `8090` + health indicators |

## 4. Nguyên tắc tách lớp

- **Worker Core (`core/`)**: không import gì từ `tasks/`; không biết VBDI/OCR/ES...
- **Transport (`transport/`)**: biên giới broker; parse xong đưa về Core.
- **Tasks (`tasks/`)**: mỗi loại task 1 package `@Component` implements `TaskHandler`.
  Thêm task mới = thêm 1 file, không sửa Core.
- **Infrastructure (`infrastructure/`)**: pool/connection/tracing — phục vụ Core & Tasks.

Tài liệu tiếp theo: [02-worker-core.md](02-worker-core.md)