# Hướng dẫn publish message (RabbitMQ/Kafka) cho tính năng mới

> Viết 04/09/2026 — yêu cầu người dùng "viết class chung... để sau có thể tái dùng, không cần code
> riêng từng chức năng". Đọc thêm bối cảnh/thiết kế đầy đủ ở
> `docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md` addendum 04/09/2026 (tài liệu
> này chỉ là HƯỚNG DẪN THAO TÁC, không lặp lại phần thiết kế).

## 1. Có gì sẵn — không cần build lại

| Lớp | Ở đâu | Vai trò |
|---|---|---|
| `OutboxPublisherService` | `td-application/messaging` | API DUY NHẤT bạn cần gọi |
| `OutboundMessage`/`MessageUnitSpec` | `td-application/messaging` | DTO mô tả message (không biết Rabbit/Kafka) |
| `MessagePublisher` | `td-application/messaging` (port) | KHÔNG gọi trực tiếp — outbox lo việc này |
| `OutboxEvent` | `td-domain/messaging` | Bảng `outbox_events` (đã có migration) |
| `RabbitMessagePublisher`/`KafkaMessagePublisher` | `td-infrastructure/messaging` | Chọn 1 qua `app.messaging.broker` — bạn không cần đụng vào |
| `OutboxDispatcherScheduler` | `td-infrastructure/messaging` | Tự chạy nền, publish batch `PENDING` mỗi tenant |

## 2. Các bước để publish 1 message mới (vd task type `"foo.bar"`)

1. **Định nghĩa payload** — 1 POJO thường (Lombok `@Data`/`@Builder`), field khớp ĐÚNG những gì
   phía consumer (`td-woker` hoặc consumer khác) mong đợi. Đặt ở package nghiệp vụ của bạn (KHÔNG
   đặt trong `messaging` — package đó chỉ chứa hạ tầng generic), vd
   `com.td.application.foo.FooBarMessagePayload` — xem `VbdiDeliveryMessagePayload` làm ví dụ.

2. **Xác định `tenantId` đích** cho từng unit — tenant nào sẽ XỬ LÝ message này (không nhất thiết
   là tenant hiện tại). Nếu KHÔNG có tenant đích thật (task chạy trên tenant hiện tại), dùng
   `currentTenantProvider.currentTenantId()`.

3. **Trong use case của bạn (đã `@Transactional`)**, sau khi ghi xong dữ liệu nghiệp vụ, gọi:

   ```java
   var unit = new MessageUnitSpec(unitId, targetTenantId, payload); // payload = POJO bước 1
   var message = new OutboundMessage("foo.bar", correlationId, List.of(unit));
   outboxPublisherService.enqueue("foo", aggregateId, message);
   ```

   - `"foo"` = tên aggregate (chỉ để truy vết/lọc, tuỳ bạn đặt).
   - `aggregateId` = id bản ghi nghiệp vụ vừa tạo/sửa (dùng để tra ngược khi cần).
   - `correlationId` = 1 id ổn định để log/dò vết (thường dùng lại id bản ghi nghiệp vụ).
   - KHÔNG cần try/catch riêng — nếu bạn muốn publish là best-effort (không làm hỏng nghiệp vụ
     chính khi lỗi), tự bọc try/catch quanh lời gọi `enqueue` như `AddVbdiSendLogUseCase` đã làm;
     nếu muốn publish là BẮT BUỘC (lỗi phải rollback cả transaction), để nguyên không bọc.

4. **Xong.** `OutboxDispatcherScheduler` tự động publish trong vài giây (mặc định 5s,
   `app.messaging.outbox.poll-interval-ms`) — không cần code gì thêm dù chọn RabbitMQ hay Kafka.

## 3. Đổi broker (RabbitMQ ↔ Kafka)

Chỉ đổi 1 biến môi trường `APP_MESSAGING_BROKER=rabbit` hoặc `kafka` (mặc định `rabbit`) —
KHÔNG sửa code. Cả 2 client (`spring.rabbitmq.*`/`spring.kafka.*`) luôn được cấu hình sẵn trong
`application.yml`, chỉ 1 `MessagePublisher` bean active theo giá trị này
(`@ConditionalOnProperty`).

## 4. Quy ước quan trọng — PHẢI giữ đúng để `td-woker` (hoặc consumer khác) nhận được

- Exchange RabbitMQ: `app.messaging.exchange` (mặc định `worker.exchange`) — PHẢI khớp
  `worker.rabbit.exchange` phía consumer.
- Routing key: `app.messaging.routing-prefix + taskType` (mặc định `worker.` + taskType, vd
  `worker.foo.bar`).
- Topic Kafka: `app.messaging.kafka-topic` (mặc định `worker.tasks`) — PHẢI khớp 1 topic consumer
  đang lắng nghe; router phía consumer đọc `taskType` TỪ BODY envelope, không phải tên topic.
- Đổi 3 giá trị trên chỉ khi bạn CHỦ ĐỘNG dựng 1 consumer khác dùng exchange/topic riêng — mặc định
  giữ nguyên để tương thích `td-woker` hiện có.

## 5. Khi nào KHÔNG dùng cơ chế này

Nếu tính năng của bạn cần publish NGAY LẬP TỨC trong request thread (không chấp nhận độ trễ vài
giây của outbox poller) — cân nhắc lại: đa số trường hợp "gửi message cho hệ khác xử lý" không cần
tức thời tới mức đó, và outbox pattern tồn tại chính xác để tránh publish "ảo" khi transaction
rollback (xem `docs/architecture/08 §3.1`). Nếu THẬT SỰ cần đồng bộ/tức thời, đó là 1 use case khác
hẳn (gọi thẳng API HTTP, không phải message queue) — không thuộc phạm vi hướng dẫn này.
