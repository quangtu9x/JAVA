# 02 — Worker Core

## 1. Envelope (message gồm nhiều unit)

```json
{
  "messageId": "msg-1",
  "taskType": "vbdi.delivery",
  "correlationId": "corr-1",
  "source": "td-web",
  "createdAt": "2026-09-04T00:00:00Z",
  "units": [
    { "unitId": "u1", "tenantId": "H05", "payload": {...}, "target": {...} },
    { "unitId": "u2", "tenantId": "H06", "payload": {...} }
  ]
}
```

- **messageId**: idempotency cấp message — message trùng (redelivery) không insert thêm.
- **taskType**: quyết định handler nào xử lý.
- **units[]**: mỗi unit có thể thuộc **tenant/database/resource khác**; xử lý độc lập.

`EnvelopeParser` validate bắt buộc: `messageId`, `taskType`, `units[]` không rỗng,
mỗi unit phải có `unitId` + `tenantId`, không trùng `unitId`. Lỗi → `EnvelopeValidationException`
→ transport đưa message vào DLQ (không retry vô ích).

## 2. MessageFlowRunner

`MessageFlowRunner.run(envelope)`:

1. Normalize: gán `maxAttempts` mặc định (cấu hình `retries-per-unit`) nếu unit không khai báo.
2. `storeMessage` — bảng `worker_messages` (audit + tái dựng).
3. `initializeUnits` — bảng `worker_units`; idempotent theo uniqueKey `tenantId|messageId|unitId`.
4. Dispatch từng unit; nếu `UnitDispatcher` đang đầy (backpressure) thì unit bỏ lượt này,
   `RetryPoller` sẽ xử lý — không chặn thread broker.
5. Chờ hoàn tất; unit quá timeout bị `fail-in-place` (→ RETRY/FAILED), KHÔNG hủy các unit khác.
6. Tính `FlowResult.canAck` theo ACK policy.

### ACK policy

| Policy | Điều kiện ACK |
|---|---|
| `ALL_ACCOUNTED` (mặc định) | Không còn unit `PENDING` — RETRY/FAILED/DONE/PROCESSING(khác worker) đều "đã ghi nhận" |
| `ALL_TERMINAL` | Mọi unit về `DONE`/`FAILED` (không RETRY/PENDING/PROCESSING) |

Đúng tinh thần docs/promt.txt: "Chỉ ACK message theo chính sách hoàn tất được thiết kế;
không ACK đơn giản chỉ vì một phần unit đã xử lý thành công."

## 3. UnitDispatcher

- Bounded `ThreadPoolExecutor` (số thread = `max-inflight-units`) + **admission semaphore**.
- Claim là `UPDATE ... WHERE status IN (... )` atomic — chỉ 1 Worker (instance) thắng.
- Claim có **lease** (`claim-lease-seconds`); hết hạn → Worker khác/poller claim lại
  (crash recovery, không bao giờ chạy kép hợp lệ).
- Handler chạy trong `WorkerTenantContext.runAs(tenantId)` → đúng DB tenant thông qua
  `TenantDataSourceManager`.

## 4. Checkpoint store (bảng worker_units)

```
unique_key      tenantId|messageId|unitId   (unique index → idempotency persistent)
status          PENDING → PROCESSING → DONE | RETRY → PROCESSING ... → FAILED
attempts        tăng mỗi lần claim
next_retry_at   thời điểm retry (exponential backoff per-unit)
claimed_by      instance giữ lease
lease_expires_at
last_error      lỗi gần nhất
```

Trạng thái theo đúng yêu cầu docs/promt.txt: `PENDING`, `PROCESSING`, `DONE`, `FAILED`, `RETRY`.

Quy tắc:
- Unit `DONE` KHÔNG BAO GIỜ chạy lại (kể cả redelivery/restart).
- Worker A crash → lease hết hạn → Worker B claim và tiếp tục (không xử lý lại phần đã xong).
- FAILED là trạng thái "DLQ-equivalent" — có thể truy vấn để vận hành gửi lại thủ công.

Tài liệu tiếp: [03-task-handler-model.md](03-task-handler-model.md)