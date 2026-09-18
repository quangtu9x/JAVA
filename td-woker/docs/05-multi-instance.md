# 05 — Multi-instance / Multi-machine

## 1. Mô hình

```
                  ┌─────────────┐
                  │  PostgreSQL │   ← checkpoint store (worker_units) — nguồn sự thật
                  │ (tdworker)  │
                  └─────────────┘
                        ▲ shared
        ┌───────────────┴───────────────┐
        │                               │
┌───────┴────────┐               ┌──────┴────────┐
│ Worker A       │               │ Worker B      │
│ (machine 1)    │               │ (machine 2)   │
│ Rabbit consumer│               │ Rabbit consumer│
│ + retry poller │               │ + retry poller │
└───────┬────────┘               └──────┬────────┘
        │                               │
        └───────── RabbitMQ broker ─────┘   (cùng queue → competing consumers)
```

- Mỗi Worker có `WORKER_INSTANCE_ID` riêng (dùng làm `claimed_by`).
- N Worker cùng consume 1 Rabbit queue / cùng Kafka group → load balance.
- Chạy tối thiểu 2 instance (đúng khuyến nghị docs/architecture/11 §13.7).

## 2. Vì sao an toàn khi chạy nhiều instance

| Cơ chế | Chống gì |
|---|---|
| `INSERT ... WHERE NOT EXISTS` (uniqueKey `tenantId|messageId|unitId`) | Redelivery / 2 Worker cùng nhận 1 message → không tạo trùng unit |
| `UPDATE ... WHERE status IN (...)` atomic | 2 Worker claim cùng 1 unit → chỉ 1 thắng |
| Lease (`lease_expires_at`) | Worker A crash/timeout → hết hạn → B claim lại, không chờ vô hạn |
| `complete`/`fail` có điều kiện `claimed_by = worker` | Worker khác không thể đổi trạng thái unit không phải của mình |

## 3. Crash recovery (kịch bản)

1. Worker A nhận message M (2 unit), claim u1, đang xử lý thì A bị kill.
2. u1 ở trạng thái `PROCESSING` với lease hết hạn; u2 `PENDING`.
3. Rabbit redeliver M (unacked) → Worker B nhận; `initializeUnits` bỏ qua u1/u2 đã tồn tại;
   dispatch → u1 (lease hết hạn → claim lại được), u2 (PENDING → claim).
4. Worker B tiếp tục từ đúng phần dở dang — không xử lý lại gì đã DONE.

`RetryPoller.requeueExpiredLeases` + `requeueStalePending` là lưới an toàn thứ 2 nếu broker
không redeliver kịp.

## 4. Lưu ý vận hành

- Các instance phải có `WORKER_INSTANCE_ID` KHÁC NHAU.
- Tất cả instance trỏ CÙNG database checkpoint (`WORKER_DB_URL`) và CÙNG broker.
- Key/salt giải mã tenant phải CÙNG `td-web` (docs/architecture/11 §13.3).
- Mở firewall/pg_hba từ MỌI máy Worker tới control-plane + từng DB tenant
  (docs/architecture/11 §13.2).

Tài liệu tiếp: [06-multi-tenant.md](06-multi-tenant.md)