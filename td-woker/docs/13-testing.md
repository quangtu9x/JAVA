# 13 — Testing & kiểm chứng

## 1. Test hiện có (H2 PostgreSQL mode, broker off)

| Class | Kiểm chứng |
|---|---|
| `EnvelopeParserTest` | validate envelope: hợp lệ multi-unit; thiếu messageId/tenantId; trùng unitId; units rỗng; không phải JSON |
| `JdbcCheckpointStoreTest` | idempotency init; claim độc quyền 2 worker; lease hết hạn reclaimed; retry→FAILED sau maxAttempts; requeue expired lease; complete chỉ worker giữ claim; countByStatus |
| `MessageFlowRunnerTest` | multi-unit: 1 DONE + 1 RETRY; redelivery KHÔNG chạy lại unit DONE; ACK policy ALL_TERMINAL |
| `WorkerApplicationContextTest` | toàn bộ Spring context boot (H2, broker off) + registry có đủ handler (sample/vbdi.delivery/vbdi.reply) |

## 2. Ma trận kiểm chứng theo docs/promt.txt "Tự kiểm tra các tình huống"

| Tình huống | Cơ chế | Đã test |
|---|---|---|
| Crash giữa chừng | lease hết hạn → claim lại | ✅ `expiredLeaseCanBeReclaimed` |
| Timeout unit | fail-in-place → RETRY/FAILED | ✅ code path + policy test |
| Retry | per-unit exponential backoff | ✅ `failTransitionsToRetryThenFailedAfterMaxAttempts` |
| Redelivery | init idempotent + skip DONE | ✅ `multiUnitFlow...` |
| Duplicate | uniqueKey unique index | ✅ `initializeUnitsIsIdempotent` |
| Concurrent Worker | claim UPDATE atomic + settlement owner | ✅ `claimIsExclusiveBetweenWorkers` |
| Partial failure | unit độc lập, ACK policy | ✅ `multiUnitFlow...`, `allTerminalAckPolicy...` |

## 3. Chạy test

```powershell
mvn test          # toàn bộ
mvn -pl . -Dtest=MessageFlowRunnerTest test   # 1 test cụ thể
```

## 4. Hạn chế & hướng mở rộng

- Chạy H2 (PostgreSQL mode) cho đơn vị; build thật vẫn cần PG reachable cho schema `migration-worker`
  (Flyway) khi chạy production.
- Chưa có test broker thật (Rabbit/Kafka) — nên bổ sung integration test với Testcontainers
  khi môi trường có Docker-Linux (khớp cách project tham khảo tự skip Testcontainers cục bộ).
- Chưa có test MinIO thật (MinIO container qua Testcontainers) — `objectExists` cần verify
  với MinIO thật trước khi bật `verify-object-exists`.