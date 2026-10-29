# 07 — Idempotency / Checkpoint / Retry / DLQ

Tài liệu này là phần QUAN TRỌNG NHẤT — đúng trọng tâm docs/promt.txt mục "Message gồm nhiều Unit".

## 1. Idempotency persistent (KHÔNG dựa vào memory/cache)

- Bảng `worker_units` với **unique index** trên `unique_key = tenantId|messageId|unitId`.
- `initializeUnits` dùng `INSERT ... WHERE NOT EXISTS` → redelivery/2 Worker không tạo trùng.
- Trạng thái persistent trong PostgreSQL → crash/restart/redelivery không nhân bản dữ liệu.

## 2. Trạng thái unit (persistent)

| State | Ý nghĩa |
|---|---|
| `PENDING` | chưa được claim |
| `PROCESSING` | đang do 1 Worker xử lý (có lease) |
| `DONE` | hoàn tất — không bao giờ chạy lại |
| `RETRY` | lỗi tạm thời, lên lịch `next_retry_at` |
| `FAILED` | lỗi sau khi vượt maxAttempts — vận hành gửi lại thủ công |
| `WAITING_DEPENDENCY` | handler/dependency phía ngoài CHƯA sẵn sàng (vd chưa có `TaskHandler`, hoặc nghiệp vụ tự biết chưa thể hoàn tất thật — xem VBDI `external_unit` [11 §3](11-vbdi-reference.md)); tự kiểm tra lại theo `next_retry_at`, KHÔNG tiêu `maxAttempts` (`defer()` giảm lại `attempts` đã tăng khi claim) — xem [14-load-resilience.md](14-load-resilience.md) |

Chu trình: `PENDING → PROCESSING → DONE | RETRY → PROCESSING → ... → FAILED`.
Nhánh chờ dependency: `PENDING/PROCESSING → WAITING_DEPENDENCY → PROCESSING → ...` (lặp lại vô
thời hạn cho tới khi handler/dependency sẵn sàng — không rơi vào `FAILED`).

## 3. Claim atomic + lease (xử lý race khi nhiều Worker cùng nhận 1 unit)

```sql
UPDATE worker_units SET status='PROCESSING', claimed_by=?,
       lease_expires_at = CURRENT_TIMESTAMP + CAST(? AS INTEGER) * INTERVAL '1' SECOND,
       attempts=attempts+1, ...
WHERE unique_key=? AND (
    (status IN ('PENDING','RETRY','WAITING_DEPENDENCY') AND (next_retry_at IS NULL OR next_retry_at <= NOW()))
    OR (status='PROCESSING' AND lease_expires_at < NOW())
)
```

- Chỉ 1 Worker thắng `UPDATE` (điều kiện cạnh tranh DB).
- Lease hết hạn → Worker khác claim lại (crash recovery).
- Settlement (`complete`/`fail`/`defer`) có điều kiện `claimed_by = worker` — Worker khác không can
  thiệp; kết quả `false` (đã mất claim) được log cảnh báo, không tính DONE.

**Cập nhật 23/10/2026 (chạy nhiều máy chủ):**

- Mọi mốc lease/stale tính bằng **đồng hồ DB** (`CURRENT_TIMESTAMP ± n giây`), không dùng
  `Instant.now()` của JVM nữa — máy chủ lệch giờ không làm lease hết sớm.
- **Heartbeat**: `UnitDispatcher` gia hạn lease các unit đang chạy mỗi `lease-renew-interval` (mặc
  định lease/3) qua `CheckpointStore.renewLeases` — lease chỉ hết khi tiến trình chết thật.
- **Timeout**: watchdog 1s/lần interrupt thread khi quá `unit-timeout` (áp cho cả đường RetryPoller,
  trước đây không có timeout); unit GIỮ `PROCESSING` tới khi handler thoát mới settle. Trước đây
  `MessageFlowRunner` cancel Future + trả RETRY ngay trong khi thread vẫn chạy ⇒ máy khác chạy song
  song. Quá `max-processing-time` (mặc định 15m) thì ngừng gia hạn để máy khác nhận lại.
- Hệ quả với ACK: unit quá thời gian chờ của message giờ còn `PROCESSING` (không phải `RETRY`) —
  `ALL_ACCOUNTED` vẫn ACK như cũ.

### 3.1 Handler phải idempotent

Claim/lease bảo đảm không 2 máy chạy 1 unit CÙNG LÚC, nhưng vẫn có cửa sổ chạy LẠI (crash sau commit
nghiệp vụ, trước `complete`). Mọi handler ghi DB phải chịu được chạy lại:

