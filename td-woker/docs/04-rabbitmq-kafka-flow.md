# 04 — RabbitMQ / Kafka Flow

## 1. Nguyên tắc chung

- **Checkpoint PostgreSQL là nguồn sự thật**, broker chỉ là kênh vận chuyển.
- Delivery guarantee: **at-least-once** + idempotent consumer (đúng quyết định docs/architecture/08 §5).
- Envelope là lớp trừu tượng — đổi broker không đổi code nghiệp vụ (docs/architecture/08 §8.6).

## 2. RabbitMQ

### Topology (khai báo bằng RabbitAdmin lúc khởi động)

```
worker.exchange (topic, durable)
   ├─ binding worker.*            → queue worker.tasks  (generic)
   └─ binding worker.<taskType>   → queue worker.<taskType> (chuyên biệt)
Queue worker.tasks:
   x-dead-letter-exchange = worker.tasks.retry
worker.tasks.retry (topic) → worker.tasks.retry.queue (durable, TTL = retry-delay-ms)
worker.tasks.retry.queue → hết TTL / DLX → worker.exchange với routing key gốc → worker.tasks
worker.tasks.dlq (durable, không TTL)
```

### Consumer (manual ACK)

| Kết quả route | Hành động |
|---|---|
| `PARSE_ERROR` (envelope lỗi) | publish DLQ, chờ publisher confirm, rồi ACK nguồn |
| `FLOW_OK` + `canAck=true` | `basicAck` |
| `FLOW_OK` + `canAck=false` | để unacked → broker redeliver khi ack-timeout; idempotent init nên không trùng |
| `TRANSIENT_ERROR` (DB checkpoint down...) | `basicNack(requeue=false)` → retry queue TTL → main; quá `max-redeliveries` → DLQ |

Retry **theo unit** KHÔNG qua broker — do `RetryPoller` trên DB (xem [07-idempotency-checkpoint.md](07-idempotency-checkpoint.md)). Retry queue chỉ dành cho lỗi trước khi checkpoint được ghi, như checkpoint DB tạm mất kết nối.
Main queue không đặt TTL: TTL tại main queue sẽ đẩy cả message bình thường sang DLQ nếu consumer
tạm thời không bắt kịp. Giới hạn redelivery và retry theo unit là cơ chế chống lỗi chính.

### Cập nhật topology đang chạy

RabbitMQ không cho đổi arguments của queue đã tồn tại. Khi nâng cấp từ topology cũ, dừng producer/consumer, xử lý hoặc export message ở main queue, xóa riêng queue chính và topology liên quan, sau đó khởi động Worker để RabbitAdmin khai báo lại. Không xóa DLQ trước khi backup/replay message.

### Publish outbound (handler → broker)

`RabbitPublisher.publish(exchange, routingKey, json)` dùng `RabbitTemplate` (Spring Boot quản lý
connection/channel pool). VBDI reply dùng routing key `senderReplyRoutingKey` nhúng trong envelope.

## 3. Kafka

### Consumer group

- Worker instance dùng CÙNG `group.id = <groupIdPrefix><topic>` → Kafka tự load balance qua
  partition/rebalance (competing consumers) — an toàn chạy nhiều Worker.
- `AckMode.RECORD` — offset commit sau khi listener trả về. Redelivery sau rebalance/crash chỉ
  gây xử lý lại record, nhưng unit DONE không bao giờ chạy lại (idempotent).

| Kết quả route | Hành động |
|---|---|
| `PARSE_ERROR` | publish DLQ (`topic + dlq-suffix`) rồi trả về (auto-commit) |
| `FLOW_OK` | trả về (auto-commit offset) |
| `TRANSIENT_ERROR` | ném exception → `DefaultErrorHandler` retry backoff → DLQ |

### Concurrency

`worker.kafka.concurrency` đặt số thread listener/container (nhiều hơn số partition không tăng
throughput, vì Kafka giữ thứ tự theo partition; tăng partition mới thật sự tăng song song).

## 4. Bảng so sánh nhanh

| | RabbitMQ | Kafka |
|---|---|---|
| Competing consumers | N Worker cùng consume 1 queue | Cùng `group.id` chia partition |
| ACK | manual (channel.basicAck/Nack) | offset commit (AckMode.RECORD) |
| Retry | per-unit qua DB + requeue bounded | per-unit qua DB + DefaultErrorHandler |
| DLQ | queue `.dlq` | topic `.dlq` |

Tài liệu tiếp: [05-multi-instance.md](05-multi-instance.md)
