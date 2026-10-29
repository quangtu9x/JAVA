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

- Mỗi tiến trình Worker tự có định danh DUY NHẤT `<WORKER_INSTANCE_ID hoặc hostname>-<8 ký tự ngẫu
  nhiên>` (dùng làm `claimed_by`, owner lease GC) — từ 23/10/2026 copy cùng `WORKER_INSTANCE_ID` sang
  nhiều máy/replica cũng KHÔNG đụng nhau.
- N Worker cùng consume 1 Rabbit queue / cùng Kafka group → load balance.
- Chạy tối thiểu 2 instance (đúng khuyến nghị docs/architecture/11 §13.7).

## 2. Vì sao an toàn khi chạy nhiều instance

| Cơ chế | Chống gì |
|---|---|
| `INSERT ... WHERE NOT EXISTS` (uniqueKey `tenantId|messageId|unitId`) | Redelivery / 2 Worker cùng nhận 1 message → không tạo trùng unit |
| `UPDATE ... WHERE status IN (...)` atomic | 2 Worker claim cùng 1 unit → chỉ 1 thắng |
| Lease (`lease_expires_at`) tính theo **đồng hồ DB** | Worker A crash → hết hạn → B claim lại, không chờ vô hạn; máy chủ lệch giờ không làm lease hết sớm |
| **Heartbeat gia hạn lease** (mỗi `lease-renew-interval`, mặc định lease/3) | Handler chạy lâu hơn lease KHÔNG bị máy khác giành giữa chừng |
| **Watchdog timeout** trong `UnitDispatcher` (mọi đường: broker + RetryPoller) | Quá `unit-timeout` → interrupt thread nhưng GIỮ claim tới khi handler thực sự thoát; quá `max-processing-time` (treo, bỏ qua interrupt) → ngừng gia hạn để máy khác nhận lại |
| `complete`/`fail`/`defer` có điều kiện `claimed_by = worker` + **kiểm tra kết quả** | Worker khác không thể đổi trạng thái unit không phải của mình; mất claim thì log cảnh báo, không báo DONE giả |
| Handler idempotent (xem §5) | Lỡ chạy lại 1 unit (crash sau commit, trước `complete`) không sinh dữ liệu trùng |

## 3. Crash recovery (kịch bản)

1. Worker A nhận message M (2 unit), claim u1, đang xử lý thì A bị kill.
2. u1 ở trạng thái `PROCESSING` với lease hết hạn; u2 `PENDING`.
3. Rabbit redeliver M (unacked) → Worker B nhận; `initializeUnits` bỏ qua u1/u2 đã tồn tại;
   dispatch → u1 (lease hết hạn → claim lại được), u2 (PENDING → claim).
4. Worker B tiếp tục từ đúng phần dở dang — không xử lý lại gì đã DONE.

`RetryPoller.requeueExpiredLeases` + `requeueStalePending` là lưới an toàn thứ 2 nếu broker
không redeliver kịp.

## 4. Lưu ý vận hành

- `WORKER_INSTANCE_ID` chỉ là TIỀN TỐ dễ đọc (trống = hostname); không cần đặt khác nhau nữa. Hệ quả
  nhỏ: sau khi 1 máy chết giữa lượt GC tệp dùng chung, máy khởi động lại phải chờ lease GC (2h) hết
  hạn mới chạy GC tiếp (trước đây cùng ID thì nhận lại ngay).
- Mỗi máy tự tạo pool riêng: tải kết nối ≈ N × (5 pool + 1 LISTEN) trên MỖI DB tenant và N × 10 trên
  DB worker — kiểm tra `max_connections` PostgreSQL khi tăng số máy.
- Giới hạn `max-inflight-units`, rate limit Rabbit… tính THEO TỪNG instance (N máy = N lần).
- Mỗi instance chạy ở thư mục riêng (`logs/`, `java.pid` là đường dẫn tương đối).
- Nâng cấp cuốn chiếu: giữ `WORKER_SEARCH_SYNC_MAPPING_VERSION` GIỐNG NHAU trên mọi máy (khác nhau thì
  các máy tranh nhau trỏ alias và reindex toàn bộ).
- Tất cả instance trỏ CÙNG database checkpoint (`WORKER_DB_URL`) và CÙNG broker.
- Key/salt giải mã tenant phải CÙNG `td-web` (docs/architecture/11 §13.3).
- Mở firewall/pg_hba từ MỌI máy Worker tới control-plane + từng DB tenant
  (docs/architecture/11 §13.2).

## 5. Đánh giá theo loại việc (rà soát 23/10/2026)

| Việc | Cơ chế chống trùng | Kết luận |
|---|---|---|
| Rabbit/Kafka → `worker_units` (claim/settle) | claim atomic + heartbeat lease + watchdog | An toàn |
| `vbdi.delivery` / `vbdi.reply` / `vbdi.revoke` | `ON CONFLICT` dedup trong cùng giao dịch tenant | An toàn |
| `vbden.forward_unit` | id bản sao / `file_metadata` / reply unitId **tất định** theo `tenant + unitId`, `INSERT … ON CONFLICT (id) DO NOTHING` | An toàn (sửa 23/10 — trước đây chạy lại sinh **2 bản sao**) |
| `vbden.forward_unit.reply` | `SELECT … FOR UPDATE` văn bản gốc + bỏ qua nếu bản sao đã có trong `forwarded_units` | An toàn (sửa 23/10 — trước đây 2 reply đồng thời **mất cập nhật** `unit_process`/`forwarded_units`, chạy lại nhân đôi dòng "Đã đến") |
| `vbden.forward_unit.status` / `.revoke` | id dòng lịch sử tất định + `ON CONFLICT`; revoke chạy lại bỏ qua toàn bộ | An toàn (sửa 23/10 — trước đây nhân đôi dòng lịch sử) |
| Đồng bộ Elasticsearch (`search_index_queue`) | `FOR UPDATE SKIP LOCKED` + version `external_gte` + advisory lock bootstrap/reconcile | An toàn |
| Bóc tách nội dung tệp | `SKIP LOCKED` + lease 15 phút + upsert | An toàn (tệp rất lâu có thể bị bóc 2 lần — chỉ tốn CPU) |
| GC tệp dùng chung | lease 1 dòng trong DB + đánh dấu có điều kiện trước khi xoá | An toàn |
| TD Gateway gửi / `simpki.sign` | KHÔNG còn trong td-woker (đã chuyển `tdgateway`) | Xem tài liệu tdgateway |

Giới hạn còn lại (chấp nhận): ghi `forwarded_units` ở td-web (`PublishForwardUnitRevokeUseCase`) không
khoá dòng — FOR UPDATE phía worker chỉ tuần tự hoá các reply với nhau. Unit treo quá
`max-processing-time` mà bỏ qua interrupt vẫn có thể bị máy khác chạy lại — nhờ handler idempotent nên
không sinh dữ liệu trùng.

Tài liệu tiếp: [06-multi-tenant.md](06-multi-tenant.md)