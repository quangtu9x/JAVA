# Plan — Nâng `td-woker` từ Worker thành Backend (Web API + PDF)

> Context: docs/17-checklist-tich-hop-kyso-simpki.md + docs/18-trien-khai-test-simpki.md
> Người dùng muốn nâng `td-woker` (hiện tại chỉ xử lý message qua RabbitMQ/Kafka)
> thành Backend có Web API + xử lý PDF — KHÔNG sửa cấu trúc cũ, KHÔNG hỏng worker đang chạy.
> Không code — chỉ lập kế hoạch + viết tài liệu checklist.

---

## 1. Đánh giá khả thi

**Có được không? — CÓ**, với điều kiện tuân thủ nguyên tắc Extensible Worker (docs/promt-Extensible-Worker-Architecture.txt): thêm mới hoàn toàn, KHÔNG sửa file đang có.

Cơ sở:
- `pom.xml` đã có `spring-boot-starter-web` (dùng cho actuator + debug endpoint) → Spring MVC sẵn sàng, KHÔNG cần dependency mới cho Web API.
- `spring-boot-starter-web` cung cấp `spring-webmvc` → có thể tạo `@RestController` mới trong package mới.
- Extensible Worker pattern: mỗi module mới = 1 package `@Component` mới, KHÔNG sửa Core → áp dụng cho API + PDF.
- `spring-boot-starter-web` đã kéo `tomcat-embed` → nhúng server HTTP sẵn, KHÔNG cần thêm server.
- Hiện tại Worker chạy port 8090 (actuator) → có thể expose thêm REST API cùng port hoặc port riêng.

**Rủi ro chính**: (1) Xung đột dependency PDF library với existing jars, (2) Multi-tenant context propagation trong HTTP (khác message context), (3) Thread model khác nhau (đồng bộ HTTP vs bất đồng bộ message), (4) Cổng port quản trị bị expose nếu không cẩn thận.

---

## 2. Quyết định kiến trúc then chốt

| Quyết định | Đề xuất | Lý do |
|---|---|---|
| Package Web API | `com.td.worker.api` (REST controllers) | Tách biệt hoàn toàn khỏi `transport/`, `tasks/`, `core/` — giống pattern `SimPkiProperties`/`HttpDebugTriggerProperties` |
| Package PDF | `com.td.worker.pdf` (service + handler) | Có thể vừa là `TaskHandler` (qua queue) vừa là service gọi trực tiếp qua API |
| Port API | Cùng port 8090 hoặc port riêng (env `WORKER_API_PORT`) | Cùng port: đơn giản, cần `management.port` tách riêng. Port riêng: an toàn hơn, cần thêm config |
| Auth API | API Key header (`X-API-Key`) hoặc Basic Auth | Worker không có Security Filter chain hiện tại, không kéo spring-security mới |
| Library PDF | Apache PDFBox (Apache 2.0 license) hoặc OpenPDF (LGPL/MPL) | PDFBox: mạnh, phổ biến, license Apache an toàn. OpenPDF: nhẹ hơn, fork của iText 5 (tránh AGPL iText 7) |
| PDF làm TaskHandler? | CÓ — `pdf.*` taskType theo pattern Extensible Worker | API gọi → publish envelope → handler xử lý → consistent với kiến trúc hiện tại. API đồng bộ cho trường hợp cần wait |

---

## 3. Danh sách task (thứ tự phụ thuộc)

### Phase 1 — Nền tảng (không ảnh hưởng anything đang chạy)

1. **Tạo `com.td.worker.api` package** — REST controllers, DTOs, exception handler
2. **Tạo `com.td.worker.pdf` package** — PDF service interface + implementation
3. **Thêm dependency PDF library** vào `pom.xml` (PDFBox hoặc OpenPDF) — additive only
4. **Cấu hình API** — `ApiProperties` (@ConfigurationProperties, prefix `worker.api`), env variables, `.env.example`
5. **Tạo `docs/19-...` tài liệu** — tài liệu mới này

### Phase 2 — Web API endpoints

6. **Endpoint submit task** (`POST /api/v1/tasks`) — submit taskType qua HTTP, return taskId/messageId
7. **Endpoint query status** (`GET /api/v1/tasks/{taskId}` hoặc `GET /api/v1/tasks/{messageId}/units/{unitId}`) — check checkpoint status
8. **Endpoint health/metrics mở rộng** — beyond actuator, business-level health
9. **Endpoint management** — config snapshot, handler registry, queue stats (non-sensitive)
10. **Authentication middleware** — API key validation (filter/interceptor)

### Phase 3 — PDF capabilities

11. **PDF generation** — từ HTML/template → PDF (Apache PDFBox + Flying Saucer hoặc template engine)
12. **PDF manipulation** — merge, split, watermark, page extraction
13. **PDF signing** — tích hợp với SIM-PKI (ký số trên PDF kết quả)
14. **PDF extraction** — trích xuất text/data từ PDF
15. **File storage integration** — MinIO upload/download cho PDF files

### Phase 4 — Tích hợp & Validation

16. **Multi-tenant resolution từ HTTP** — tenantId từ header/URL/JWT, map sang `TenantDataSourceManager`
17. **Integration test** — API + PDF end-to-end (H2/Testcontainers)
18. **Deploy script** — build/run script mới (giống `deploy/simpki/` pattern)
19. **Documentation update** — `docs/README.md` mục lục (CHỈ thêm dòng, không sửa nội dung khác)

---

## 4. Khó khăn & vướng mắc (không có câu trả lời đơn giản)

