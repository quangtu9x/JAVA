# 03 — Task/Handler Model

## 1. SPI

```java
public interface TaskHandler {
    String taskType();                                   // vd "vbdi.delivery"
    UnitResult execute(TaskContext context, JsonNode payload) throws Exception;
}
```

`TaskHandlerRegistry` (Spring `@Component`) tự thu thập mọi bean `TaskHandler` theo `taskType`.
Thêm 1 loại task mới (OCR, ES indexing, file processing, data sync, notification, background job...)
= thêm 1 class `implements TaskHandler` + đăng ký `taskType` duy nhất — **KHÔNG sửa Worker Core**.

## 2. TaskContext — các mã truy vết bắt buộc

```java
record TaskContext(
    String taskType, String messageId, String unitId, String correlationId,
    String tenantId, JsonNode payload, Map<String,String> target,
    int maxAttempts, Instant deadline, Map<String,String> headers) {}
```

Worker luôn log kèm `correlationId/messageId/tenantId/unitId` (xem [12-observability.md](12-observability.md)).

## 3. UnitResult

- `UnitResult.success()` → checkpoint `DONE`.
- `UnitResult.failure(msg)` hoặc handler ném exception → Worker tự quyết định
  **RETRY** (attempts < maxAttempts, lên lịch `next_retry_at` exponential backoff)
  hay **FAILED** (attempts >= maxAttempts).

## 4. Ví dụ: task "sample.echo"

```java
@Component
public class SampleEchoTaskHandler implements TaskHandler {
    @Override public String taskType() { return "sample.echo"; }
    @Override public UnitResult execute(TaskContext ctx, JsonNode payload) {
        log.info("echo {} {} {}", ctx.messageId(), ctx.unitId(), ctx.tenantId());
        return UnitResult.success();
    }
}
```

Producer publish envelope với `"taskType":"sample.echo"`, routing key `worker.sample.echo`
(RabbitMQ) hoặc topic `worker.tasks` (Kafka) là handler chạy tự động.

## 5. Lưu ý khi viết handler

- Handler nhận `tenantId` từ context; nếu cần ghi DB tenant dùng `TenantDataSourceManager.getDataSource(tenantId)`
  (tự set qua `WorkerTenantContext`).
- Payload là `JsonNode` — handler tự deserialize bằng `ObjectMapper` (vd `VbdiDeliveryPayload`).
- Handler phải **idempotent**: Worker có thể gọi lại nếu lease hết hạn trước khi settle.
- Nếu payload thiếu field bắt buộc → trả `UnitResult.failure` để qua retry/FAILED rõ ràng,
  không im lặng bỏ qua.
- Lỗi cấu hình vĩnh viễn (thiếu handler, tenant không tồn tại) → Worker `FAILED` ngay.

Tài liệu tiếp: [04-rabbitmq-kafka-flow.md](04-rabbitmq-kafka-flow.md)