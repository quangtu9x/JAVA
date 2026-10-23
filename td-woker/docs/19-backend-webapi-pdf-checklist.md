# Checklist: Nâng td-woker thành Backend (Web API + PDF)

> Lưu ý hiện trạng: dịch vụ ký SIM-PKI đã được tách khỏi `td-woker`. Các mục SIM-PKI bên dưới chỉ là
> nội dung lịch sử của bản kế hoạch và không được triển khai lại trong worker; nếu cần ký số, backend
> phải tích hợp với dịch vụ ký độc lập.

> Mục tiêu: Biến td-woker từ sim worker (message-driven) thành backend có Web API để nhận yêu cầu HTTP và xử lý PDF, KHÔNG sửa cấu trúc hiện tại, KHÔNG hỏng worker đang chạy.
> Tài liệu tham chiếu: 17-checklist-tich-hop-kyso-simpki.md, 18-trien-khai-test-simpki.md, promt-Extensible-Worker-Architecture.txt
> Trạng thái: CHƯA BẮT ĐẦU — đang ở giai đoạn lập kế hoạch
> Lưu ý: Tài liệu này cần được chuyển vào `docs/19-backend-webapi-pdf-checklist.md` sau khi hoàn thiện.

---

## 0. Trạng thái hiện tại (Baseline)

### 0.1. Architecture hiện tại
- Spring Boot 3.2.0, Java 17, worker thuần (message queue: RabbitMQ/Kafka)
- Độc lập hoàn toàn khỏi td-web/td-application
- Extensibility pattern: mỗi module mới = 1 package mới @Component, KHÔNG sửa Worker Core
- Worker Core lắng nghe message queue, tạo Task, dispatch vào Handler tương ứng theo Topic

### 0.2. Dependency hiện tại
- spring-boot-starter-web ĐÃ CÓ trong pom.xml → Spring MVC sẵn sàng cho Web API
- Không cần dependency mới cho Web API cơ bản
- PDF processing: cần đánh giá library hiện tại (Apache PDFBox/iText/PDFClown) và giấy phép

### 0.3. Controller hiện tại
- Duy nhất: DebugTriggerController (/internal/debug/publish) — chỉ dev/test, mặc định tắt qua property
- Pattern: @RestController, @RequestMapping, inject Service qua Constructor

### 0.4. Configuration hiện tại
- Pattern @ConfigurationProperties riêng cho mỗi module (SimPkiProperties, GuiNhanQuocGiaProperties, HttpDebugTriggerProperties)
- KHÔNG gộp vào WorkerProperties chung
- .env.example + application.yml cho từng môi trường
- Đã có mẫu: simpki/application-simpki.yml, deploy/simpki/.env

### 0.5. Deploy hiện tại
- Script mẫu: deploy/simpki/ (copy jar, create folder, systemd service, nginx reverse proxy)
- Pattern: build jar → upload → restart service → nginx proxy đến port mới
- .env.example có sẵn

### 0.6. Testing hiện tại
- Integration tests theo mẫu SimPkiIntegrationTest
- Test profile @ActiveProfiles("test"), TestContainers cho RabbitMQ
- Mock Service bằng @MockBean

---

## 1. Khó khăn chính (Difficulty Assessment)

### 1.1. Multi-tenant HTTP Context
- Mức độ: Cao
- Mô tả: Worker xử lý nhiều Tenant. HTTP request cần phân biệt tenant qua header/path. Mỗi tenant có config riêng (API key, endpoint, PDF path...).
- Giải pháp đề xuất: Custom TenantInterceptor + ThreadLocal/RequestContextHolder lưu tenant context. Tương tự pattern đã dùng cho message-driven tenant routing.
- File liên quan (có thể): src/main/java/com/td/worker/infrastructure/tenant/ (cần tạo mới)
- Ưu tiên: Blocker — phải giải quyết trước khi viết API

### 1.2. Thread Model & Timeout
- Mức độ: Cao
- Mô tả: Message queue async (non-blocking). HTTP API sync (blocking). Cần thread pool riêng cho HTTP requests, không ảnh hưởng worker thread. Timeout xử lý PDF có thể lâu (phân trang, ký PDF...).
- Giải pháp đề xuất: @Async với thread pool cấu hình riêng, hoặc Callable/DeferredResult cho async response. Circuit breaker cho PDF processing timeout.
- File liên quan (có thể): src/main/java/com/td/worker/transport/http/config/
- Ưu tiên: Blocker — phải giải quyết trước khi viết API