### 4.1 Multi-tenant trong HTTP context
- **Vấn đề**: Worker hiện tại giải quyết tenant qua `WorkerTenantContext.runAs(tenantId)` trong message handler (tenantId từ envelope). HTTP request KHÔNG có envelope → phải có cơ chế riêng để xác định tenant.
- **Giải pháp đề xuất**: Header `X-Tenant-Id` hoặc path variable `/api/v1/tenants/{tenantId}/...`. Cần `TenantDataSourceManager.getDataSource(tenantId)` giống handler.
- **Rủi ro**: Nếu quên set tenant context → ghi nhầm DB → thảm họa.

### 4.2 Thread model & timeout
- **Vấn đề**: HTTP request synchronous (Tomcat thread chờ response). Task dài (PDF generation 30s+, ký số 300s) sẽ block Tomcat thread pool (mặc định 200 threads).
- **Giải pháp**: (a) Async processing — accept task → return 202 + location header → client poll status endpoint. (b) Tăng Tomcat max-threads. (c) Separate Tomcat instance cho API.
- **Rủi ro**: Nếu đồng bộ + task chậm → Tomathon cạn thread → toàn bộ API (kể cả actuator) không responsive.

### 4.3 Dependency conflict PDF library
- **Vấn đề**: PDF processing libraries kéo transitive dependencies (xml-apis, fonts, imageIO, commons-...) có thể xung đột với existing dependencies (Castor, Axis, HttpClient).
- **Giải pháp**: (a) Dùng `mvn dependency:tree` kiểm tra sau khi thêm. (b) Exclude transitive không cần. (c) Dependency management version rõ ràng.
- **Rủi ro**: Xung đột classpath → runtime errors ở existing handlers (vbdi/vbden/simpki).

### 4.4 Expose port quản trị
- **Vấn đề**: Mở REST API trên cùng port với actuator (8090) → risk expose thông tin nhạy cảm (metrics, health, config).
- **Giải pháp**: (a) API endpoint prefix `/api/v1/` rõ ràng, tách biệt `/actuator`. (b) Auth yêu cầu cho `/api/**`. (c) Cân nhắc port riêng (`server.port` khác, management port giữ 8090).
- **Rủi ro**: Info disclosure nếu không có auth.

### 4.5 File handling (PDF) trong Worker
- **Vấn đề**: Worker philosophy là "message nhẹ" — KHÔNG tải file về máy. PDF processing cần file input/output.
- **Giải pháp**: (a) Envelope chứa objectKey (MinIO) thay vì file bytes. (b) Handler tải từ MinIO, xử lý, upload lại MinIO. (c) Streaming cho file lớn.
- **Rủi ro**: Memory OOM nếu load toàn bộ PDF vào RAM (PDFBox `PDDocument` load toàn bộ).

### 4.6 PDF Library license
- **Vấn đề**: iText 7 = AGPL (phải open-source code nếu dùng), PDFBox = Apache 2.0 (safe), OpenPDF = LGPL/MPL (cần caution).
- **Giải pháp**: PDFBox (Apache 2.0) — an toàn nhất cho commercial project.
- **Rủi ro**: Legal nếu chọn sai license.

### 4.7 Idempotency cho API submit
- **Vấn đề**: REST API submit có thể bị retry (network timeout) → duplicate task. Khác message queue (broker dedup).
- **Giải pháp**: Client-supplied `idempotencyKey` → check trước khi create → return existing nếu duplicate.
- **Rủi ro**: Duplicate processing nếu không handle.

---

## 5. Rollout (triển khai theo giai đoạn)

| Giai đoạn | Mục đích | Triển khai |
|---|---|---|
| **Phase 0** | Thêm dependency + package skeleton + config | Build mới, KHÔNG chạy jar cũ → verify `mvn test` vẫn pass, 0 thay đổi behavior |
| **Phase 1** | API endpoints (submit/query) không có auth (dev only) | Dev instance mới, test nội bộ, KHÔNG bật production |
| **Phase 2** | Auth middleware + multi-tenant resolution | Test với tenant thật, verify không ghi nhầm DB |
| **Phase 3** | PDF capabilities (generation + manipulation) | Test với file PDF thật, verify memory/performance |
| **Phase 4** | Integration + deploy script + docs | Production rollout với blue/green hoặc new instance |

**Nguyên tắc**: Mỗi phase phải build thành công + `mvn test -DskipITs` pass + KHÔNG ảnh hưởng bất kỳ worker nào đang chạy.

---

## 6. Open questions (cần người dùng quyết định)

1. **Cùng port hay port riêng cho API?** — Cùng port 8090 (đơn giản) hay `WORKER_API_PORT` riêng (an toàn)?
2. **Auth method?** — API Key đơn giản, hay JWT (phức tạp hơn, có expiry/refresh)?
3. **PDF scope?** — Chỉ generate PDF, hay cả merge/sign/extract? Ảnh hưởng đến dependency và effort.
4. **PDF tích hợp với SIM-PKI?** — Ký số trên PDF có phải mục tiêu không?
5. **Worker hiện tại có cần đổi instance không?** — Phase 0-1 chạy cùng instance hay instance riêng?

---

## 7. Validation

- `mvn test -DskipITs` → 68 test cũ + test mới, 0 fail
- `mvn package -DskipTests` → BUILD SUCCESS
- API endpoint smoke test: `POST /api/v1/tasks` → 202 + taskId; `GET /api/v1/tasks/{id}` → status
- PDF generation test: input HTML → output PDF (validate PDF header `%PDF-`)
- Multi-tenant test: request với tenantId A → chỉ truy cập DB tenant A (verify via logs)
- No regression: tất cả handler cũ (vbdi/vbden/guinhanquocgia/simpki) vẫn hoạt động bình thường

---

*Plan file — không phải tài liệu checklist chi tiết. Xem `docs/19-backend-webapi-pdf-checklist.md` để có checklist chi tiết nhất.*
