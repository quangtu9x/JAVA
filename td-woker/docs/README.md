# TD WORKER — Tài liệu (Docs Index)

Bộ tài liệu RIÊNG của Worker Platform, xây dựng theo `td-woker/docs/promt.txt` mục "Tài liệu".
Tham chiếu kiến trúc kế thừa từ project Spring Boot (`JAVA/.../docs/architecture/06, 08, 11`) —
**chúng ta không sửa/sửa lại các tài liệu thiết kế hiện tại của project tham khảo**.

| # | Tài liệu | Nội dung |
|---|---|---|
| 01 | [01-architecture.md](01-architecture.md) | Worker architecture, module layout, quyết định kế thừa từ Spring Boot |
| 02 | [02-worker-core.md](02-worker-core.md) | Worker Core: envelope, MessageFlowRunner, UnitDispatcher |
| 03 | [03-task-handler-model.md](03-task-handler-model.md) | Task/Handler model, cách thêm task mới |
| 04 | [04-rabbitmq-kafka-flow.md](04-rabbitmq-kafka-flow.md) | RabbitMQ/Kafka flow, ACK policy, DLQ |
| 05 | [05-multi-instance.md](05-multi-instance.md) | Multi-instance/multi-machine, competing consumers, balloon |
| 06 | [06-multi-tenant.md](06-multi-tenant.md) | Multi-tenant (database-per-tenant), control-plane registry |
| 07 | [07-idempotency-checkpoint.md](07-idempotency-checkpoint.md) | Idempotency/checkpoint, retry theo unit, crash recovery |
| 08 | [08-connections.md](08-connections.md) | Database/MinIO/HTTP connection pool |
| 09 | [09-configuration.md](09-configuration.md) | Cấu hình, biến môi trường, `.env.example` |
| 14 | [14-load-resilience.md](14-load-resilience.md) | Chống quá tải, retry/DLQ, connection budget và alert |
| 10 | [10-deployment.md](10-deployment.md) | Deployment độc lập (Docker, build, run) |
| 11 | [11-vbdi-reference.md](11-vbdi-reference.md) | VBDI reference implementation |
| 12 | [12-observability.md](12-observability.md) | Structured logging, metrics, health/readiness |
| 13 | [13-testing.md](13-testing.md) | Test & kiểm chứng các tình huống lỗi |
| 15 | [15-vbden-forward-unit-reference.md](15-vbden-forward-unit-reference.md) | vbden.forward_unit — chuyển xử lý cross-tenant cho đơn vị con có văn thư riêng |
| 16 | [16-checklist-tich-hop-gui-nhan-quoc-gia.md](16-checklist-tich-hop-gui-nhan-quoc-gia.md) | Checklist tích hợp "Gửi nhận quốc gia" (VPCP/VXP, eDXML QCVN 102) từ `docs/td-guinhanquocgia` — khung sườn `guinhanquocgia.send` |
| 17 | [17-checklist-tich-hop-kyso-simpki.md](17-checklist-tich-hop-kyso-simpki.md) | Checklist tích hợp "Ký số SIM-PKI" (MSSP/ETSI TS 102 204) từ `docs/TDSimSign/sdk-mobilepki` — `simpki.sign` |
| 18 | [18-trien-khai-test-simpki.md](18-trien-khai-test-simpki.md) | Triển khai môi trường TEST chỉ chạy `simpki.sign` (build/run script Linux+Windows, profile riêng, database mới, trigger thử) |

Để hiểu nhanh: đọc `01` → `02` → `03`, sau đó `07` (phần quan trọng nhất) và `11` (use case. cap chứng).