### 1.3. Dependency Conflict (PDF Libraries)
- Mức độ: Trung bình
- Mô tả: PDF processing libraries (iText, PDFBox, PDFClown) có thể xung đột với existing dependencies. Cần kiểm tra transitive dependencies.
- Giải pháp đề xuất: mvn dependency:tree kiểm tra, exclusions trong pom.xml nếu cần, hoặc shaded jar cho PDF library.
- Ưu tiên: High — cần đánh giá sớm

### 1.4. Exposing HTTP Port
- Mức độ: Trung bình
- Mô tả: Worker hiện tại có thể không expose HTTP port (hoặc expose cho internal traffic). Cần mở port cho API, nhưng KHÔNG ảnh hưởng worker hiện tại.
- Giải pháp đề xuất: Configurable server.port riêng cho API, hoặc dùng server.servlet.context-path để tách biệt. Spring Boot cho phép cả hai cùng chạy.
- Ưu tiên: Medium

### 1.5. File Handling (PDF Upload/Download)
- Mức độ: Trung bình
- Mô tả: PDF upload (multipart request), download (stream response). Cần validate file type, size limit, temp storage, cleanup.
- Giải pháp đề xuất: MultipartFile cho upload, ResponseEntity<Resource> cho download, temp directory với cleanup hook.
- Ưu tiên: High — core functionality

### 1.6. PDF Processing License
- Mức độ: Trung bình
- Mô tả: iText 7 có giấy phép AGPL/Commercial. PDFBox Apache 2.0 (permissive). PDFClown tự do. Cần chọn library phù hợp license dự án.
- Giải pháp đề xuất: Đánh giá giấy phép hiện tại trong project. Nếu chưa có → chọn PDFBox (Apache 2.0) hoặc kiểm tra license hiện tại.
- Ưu tiên: High — legal blocker nếu dùng iText Commercial

### 1.7. API Idempotency & Security
- Mức độ: Trung bình
- Mô tả: API cần idempotent (tránh xử lý trùng). Cần auth (API Key, JWT, OAuth2?). Tenant isolation trong HTTP layer.
- Giải pháp đề xuất: Idempotency key header + DB check. API Key đơn giản cho nội bộ, JWT nếu external. Filter cho auth.
- Ưu tiên: High cho production, Medium cho MVP

---

## 2. Vướng mắc cần quyết định (Open Questions)

