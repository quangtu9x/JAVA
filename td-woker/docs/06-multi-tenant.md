# 06 — Multi-tenant (database-per-tenant)

## 1. Mô hình (kế thừa docs/architecture/06 của project tham khảo)

```
Control-plane DB (tdwebapi_controlplane)      ← Worker CHỈ ĐỌC bảng tenants
   bảng tenants: tenant_id, host, port, database_name,
                 username, encrypted_password (AES 2 chiều), active
        │  (refresh định kỳ — hot-add tenant từ td-web)
        ▼
Worker: TenantRegistry (danh sách tenant active)
        ▼
TenantDataSourceManager.getDataSource(tenantId)
        → Hikari pool tạo LƯỜI tới database RIÊNG của tenant
          (có thể khác host/port/server hoàn toàn)
```

- Mỗi tenant = 1 database Postgres vật lý riêng.
- Worker **không migrate** database tenant — `td-web` là người duy nhất migrate
  (docs/architecture/11 §13.4). Worker chỉ tiêu thụ schema đã sẵn sàng.

## 2. Luồng chọn tenant cho 1 unit

1. Envelope unit có `tenantId`.
2. `UnitDispatcher.runUnit` chạy handler TRONG `WorkerTenantContext.runAs(tenantId)`.
3. Handler cần DB → `TenantDataSourceManager.getDataSource(tenantId)` trả pool đúng tenant
   (hoặc empty nếu tenant không active/không tồn tại trong registry → unit FAILED rõ ràng).

## 3. TenantCredentialCipher — bắt buộc CÙNG key/salt với td-web

Mật khẩu Postgres từng tenant được mã hoá AES/CBC bằng `Encryptors.text(key, salt)`:
`TENANT_CREDENTIAL_ENCRYPTION_KEY` + `TENANT_CREDENTIAL_ENCRYPTION_SALT`.
**PHẢI cùng giá trị với `td-web`** — khác giá trị = Worker không giải mã được → không mở được
pool tenant (docs/architecture/11 §13.3).

## 4. Pool/tenant

- Tạo lười (chỉ khi unit đầu tiên của tenant được xử lý).
- Giới hạn số pool tối đa (`worker.tenant.max-pools`, mặc định 50) + evict LRU theo lần truy cập.
- Mỗi pool: `pool-minimum-idle`, `pool-maximum-size`, `pool-connection-timeout-ms`,
  `pool-idle-timeout-ms`, `pool-max-lifetime-ms`, `pool-auto-commit`.
- `stringtype=unspecified` (đúng quyết định BL-101 của project tham khảo — bind String vào JSONB).

## 5. Registry refresh (hot-add tenant)

`TenantRegistry.refresh()` định kỳ (`worker.control-plane.refresh-interval`) đọc lại bảng
`tenants` — tenant mới tạo qua API `td-web` được Worker biết sau tối đa 1 chu kỳ, KHÔNG restart.

## 6. Trường hợp control-plane tạm mất

- Worker vẫn khởi động/phục vụ (đúng docs/architecture/11 §13.5 "chịu được lỗi kết nối tạm thời").
- Unit cần tenant không có pool → FAILED với lỗi rõ ràng; registry refresh lại khi control-plane
  trở lại.
- `/actuator/health` báo trạng thái qua `TenantRegistryHealthIndicator`.

Tài liệu tiếp: [07-idempotency-checkpoint.md](07-idempotency-checkpoint.md)