| Handler | Cách |
|---|---|
| `vbdi.*` | `ON CONFLICT` trên bảng dedup trong cùng giao dịch |
| `vbden.forward_unit` | id bản sao/`file_metadata`/reply unitId tất định (`VbdenTaskConstants.unitScopedId`, `UUID.nameUUIDFromBytes(taskType|purpose|tenant|unitId)`) + `ON CONFLICT (id) DO NOTHING` |
| `vbden.forward_unit.reply` | `FOR UPDATE` văn bản gốc; bản sao đã có trong `forwarded_units` (khớp `documentId`) ⇒ bỏ qua |
| `vbden.forward_unit.status` / `.revoke` | id dòng `vbden_res_documents` tất định + `ON CONFLICT`; revoke thấy dòng lịch sử đã có ⇒ bỏ qua cả UPDATE |

unitId do td-web sinh 1 lần và lưu trong outbox nên giữ nguyên qua mọi lần giao lại; mỗi thao tác
nghiệp vụ MỚI có unitId mới ⇒ vẫn tạo bản ghi mới như trước (không đổi nghiệp vụ).

## 4. Retry THEO UNIT (không retry mù toàn bộ message)

- Mỗi unit tự lên lịch `next_retry_at` theo **exponential backoff + jitter**:
  `initial * multiplier^(attempts-1)`, cap `max-backoff`, jitter 0-10%.
- `RetryPoller` định kỳ:
  1. `findDueRetries` → unit `RETRY` tới hạn → claim + xử lý lại.
  2. `requeueExpiredLeases` → `PROCESSING` lease hết hạn → `RETRY`.
  3. `requeueStalePending` → `PENDING` quá lâu → `RETRY`.
- Unit A lỗi không kéo unit B/C chạy lại — chỉ A retry.

## 5. Kiểm chứng các tình huống lỗi (đã có unit test)

| Tình huống | Test trong `JdbcCheckpointStoreTest` / `MessageFlowRunnerTest` |
|---|---|
| Redelivery/cùng message | `initializeUnitsIsIdempotent`; `multiUnitFlow...RedeliveryCompletesOnlyRemainingUnit` |
| 2 Worker claim cùng unit | `claimIsExclusiveBetweenWorkers` |
| Worker crash → lease hết hạn | `expiredLeaseCanBeReclaimed`, `requeueExpiredLeasesMovesProcessingToRetry` |
| Retry rồi FAILED sau maxAttempts | `failTransitionsToRetryThenFailedAfterMaxAttempts` |
| Settlement chỉ Worker giữ claim | `completeIsOwnedByClaimingWorkerOnly` |
| Heartbeat gia hạn lease / báo mất claim | `renewLeasesKeepsClaimAliveAndReportsLostKeys`; `UnitDispatcherTest.longRunningHandlerKeepsLeaseSoOtherWorkerCannotClaim` |
| Timeout interrupt nhưng giữ claim tới khi handler thoát | `UnitDispatcherTest.unitTimeoutInterruptsHandlerButKeepsClaimUntilHandlerExits` |
| Stale PENDING theo đồng hồ DB | `requeueStalePendingUsesDatabaseClock` |
| Instance ID duy nhất mỗi tiến trình | `UnitDispatcherTest.instanceIdIsUniquePerProcessAndStable` |
| vbden chạy lại không sinh trùng / reply đồng thời không mất cập nhật | `VbdenForwardUnit*IntegrationTest` (`rerunOfSameUnit_*`, `duplicateReplyForSameCopy_isIdempotent`, `concurrentRepliesForDifferentTargets_doNotLoseUpdates`) — Testcontainers, chạy ở CI |
| Redelivery không chạy lại unit DONE | `multiUnitFlow...RedeliveryCompletesOnlyRemainingUnit` (u2 chạy đúng 1 lần) |
| `defer()` → `WAITING_DEPENDENCY`, không tiêu `maxAttempts`, tự claim lại khi tới hạn | `deferMovesProcessingToWaitingDependencyWithoutConsumingAttempt`, `findDueRetriesIncludesWaitingDependency` |
| VBDI `external_unit`/`contact_unit`/mailin trả `WAITING_DEPENDENCY` chứ không `success()` giả | `VbdiDeliveryTaskHandlerTest.externalUnitReturnsWaitingDependencyNotSuccess` + các test liên quan |

## 6. DLQ (broker) vs FAILED (checkpoint)

- **Broker DLQ** (queue/topic `.dlq`): envelope không parse được hoặc lỗi hạ tầng tái diễn —
  vận chuyển sai/sữa producer/cấu hình.
- **`FAILED` checkpoint**: unit xử lý không thành công sau N lần thử — nghiệp vụ lỗi/vận hành
  sửa dữ liệu rồi `gửi lại` message (hoặc reset trạng thái exact).

Cả 2 đều có thể truy vấn để vận hành (xem [12-observability.md](12-observability.md) + `countByStatus`).

Tài liệu tiếp: [08-connections.md](08-connections.md)