### 2.1. Cùng port hay port riêng cho Web API?
- Cùng port (server.port hiện tại): Đơn giản, ít infrastructure. API và worker cùng process, dùng server.servlet.context-path phân tách (ví dụ /api/* cho API, worker dùng message queue).
- Port riêng (ví dụ 8081): Tách biệt hoàn toàn, dễ quản lý, dễ scale. Cần thêm config port, nginx config riêng.
- Khuyến nghị: Cùng port + context-path (MVP nhanh). Port riêng nếu production cần scale độc lập.
- Status: ❓ Chưa quyết định — cần người dùng quyết định

### 2.2. Auth method?
- API Key (đơn giản, nội bộ): Header X-API-Key, validate trong Filter. Phù hợp cho internal microservice.
- JWT Token: Phức tạp hơn, support user session, external access. Cần JWT library, secret management.
- OAuth2: Phức tạp nhất, cần Authorization Server. Phù hợp cho external-facing API.
- Khuyến nghị: API Key cho nội bộ (phù hợp worker context). JWT nếu API expose external.
- Status: ❓ Chưa quyết định — cần người dùng quyết định

### 2.3. PDF scope?
- Chỉ tạo PDF: HTML → PDF conversion (iText/PDFBox HTML-to-PDF). Đơn giản nhất.
- Tạo + ký PDF: Thêm digital signature (SIM-PKI integration). Phức tạp hơn.
- Tạo + ký + lưu + download: Full pipeline. Phức tạp nhất.
- Khuyến nghị: Bắt đầu với "chỉ tạo PDF", tích hợp SIM-PKI sau.
- Status: ❓ Chưa quyết định — cần người dùng quyết định

### 2.4. Tích hợp SIM-PKI hay không?
- Có: Nếu backend cần ký PDF số. Kế thừa module simpki pattern, thêm API endpoint cho signing.
- Không: Nếu chỉ cần tạo PDF thuần. Đơn giản hơn nhiều.
- Khuyến nghị: Tách biệt 2 giai đoạn — Giai đoạn 1: PDF only, Giai đoạn 2: SIM-PKI integration.
- Status: ❓ Chưa quyết định — cần người dùng quyết định

### 2.5. Instance riêng hay cùng instance?
- Cùng instance: API + worker trong 1 jar/1 process. Đơn giản deployment, ít resource.
- Instance riêng: 1 jar cho API, 1 jar cho worker. Tách biệt, dễ scale, dễ restart API không ảnh hưởng worker.
- Khuyến nghị: Cùng instance (MVP). Riêng instance nếu load cao.
- Status: ❓ Chưa quyết định — cần người dùng quyết định

---

## 3. Checklist chi tiết

### 3.1. Giai đoạn 0: Đánh giá & Khai báo (không code)

| # | Việc cần làm | Chi tiết | Trạng thái |
|---|--------------|----------|------------|
| 0.1 | Xác nhận PDF library | Kiểm tra giấy phép library hiện tại (PDFBox/iText/PDFClown). Nếu chưa có → chọn PDFBox (Apache 2.0). | ❌ |
| 0.2 | Xác nhận port strategy | Cùng port + context-path hay port riêng? | ❌ |
| 0.3 | Xác nhận auth method | API Key / JWT / OAuth2? | ❌ |
| 0.4 | Xác nhận PDF scope | Tạo / Tạo+Ký / Tạo+Ký+Lưu+Download? | ❌ |
| 0.5 | Xác nhận SIM-PKI integration | Có ký PDF số không? | ❌ |
| 0.6 | Xác nhận instance strategy | Cùng instance hay riêng? | ❌ |
| 0.7 | Đánh giá dependency conflict | Chạy mvn dependency:tree, kiểm tra xung đột PDF libraries. | ❌ |
| 0.8 | Đánh giá thread model hiện tại | Kiểm tra Worker Core thread pool config, đánh giá khả năng đồng thời HTTP + MQ. | ❌ |

### 3.2. Giai đoạn 1: Foundation (Cấu hình & Infrastructure)

| # | Việc cần làm | Chi tiết | Trạng thái |
|---|--------------|----------|------------|
| 1.1 | Tạo cấu hình mới | Tạo BackendProperties (hoặc WebApiProperties) theo pattern @ConfigurationProperties. Đảm bảo KHÔNG modify WorkerProperties. | ❌ |
| 1.2 | Tạo .env template | Thêm biến môi trường mới vào .env.example (port, api keys, PDF config, tenant config...). | ❌ |
| 1.3 | Tạo application.yml mới | Thêm application-backend.yml / application-webapi.yml theo mẫu application-simpki.yml. | ❌ |
| 1.4 | Tạo directory cấu trúc | Tạo src/main/java/com/td/worker/transport/http/ (chứa Controller, Config, Filter, Interceptor). | ❌ |
| 1.5 | Tạo tenant config | Nếu multi-tenant: tạo TenantProperties (tenant ID → API config mapping). | ❌ |
| 1.6 | Cập nhật pom.xml | Nếu cần dependency mới (PDF library, auth library). KHÔNG xóa/sửa dependency hiện tại. | ❌ |
| 1.7 | Tạo deploy script mới | Tạo deploy/backend/ theo mẫu deploy/simpki/ (copy jar, systemd service, nginx proxy). | ❌ |
| 1.8 | Cập nhật README.md | Thêm dòng mới vào docs/README.md mục lục. | ❌ |

### 3.3. Giai đoạn 2: Web API Layer (Lớp API HTTP)

| # | Việc cần làm | Chi tiết | Trạng thái |
|---|--------------|----------|------------|
| 2.1 | Tạo Tenant Interceptor | Custom HandlerInterceptor đọc tenant từ header/path, lưu vào ThreadLocal/RequestContextHolder. | ❌ |
| 2.2 | Tạo Auth Filter | Filter kiểm tra API Key / JWT. Inject vào Spring Security filter chain. | ❌ |
| 2.3 | Tạo Async Config | @Configuration + @EnableAsync với thread pool riêng cho HTTP requests. KHÔNG ảnh hưởng worker thread pool. | ❌ |
| 2.4 | Tạo Error Handler | @ControllerAdvice / RestControllerAdvice cho error response thống nhất. | ❌ |
| 2.5 | Tạo Base Response | ApiResponse<T> (code, message, data) cho mọi endpoint. | ❌ |
| 2.6 | Tạo DTOs | Request/Response DTOs cho các endpoint (upload PDF, download PDF, status...). | ❌ |

### 3.4. Giai đoạn 3: PDF Processing (Xử lý PDF)

| # | Việc cần làm | Chi tiết | Trạng thái |
|---|--------------|----------|------------|
| 3.1 | Tạo PDF Service Interface | Định nghĩa interface PdfService (generate, sign, merge, split...). | ❌ |
| 3.2 | Tạo PDF Service Impl | Triển khai interface. Dùng PDF library đã chọn. | ❌ |
| 3.3 | Tạo File Storage Service | Upload (temp), validate (type/size), download, cleanup. | ❌ |
| 3.4 | Tạo PDF Validation | Validate PDF format, page count, file size trước khi xử lý. | ❌ |
| 3.5 | Xử lý timeout | Timeout mechanism cho PDF processing dài (Circuit Breaker / Async + polling). | ❌ |
| 3.6 | Tạo error handling riêng | Exception classes riêng cho PDF processing (PdfGenerationException, PdfValidationException...). | ❌ |

### 3.5. Giai đoạn 4: Controllers (Endpoint API)

| # | Việc cần làm | Chi tiết | Trạng thái |
|---|--------------|----------|------------|
| 4.1 | Tạo PdfApiController | Controller chính cho PDF operations. Dùng @RestController, @RequestMapping("/api/pdf"). | ❌ |
| 4.2 | Endpoint: Upload & Generate | POST /api/pdf/generate — upload HTML/template, trả về PDF. | ❌ |
| 4.3 | Endpoint: Download | GET /api/pdf/{id}/download — tải PDF đã tạo. | ❌ |
| 4.4 | Endpoint: Status | GET /api/pdf/{id}/status — trạng thái xử lý (async case). | ❌ |
| 4.5 | Endpoint: Health | GET /api/health — health check riêng cho API layer. | ❌ |
| 4.6 | Giữ nguyên DebugTriggerController | KHÔNG sửa DebugTriggerController. Có thể thêm @ConditionalOnProperty nếu cần. | ❌ |

### 3.6. Giai đoạn 5: Testing

| # | Việc cần làm | Chi tiết | Trạng thái |
|---|--------------|----------|------------|
| 5.1 | Tạo test config | application-test.yml với cấu hình test cho API (mock services, test port...). | ❌ |
| 5.2 | Tạo BackendApiTest | Base test class theo mẫu SimPkiIntegrationTest. | ❌ |
| 5.3 | Test: PDF generation | Test endpoint 4.2 với mock PDF service. | ❌ |
| 5.4 | Test: PDF download | Test endpoint 4.3. | ❌ |
| 5.5 | Test: Tenant resolution | Test tenant interceptor với nhiều tenant headers. | ❌ |
| 5.6 | Test: Auth filter | Test auth filter valid/invalid API key. | ❌ |
| 5.7 | Test: Error handling | Test error response format, status codes. | ❌ |
| 5.8 | Test: PDF validation | Test invalid file type, oversized file... | ❌ |
| 5.9 | Integration test (nếu SIM-PKI) | Kết hợp PDF + SIM-PKI signing. | ❌ |

### 3.7. Giai đoạn 6: Security Hardening

| # | Việc cần làm | Chi tiết | Trạng thái |
|---|--------------|----------|------------|
| 6.1 | Rate limiting | Rate limit cho API endpoints (token bucket / sliding window). | ❌ |
| 6.2 | Request size limit | Cấu hình server.tomcat.max-swallow-size, spring.servlet.multipart.max-file-size. | ❌ |
| 6.3 | CORS config | CORS cho phép domains (nếu API expose external). | ❌ |
| 6.4 | HTTPS | HTTPS cho API endpoints (sẽ cấu hình ở Nginx/Load Balancer). | ❌ |
| 6.5 | Audit logging | Log tất cả API requests (who, when, what, tenant). | ❌ |
| 6.6 | SQL injection prevention | Validate all inputs, dùng parameterized queries (nếu DB). | ❌ |

### 3.8. Giai đoạn 7: Deployment & Monitoring

| # | Việc cần làm | Chi tiết | Trạng thái |
|---|--------------|----------|------------|
| 7.1 | Tạo deployment script | deploy/backend/build-and-deploy.sh theo mẫu deploy/simpki/. | ❌ |
| 7.2 | Tạo systemd service | td-worker-backend.service với proper startup/shutdown. | ❌ |
| 7.3 | Cấu hình Nginx | Nginx reverse proxy cho API endpoints (/api/* → worker port). | ❌ |
| 7.4 | Health check endpoint | /api/health cho load balancer/monitoring. | ❌ |
| 7.5 | Graceful shutdown | Đảm bảo Spring Boot graceful shutdown cho HTTP connections. | ❌ |
| 7.6 | Monitoring | Prometheus/Grafana metrics cho API endpoints (request count, latency, error rate). | ❌ |
| 7.7 | Log aggregation | Cấu hình log format cho API requests (ELK/EFK stack). | ❌ |
| 7.8 | Load testing | JMeter/Gatling test cho API endpoints (nếu cần). | ❌ |

### 3.9. Giai đoạn 8: SIM-PKI Integration (Tùy chọn, Phase 2)

| # | Việc cần làm | Chi tiết | Trạng thái |
|---|--------------|----------|------------|
| 8.1 | Tạo Signing Service | Interface + Impl cho digital signature (kế thừa simpki module pattern). | ❌ |
| 8.2 | Endpoint: Sign PDF | POST /api/pdf/{id}/sign — ký PDF số. | ❌ |
| 8.3 | Endpoint: Verify Signature | GET /api/pdf/{id}/verify — verify signature. | ❌ |
| 8.4 | Test signing | Integration test cho signing flow. | ❌ |
| 8.5 | Deploy SIM-PKI | Kết hợp PDF API + SIM-PKI trong deployment. | ❌ |

---

## 4. Retry Strategy & Rollback

### 4.1. Khi nào retry?
- Dependency conflict không giải quyết được → retry với library khác (PDFBox ↔ iText ↔ PDFClown)
- Thread model conflict → retry với async processing pattern khác
- Auth method phức tạp quá → retry với API Key đơn giản

### 4.2. Rollback plan
- Tất cả thay đổi ở Giai đoạn 0-2: KHÔNG sửa code cũ → rollback = bỏ qua giai đoạn đó
- Giai đoạn 3+ (PDF processing): Code mới riêng biệt → rollback = tắt endpoint, giữ nguyên worker
- Deploy script mới: KHÔNG ảnh hưởng deploy hiện tại → rollback = dùng script cũ

### 4.3. Test để đảm bảo không hỏng
- Chạy toàn bộ test hiện tại sau mỗi giai đoạn
- Worker vẫn nhận message queue bình thường
- Health check worker vẫn hoạt động
- DebugTriggerController vẫn hoạt động (nếu bật)

---

## 5. File sẽ tạo mới (KHÔNG sửa file hiện có)

### 5.1. Source code mới
```
src/main/java/com/td/worker/transport/http/
├── config/
│   ├── BackendProperties.java
│   ├── AsyncConfig.java
│   └── WebMvcConfig.java
├── filter/
│   └── AuthFilter.java
├── interceptor/
│   └── TenantInterceptor.java
├── controller/
│   ├── PdfApiController.java
│   └── HealthController.java
├── dto/
│   ├── PdfGenerateRequest.java
│   ├── PdfGenerateResponse.java
│   ├── PdfDownloadResponse.java
│   └── ApiResponse.java
├── exception/
│   ├── PdfException.java
│   └── RestControllerAdvice.java
└── service/
    ├── PdfService.java
    └── FileStorageService.java
```

### 5.2. Config & resources mới
```
src/main/resources/
├── application-backend.yml
├── application-webapi.yml
└── pdf/
    └── config.properties (nếu cần)
```

### 5.3. Test mới
```
src/test/java/com/td/worker/transport/http/
├── BackendApiTest.java
├── PdfApiControllerTest.java
├── TenantInterceptorTest.java
└── AuthFilterTest.java
```

### 5.4. Deploy & docs mới
```
deploy/backend/
├── build-and-deploy.sh
├── .env.example
└── td-worker-backend.service

docs/
├── 19-backend-webapi-pdf-checklist.md (tài liệu này)
└── 20-backend-webapi-pdf-deploy.md (sau khi triển khai)
```

---

## 6. Mở rộng trong tương lai (Future Roadmap)

- GraphQL: Thay REST bằng GraphQL cho queries phức tạp (nếu cần)
- WebSocket: Real-time notification cho PDF processing status
- Rate Limiting: Advanced rate limiting với Redis
- API Gateway: Tách API gateway riêng, worker chỉ nhận internal traffic
- Microservice: Tách backend API thành microservice riêng (instance riêng)
- File Storage: Tích hợp S3/MinIO thay vì local storage cho PDF files
- Async Pipeline: Event-driven PDF processing pipeline (publish result khi xong)

---

## 7. References

- Plan file: C:\Users\Administrator\.local\share\kilo\plans\1789778117249-td-worker-simpki-backend-upgrade.md
- SIM-PKI checklist: docs/17-checklist-tich-hop-kyso-simpki.md
- SIM-PKI deployment: docs/18-trien-khai-test-simpki.md
- Extensible Architecture: docs/promt-Extensible-Worker-Architecture.txt
- Project root: C:\code_projects\HSCV\JAVA\td-woker\
