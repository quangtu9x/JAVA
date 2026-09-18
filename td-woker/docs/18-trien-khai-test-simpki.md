# 18 — Triển khai môi trường TEST chỉ chạy `simpki.sign`

> Hướng dẫn TRIỂN KHAI cụ thể (khác doc kiến trúc `01`-`15` và checklist tích hợp `16`/`17`) — dùng
> để đưa 1 Worker instance **CHỈ xử lý `simpki.sign`** lên máy chủ test thật, tách biệt khỏi instance
> dev/multi-module hiện có. Toàn bộ script nằm ở `JAVA/td-woker/deploy/simpki/`. Xem
> `docs/17-checklist-tich-hop-kyso-simpki.md` cho bối cảnh nghiệp vụ/rủi ro của module `simpki`.

---

## 0. ⚠️ Bug đã tự phát hiện + sửa (17/09/2026) — BẮT BUỘC đọc trước khi chạy

**Không tự ý chạy `java -jar td-worker.jar` trực tiếp — LUÔN dùng `run-simpki.sh`/`run-simpki.ps1`.**

Đã tự phát hiện bằng thực nghiệm thật (không suy đoán): cơ chế Spring Boot đọc file `.env`
(`spring.config.import: optional:file:.env[.properties]`) **KHÔNG** tự động map đúng biến môi
trường dạng `WORKER_TASKS_SIMPKI_ENABLED` sang property `worker.tasks.simpki.enabled` cho các
module đăng ký `@ConfigurationProperties` RIÊNG (`SimPkiProperties`/`GuiNhanQuocGiaProperties`/
`HttpDebugTriggerProperties`) — khác các property ĐÃ CÓ SẴN `${VAR:default}` trong
`application.yml` (`WORKER_DB_URL`, `CONTROL_PLANE_ENABLED`... — những cái này vẫn hoạt động đúng
qua `.env`, KHÔNG bị ảnh hưởng). Hậu quả nếu chạy `java -jar` trực tiếp: `.env` có
`WORKER_TASKS_SIMPKI_ENABLED=true` nhưng **ÂM THẦM KHÔNG có tác dụng** — Worker vẫn khởi động bình
thường, KHÔNG báo lỗi gì, nhưng `SimPkiGatewayConfig` vẫn chọn `NoopSimPkiGatewayClient` (mọi lần
"ký" đều trả `NOT_CONFIGURED`, không bao giờ gọi MSSP thật) — dễ nhầm tưởng cấu hình sai ở đâu đó
khác. **Real OS environment variable thì KHÔNG bị lỗi này** (đã xác nhận qua 3 phép thử: biến môi
trường thật ✅, `-D` hệ thống ✅ (dạng dot đúng), `.env` file-import ❌ cho property không có
placeholder).

**Đã sửa**: `run-simpki.sh`/`run-simpki.ps1` giờ tự đọc `.env` rồi **export/set làm biến môi trường
HỆ ĐIỀU HÀNH THẬT** trước khi gọi `java -jar` (bash: `set -a; source .env; set +a`; PowerShell:
`Set-Item -Path "Env:$key" -Value $value` cho từng dòng) — cách này đã THỬ THẬT (chạy 2 script, xác
nhận cả `worker.debug.endpoint.enabled` lẫn `worker.tasks.simpki.enabled` đều bind đúng, log xác
nhận `SimPkiSignTaskHandler` chạy tới bước gọi gateway thay vì dừng ở "enabled=false").

**Ghi chú cho việc khác trong tương lai** (chưa sửa trong phiên này, NGOÀI phạm vi yêu cầu hiện tại):
module `guinhanquocgia` (`docs/16-checklist`) dùng CÙNG pattern `@ConfigurationProperties` không có
placeholder trong `application.yml` — RẤT CÓ THỂ dính CÙNG lỗi này nếu chạy qua `.env` thay vì biến
môi trường thật. Chưa kiểm chứng/sửa — cần làm riêng khi có nhu cầu bật `guinhanquocgia.send` thật.

### Bug đã sửa (2) — 18/09/2026: `SSLHandshakeException: PKIX path building failed` dù cấu hình đúng

Sau khi sửa bug (1) ở trên, người dùng đưa lên máy chủ test THẬT (đã có Postgres/H2 hoạt động,
`WORKER_TASKS_SIMPKI_ENABLED=true` bind đúng) — gọi thử `simpki.sign` với AP_ID/AP_PASSWORD/keystore
Thanh Hóa THẬT, nhận lỗi TLS thật (khác hẳn `UNAUTHORIZED_ACCESS` đã thấy trước đó):

```
SDK_ERROR: ; nested exception is:
javax.net.ssl.SSLHandshakeException: (certificate_unknown) PKIX path building failed:
sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification
path to requested target
```

**Đã tái hiện THẬT trên môi trường khác** (không suy đoán): boot `td-worker.jar` với ĐÚNG cấu hình
AP_ID/AP_PASSWORD/keystore/truststore Thanh Hóa (đã xác minh dùng được ở đợt trước) qua
`run-simpki.sh` — LUÔN lỗi PKIX y hệt, không bao giờ chạm được MSSP thật.

**Nguyên nhân** (đã cô lập bằng 2 lần chạy thật, khác nhau đúng 1 biến): `WorkerHttpClient` (Apache
HttpClient5, LUÔN được Spring khởi tạo lúc context refresh — KHÔNG phụ thuộc `simpki.sign` có bật
hay không) gọi `SSLContexts.createSystemDefault()` NGAY LÚC khởi tạo bean, đọc
`javax.net.ssl.trustStore` TẠI THỜI ĐIỂM ĐÓ (chưa được set, vì `simpki.sign` chỉ set LƯỜI lúc
`sign()` đầu tiên — luôn xảy ra SAU khi `WorkerHttpClient` đã khởi tạo xong) rồi CACHE lại cho JVM —
QUÁ MUỘN để `simpki.sign` set lại đúng trust store cho lần gọi Axis thật.

**Đã xác nhận fix bằng thực nghiệm** (2 lần chạy thật, chỉ khác thời điểm set SSL property):
- Set LƯỜI (bản gốc, lúc `sign()` đầu tiên) → **LUÔN** lỗi PKIX như trên.
- Set SỚM (`-Djavax.net.ssl.trustStore=...` truyền qua JVM args, tương đương thời điểm bootstrap) →
  gọi MSSP thật **THÀNH CÔNG** tới tận `UNAUTHORIZED_ACCESS` (đúng kỳ vọng, không còn lỗi PKIX).

**Đã sửa tận gốc**: `com.td.worker.tasks.simpki.SimPkiSslBootstrap` (class MỚI) gọi từ
`WorkerApplication.main()` NGAY TRƯỚC `SpringApplication.run(...)` — đọc biến môi trường THẬT trực
tiếp (`System.getenv`, Spring Environment CHƯA tồn tại ở bước này) và set SSL SỚM HƠN MỌI bean, kể cả
`WorkerHttpClient`. Đã build lại + chạy thật qua `run-simpki.sh` (đường chuẩn, KHÔNG cần `-D` thủ
công) — xác nhận: log `SimPkiSslBootstrap` xuất hiện Ở DÒNG THỨ 3 (trước cả `WorkerHttpClient`), và
lần ký đạt tới `UNAUTHORIZED_ACCESS` — hết hẳn lỗi PKIX.

⚠️ Đây là 1 trong RẤT ÍT chỗ `WorkerApplication.java` (Worker Core) bị đụng tới cho 1 module cụ thể
— có chủ đích và bắt buộc kỹ thuật (docs/17-checklist mục 0.4.1 giải thích đầy đủ vì sao không cơ chế
Spring bean lifecycle nào khác đảm bảo được "chạy trước MỌI bean"), không phải vi phạm nguyên tắc
"Worker Core không biết nghiệp vụ module".

### Việc đã dọn thêm (18/09/2026) — 2 việc KHÔNG PHẢI bug chức năng, chỉ để log/console sạch hơn

Sau khi 2 bug ở trên đã sửa xong, ký thử với thuê bao thật ĐÃ THÀNH CÔNG (có `transId` +
`verificationCode`) — 2 việc dưới đây chỉ là dọn dẹp, không ảnh hưởng kết quả ký:

1. **Log tiếng Việt hiện sai font trên console Windows** (vd `tß║íi` thay vì `tại`) — do code page
   mặc định của console Windows (cp1252/850) không phải UTF-8, trong khi `logback-spring.xml` ghi
   log đúng UTF-8 từ trước (file `logs/td-worker.log` luôn đúng, chỉ HIỂN THỊ trên console sai).
   Đã sửa `run-simpki.ps1`: thêm `chcp 65001` (đổi code page cửa sổ console) +
   `-Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dfile.encoding=UTF-8` (ép JVM ghi
   System.out/err bằng UTF-8, cần từ Java 13+, đã kiểm chứng Java 17). `run-simpki.sh` không cần sửa
   (terminal Linux mặc định đã UTF-8).
2. **WARN `Unable to find required classes (javax.activation.DataHandler và
   javax.mail.internet.MimeMultipart). Attachment support is disabled.`** — cùng nguyên nhân với bug
   SAAJ (docs/17-checklist mục 0.3.1): Axis 1.4 (2006) còn tham chiếu `javax.activation`/`javax.mail`
   để hỗ trợ đính kèm MIME (SOAP with Attachments), 2 gói này cũng đã bị GỠ khỏi JDK từ Java 11 trở
   lên. Giao thức MSSP ký số của ta KHÔNG dùng đính kèm MIME (SOAP text thuần, base64 trong XML) nên
   WARN này KHÔNG chặn việc ký (đã có bằng chứng: ký thành công dù thiếu, log user gửi 18/09/2026).
   Đã bổ sung 2 dependency Maven Central đúng namespace `javax.*` vào `pom.xml`
   (`javax.activation:javax.activation-api:1.2.0`, `com.sun.mail:javax.mail:1.6.2`) để hết cảnh báo
   và phòng trường hợp SDK sau này cần đính kèm thật. Test suite 72/72 pass sau khi thêm.

### Việc đã bổ sung (18/09/2026) — endpoint debug trả kèm dữ liệu THẬT từ MSSP

Trước đây `POST /internal/debug/publish` chỉ trả `{"type":"FLOW_OK","canAck":true,"detail":null}` —
đủ để biết message có ACK được không, nhưng KHÔNG có transId/verificationCode/signatureBase64 (khi
ký thành công) hay errorCode/errorDesc (khi thất bại) — người test phải tự xem log server mới biết
kết quả ký thật. Đã bổ sung field MỚI `units` (mảng, giữ nguyên `type`/`canAck`/`detail` như cũ,
KHÔNG phá cấu trúc response hiện có):

```jsonc
{
  "type": "FLOW_OK",
  "canAck": true,
  "detail": null,
  "units": [
    {
      "unitId": "u-test-5",
      "success": true,
      "error": null,
      "detail": {
        "success": true,
        "signatureBase64": "...",
        "transId": "A8102999652155779",
        "verificationCode": "261731",
        "statusCode": 500
      }
    }
  ]
}
```

Khi ký thất bại, `units[].detail` chứa `errorCode`/`errorDesc` gốc từ MSSP thay vì `signatureBase64`/
`transId`/`verificationCode`.

**Cách làm** (không phá kiến trúc "Worker Core không biết nghiệp vụ module" — hoàn toàn generic, áp
dụng được cho MỌI TaskHandler khác, không riêng `simpki.sign`): trước đây kết quả `Future<UnitResult>`
của từng unit bị `MessageFlowRunner.waitForCompletion` **BỎ QUA HOÀN TOÀN** sau khi `Future.get()`
xong (chỉ dùng để "chờ"), nên `FlowResult`/`RoutedResult` chỉ có số đếm tổng hợp
(`DONE=.. RETRY=.. FAILED=..`), không có chi tiết unit nào trả về gì:
1. `UnitResult` (core.task) thêm field tự do `Map<String,Object> detail` (optional, mặc định
   `null` — handler cũ KHÔNG cần đổi gì) qua overload mới `UnitResult.success(detail)`/
   `UnitResult.failure(error, detail)`.
2. `MessageFlowRunner.waitForCompletion` giờ GIỮ LẠI `Future.get()` thành `UnitOutcome` (record mới,
   `core.flow.UnitOutcome`: unitId/success/error/detail) thay vì bỏ qua.
3. `FlowResult` thêm `List<UnitOutcome> unitOutcomes` → `RoutedResult` thêm `List<UnitOutcome> units`
   (field MỚI, không đổi 3 field cũ) → `DebugTriggerController` trả thẳng ra HTTP response.
4. `SimPkiSignTaskHandler` set `detail` bằng ĐÚNG bộ field đã gửi qua `simpki.sign_reply` (RabbitMQ)
   từ trước — không có logic mới, chỉ không còn "quăng đi" giá trị đã có sẵn.

**Đã bổ sung log lỗi rõ ràng hơn** (theo yêu cầu người dùng): trước đây khi ký thất bại (MSSP trả lỗi
protocol, vd `UNAUTHORIZED_ACCESS`) hoặc gateway ném exception (vd lỗi mạng/SSL), `SimPkiSignTaskHandler`
KHÔNG tự log gì — chỉ có 1 dòng WARN chung chung từ `UnitDispatcher` (rút gọn message, KHÔNG có stack
trace). Giờ `SimPkiSignTaskHandler` tự log NGAY tại nguồn: `log.warn` khi MSSP trả lỗi protocol (kèm
documentId/unitId/errorCode/errorDesc), `log.error` kèm ĐẦY ĐỦ stack trace khi gateway ném exception
(giúp chẩn đoán lỗi mạng/SSL/SDK nhanh hơn nhiều so với chỉ có `e.getMessage()` rút gọn).

Test suite 72/72 pass (2 test cũ `SimPkiSignTaskHandlerTest` bổ sung assertion cho `result.detail()`).

---

## 1. Vì sao 1 instance RIÊNG (không dùng chung instance dev hiện có)

- **SSL JVM global** (docs/17-checklist mục 0.4): bật `simpki.sign` set
  `javax.net.ssl.keyStore/trustStore` cho TOÀN BỘ tiến trình — ảnh hưởng mọi HTTPS khác của CÙNG
  instance. Tách instance tránh xung đột với các module khác (vbdi/vbden/guinhanquocgia).
- **IP whitelist** (docs/17-checklist mục A3): MSSP chỉ chấp nhận kết nối từ IP Public đã đăng ký
  với Cục CTS-BMTT — máy chủ chạy instance này phải NAT ra Internet đúng IP đó.
- **Thời gian xử lý dài bất thường**: 1 lần ký chờ người dùng xác nhận trên điện thoại tới 300s —
  khác hẳn các task khác (thường <1s). Cấu hình timeout riêng (mục 4) tránh ảnh hưởng instance khác.

Do đó: **profile Spring riêng `simpki`** (`src/main/resources/application-simpki.yml`, file MỚI,
không sửa `application.yml`) + **bộ script triển khai riêng** (`deploy/simpki/`).

## 2. Chuẩn bị TRƯỚC khi build (checklist)

| # | Việc | Đã có sẵn ở đâu |
|---|---|---|
| 1 | Database checkpoint CHƯA có? | Postgres: `deploy/simpki/create-database.sql` (mục 3a). CHƯA có Postgres/Docker: dùng H2 file cục bộ, không cần cài gì (mục 3b) |
| 2 | RabbitMQ broker (host/port, đã bật plugin management nếu muốn dùng `publish-test-signal`) | Do đội hạ tầng cung cấp |
| 3 | AP_ID/AP_PASSWORD thật | `docs/TDSimSign/sdk-mobilepki/java/thanhhoa.txt` — ĐÃ có |
| 4 | Truststore CA (`truststore-bcy.jks`) | `JAVA/td-woker/certs/simpki/truststore-bcy.jks` — ĐÃ có, đã xác minh còn dùng được (`certs/simpki/README.md`) |
| 5 | Keystore PKCS12 (chứng thư Client Authentication) | `JAVA/td-woker/certs/simpki/3108937_VGCA_Application_Provider_certificate.p12` — ĐÃ có (mật khẩu `123456`), xem `certs/simpki/README.md` |
| 6 | Máy chủ chạy instance NAT ra Internet bằng IP đã đăng ký | `116.97.118.219`/`222.255.21.194` (`thanhhoa.txt`) — PHẢI xác nhận với đội hạ tầng TRƯỚC khi build trên máy đó |
| 7 | JRE 17+ trên máy chủ test (Linux hoặc Windows) | Cài đặt thủ công nếu chưa có |

## 3. Database checkpoint — 2 lựa chọn

Worker Core **BẮT BUỘC** có 1 database checkpoint (không thể tắt/bỏ qua dù chỉ chạy `simpki.sign` —
đây là cơ chế idempotency/dedup CHUNG của Worker Core, không phải thứ riêng của module nào, xem
docs/07-idempotency-checkpoint.md). Chọn 1 trong 2 tuỳ hạ tầng máy chủ test hiện có:

### 3a. ĐÃ có Postgres (khuyến nghị cho môi trường thật)

```bash
# Sửa mật khẩu trong file trước khi chạy (KHÔNG dùng nguyên "CHANGE_ME_STRONG_PASSWORD")
psql -U postgres -h <db-host> -f deploy/simpki/create-database.sql
```

`.env`: giữ nguyên "LỰA CHỌN A" trong `.env.simpki.example` (`WORKER_DB_URL=jdbc:postgresql://...`).

### 3b. CHƯA có Postgres/Docker (test nhanh, không cài thêm gì)

**Đã THỰC NGHIỆM xác nhận** (build + chạy thật `td-worker.jar` trên máy soạn tài liệu này, khởi động
2 lần liên tiếp trỏ cùng 1 file dữ liệu): dùng thẳng **H2 file cục bộ** đã bundle sẵn trong
`td-worker.jar` — KHÔNG cần cài Postgres/Docker/bất kỳ gì thêm trên máy chủ test. Trong `.env`, comment
4 dòng "LỰA CHỌN A", bỏ comment 4 dòng "LỰA CHỌN B" (đã có sẵn trong `.env.simpki.example`):

```
WORKER_DB_URL=jdbc:h2:file:./data/tdworker;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
WORKER_DB_USERNAME=sa
WORKER_DB_PASSWORD=
WORKER_DB_SCHEMA_MANAGEMENT=script
WORKER_DB_DRIVER_CLASS_NAME=org.h2.Driver
```

(`pom.xml`: dependency `com.h2database:h2` đổi từ scope `test` sang mặc định/compile để có mặt trong
jar production — H2 trước đây CHỈ dùng cho test tự động; mọi hướng dẫn/`docker-compose.yml` khác của
Worker vẫn dùng Postgres làm mặc định, H2 chỉ kích hoạt khi tự tay đổi `WORKER_DB_URL` như trên.)

Giới hạn (đã ghi vào `.env.simpki.example`): CHỈ dùng cho test **1 instance** (H2 file không hỗ trợ
nhiều Worker instance cùng ghi 1 file); muốn "reset" sạch — dừng Worker rồi xoá thư mục `data/`.
**Khi có Postgres thật (môi trường triển khai thật sau này) — chỉ cần đổi lại 4 dòng
`WORKER_DB_*` về "LỰA CHỌN A" trong `.env`, KHÔNG cần đổi code/rebuild lại jar.**

Sau bước 3a HOẶC 3b, Worker **TỰ tạo bảng** ngay lần khởi động đầu tiên — KHÔNG cần chạy tay bất kỳ
file `.sql` migration nào:

- Postgres (`schema-management=flyway`, mặc định): log

  ```
  Flyway migrate database Worker (migration-worker): 3 migration được áp dụng
  ```

  (3 migration = `V1__create_worker_tables`, `V2__create_worker_guinhanquocgia_send_log`,
  `V3__create_worker_simpki_sign_log` — kể cả instance chỉ dùng `simpki.sign`, Flyway vẫn áp dụng ĐỦ
  cả 3 vì đây là schema CHUNG của database Worker, không phải theo module bật/tắt).

- H2 (`schema-management=script`): log

  ```
  Schema management=script: chạy db/worker-schema-h2.sql
  ```

  (bảng `worker_simpki_sign_log`/`worker_guinhanquocgia_send_log` tự tạo LƯỜI ngay lần đầu handler
  tương ứng chạy — KHÔNG cần có trong `worker-schema-h2.sql`, xem javadoc `SimPkiSignLogStore`).

## 4. Cấu hình timeout — bắt buộc đọc trước khi chạy thật

Worker Core có `unit-timeout` (mặc định 60s) — quá thời gian này, `MessageFlowRunner` sẽ
**interrupt** thread đang xử lý (`Future.cancel(true)`). 1 lần ký SIM-PKI đồng bộ có thể mất tới
`worker.tasks.simpki.timeout-seconds` (mặc định 300s, chờ người dùng xác nhận trên điện thoại) —
NGẮN HƠN sẽ làm hỏng giao dịch ký đang hợp lệ giữa chừng.

**`application-simpki.yml` đã tự đặt giá trị an toàn** khi kích hoạt profile `simpki`:

| Property | Giá trị profile `simpki` | Lý do |
|---|---|---|
| `worker.core.unit-timeout` | `330s` (biến `WORKER_UNIT_TIMEOUT`) | > 300s (timeout ký) có biên |
| `worker.core.claim-lease-seconds` | `360` (biến `WORKER_CLAIM_LEASE_SECONDS`) | > thời gian xử lý 1 unit — tránh instance khác claim lại unit đang dở dang khi mở rộng nhiều instance |
| `worker.rabbit.queues[0].concurrency/prefetch` | `2` | Mỗi lời ký giữ 1 thread rất lâu — không đặt cao như hàng đợi generic (4-8) |

Nếu tăng `WORKER_TASKS_SIMPKI_TIMEOUTSECONDS` (`.env`) vượt 330s, PHẢI tăng tương ứng
`WORKER_UNIT_TIMEOUT`/`WORKER_CLAIM_LEASE_SECONDS` (env, ghi đè profile).

## 5. Build

### Linux

```bash
cd JAVA/td-woker
./deploy/simpki/build.sh                 # KHÔNG kèm secret (an toàn, mặc định)
./deploy/simpki/build.sh --with-secrets  # KÈM AP_ID/AP_PASSWORD/keystore thật từ .env + certs/simpki/ hiện có
```

### Windows

```powershell
cd JAVA/td-woker
.\deploy\simpki\build.ps1                  # KHÔNG kèm secret (an toàn, mặc định)
.\deploy\simpki\build.ps1 -WithSecrets     # KÈM secret thật
```

Kết quả cả 2: `JAVA/td-woker/target/simpki-deploy/` (đã `.gitignore`, không lọt git) —
`td-worker.jar` + `run-simpki.sh`/`.ps1` + `.env.simpki.example` + `certs/simpki/truststore-bcy.jks`
(và nếu `--with-secrets`/`-WithSecrets`: `.env` đã điền sẵn AP_ID/AP_PASSWORD/mật khẩu keystore thật +
file `.p12` thật).

**`--with-secrets` chỉ nên dùng NGAY TRƯỚC khi chuyển lên máy chủ test** (thư mục kết quả chứa
private key/mật khẩu thật) — chuyển bằng kênh an toàn (`scp -r`/`rsync` qua SSH; Windows dùng
`Copy-Item` qua phiên PowerShell Remoting hoặc RDP nội bộ, KHÔNG qua email/chat/ổ đĩa chia sẻ công
khai — cùng nguyên tắc đã áp dụng cho `TENANT_CREDENTIAL_ENCRYPTION_KEY`, xem docs/09-configuration.md
mục 4).

## 6. Đưa lên máy chủ test + chạy

```bash
# Ví dụ Linux
scp -r target/simpki-deploy user@test-host:/opt/td-worker-simpki
ssh user@test-host
cd /opt/td-worker-simpki
# Nếu build KHÔNG dùng --with-secrets: copy .env.simpki.example -> .env, điền WORKER_DB_URL/
# RABBITMQ_HOST (2 giá trị PHẢI điền tay - phụ thuộc hạ tầng máy chủ test cụ thể, không tự đoán
# được) + xác nhận AP_ID/AP_PASSWORD/keystore đã đúng.
./run-simpki.sh
```

```powershell
# Ví dụ Windows (chạy trên máy chủ test, sau khi copy thư mục simpki-deploy sang)
cd C:\td-worker-simpki
# Điền WORKER_DB_URL/RABBITMQ_HOST trong .env nếu chưa có
.\run-simpki.ps1
```

Cả 2 script: tự `cd` vào đúng thư mục chứa chính nó (để đường dẫn tương đối `certs/simpki/...`,
`logs/...` trong `.env` luôn đúng bất kể gọi từ đâu), kiểm tra `td-worker.jar`/`.env` tồn tại, tạo
`logs/`, rồi `java -jar td-worker.jar` với `SPRING_PROFILES_ACTIVE=simpki`.

Log khởi động cần thấy (xác nhận ĐÚNG cấu hình):

```
Đăng ký N TaskHandler: [..., simpki.sign, ...]
RabbitMQ topology: main=worker.simpki, ... routing='worker.simpki.sign'
Flyway migrate database Worker (migration-worker): 3 migration được áp dụng
```

## 7. Kiểm tra hoạt động (health + trigger thử)

```
GET http://<host>:8090/actuator/health
```

### 7a. ĐÃ có RabbitMQ

**Trigger 1 giao dịch ký thử** (publish envelope `simpki.sign` qua REST API management của
RabbitMQ, KHÔNG cần cài client AMQP) — Linux: `deploy/simpki/publish-test-signal.sh`, Windows:
`deploy/simpki/publish-test-signal.ps1`:

```bash
./deploy/simpki/publish-test-signal.sh 84972495193 "noi dung ky thu"
```

```powershell
.\deploy\simpki\publish-test-signal.ps1 -Msisdn 84972495193 -HashText "noi dung ky thu"
```

### 7b. Test độc lập KHÔNG cần RabbitMQ (HTTP debug endpoint)

Chưa có môi trường RabbitMQ thật (test độc lập trên 1 máy)? Bật
`WORKER_DEBUG_ENDPOINT_ENABLED=true` trong `.env` (đã có sẵn trong `.env.simpki.example`, mặc định
`false`) — Worker mở thêm 1 endpoint `POST /internal/debug/publish` gọi THẲNG vào
`InboundMessageRouter` (cùng đường xử lý với RabbitMQ/Kafka, chỉ khác "transport" — xem javadoc
`DebugTriggerController`), KHÔNG cần broker nào cả:

```bash
./deploy/simpki/publish-test-http.sh 84972495193 "noi dung ky thu"
```

```powershell
.\deploy\simpki\publish-test-http.ps1 -Msisdn 84972495193 -HashText "noi dung ky thu"
```

⚠️ Endpoint này KHÔNG có xác thực riêng (tương đương quyền 1 producer hợp lệ) — CHỈ bật dev/test,
KHÔNG bật ở production/expose port này ra Internet. Request chờ ĐỒNG BỘ tới khi có kết quả (tới
`worker.tasks.simpki.timeout-seconds`, mặc định 300s, nếu đang chờ SIM xác nhận thật).

### Theo dõi kết quả (cả 2 cách 7a/7b)

Theo dõi log Worker + bảng `worker_simpki_sign_log` (`SELECT * FROM worker_simpki_sign_log ORDER BY
created_at DESC;`) để xem kết quả:

- Nếu máy chủ **CHƯA** NAT đúng IP đã đăng ký (`116.97.118.219`/`222.255.21.194`): nhận
  `AxisFault: UNAUTHORIZED_ACCESS` (mã lỗi `104`) — ĐÚNG hành vi kỳ vọng, đã thực nghiệm xác nhận
  (docs/17-checklist mục A3) — KHÔNG phải lỗi cấu hình phần mềm.
- Nếu máy chủ **ĐÃ** đúng IP: giao dịch được đẩy xuống điện thoại thuê bao (`msisdn` truyền vào) —
  người dùng cần XÁC NHẬN THẬT trên điện thoại (SIM-PKI) trong vòng
  `WORKER_TASKS_SIMPKI_TIMEOUTSECONDS` giây, kết quả trả về qua envelope `simpki.sign_reply` (nếu có
  consumer phía td-web lắng nghe — ngoài phạm vi `td-woker`, xem docs/17-checklist Phần 0.5).

## 8. Khắc phục nhanh

| Triệu chứng | Nguyên nhân khả dĩ |
|---|---|
| `HikariPool ... Exception during pool initialization` / `PSQLException: The connection attempt failed` | Chưa có Postgres thật (máy chủ test chưa mở/chưa dựng DB) — dùng H2 file cục bộ (mục 3b) thay vì Postgres cho tới khi có hạ tầng thật |
| Log `KEYSTORE_FILE_NOT_FOUND` | `WORKER_TASKS_SIMPKI_KEYSTOREPATH`/`TRUSTSTOREPATH` sai đường dẫn tương đối, hoặc chưa copy file `.p12`/`.jks` vào `certs/simpki/` cạnh `td-worker.jar` |
| Log `MISSING_AP_CREDENTIAL`/`MISSING_KEYSTORE_CONFIG` | `.env` còn để trống `WORKER_TASKS_SIMPKI_APID`/`APPASSWORD`/`KEYSTOREPASSWORD` |
| `publish-test-signal` báo `routed:false` | Worker chưa khởi động xong, hoặc `WORKER_RABBIT_EXCHANGE`/`RABBITMQ_VHOST` không khớp cấu hình Worker |
| Unit bị FAILED ở đúng ~60s dù đang chờ SIM | Thiếu `SPRING_PROFILES_ACTIVE=simpki` (profile không được kích hoạt → dùng `unit-timeout` mặc định 60s) — kiểm tra log dòng "The following N profiles are active" phải có `simpki` |
| `AxisFault: UNAUTHORIZED_ACCESS` dù đã điền đúng AP_ID/keystore | Máy chủ CHƯA NAT ra đúng IP đã đăng ký — xác nhận lại với đội hạ tầng/Cục CTS-BMTT (xem mục 7) |
| `SSLHandshakeException: PKIX path building failed` dù `trustStorePath` cấu hình đúng | Đang dùng `td-worker.jar` bản CŨ (trước khi có `SimPkiSslBootstrap`) — build lại jar mới nhất, xem mục 0 "Bug đã sửa (2)". Kiểm tra log dòng đầu có `SimPkiSslBootstrap: thiết lập javax.net.ssl...` KHÔNG (phải xuất hiện TRƯỚC dòng `WorkerHttpClient`) |
| Ký "enabled=true" trong `.env` nhưng log KHÔNG BAO GIỜ thấy `simpki.sign:` (im lặng, không lỗi) | Đã tự chạy `java -jar` trực tiếp thay vì `run-simpki.sh`/`.ps1` — xem mục 0 (bug `.env` không tự map biến môi trường cho property không có placeholder) |
| `POST /internal/debug/publish` trả `404 Not Found` | `WORKER_DEBUG_ENDPOINT_ENABLED` chưa `true`, hoặc (cùng nguyên nhân mục 0) đang chạy `java -jar` trực tiếp thay vì qua `run-simpki.sh`/`.ps1` |

## 9. Kiểm chứng ĐÃ THỰC HIỆN khi soạn tài liệu này

- `ApplicationSimpkiProfileTest` (test tự động, `src/test/java/com/td/worker/`): xác nhận profile
  `simpki` override ĐÚNG `worker.rabbit.queues` (list, khai báo lại toàn bộ — không merge được qua
  biến môi trường đơn lẻ) + `unit-timeout`/`claim-lease-seconds`/`vbdi.enabled`/`vbden.enabled`.
- Cả 5 script (`build.sh`/`build.ps1`/`run-simpki.sh`/`run-simpki.ps1`/`publish-test-signal.sh`/
  `.ps1`) đã syntax-check (`bash -n` / `PSParser`) VÀ chạy dry-run thật để xác nhận JSON envelope
  sinh ra hợp lệ (parse được cả outer request lẫn inner envelope, base64 round-trip đúng).
- `build.sh --with-secrets`/`build.ps1 -WithSecrets` đã chạy thật, xác nhận `.env` kết quả có đủ
  `WORKER_TASKS_SIMPKI_APID`/`APPASSWORD`/`KEYSTOREPATH`/`KEYSTOREPASSWORD` đúng giá trị thật.
- **KHÔNG thể** kiểm chứng chạy Worker end-to-end thật với RabbitMQ thật trong môi trường soạn tài
  liệu này (không có sẵn broker). Bước 6-7 ở trên là quy trình đã CHUẨN BỊ ĐẦY ĐỦ nhưng CHƯA tự chạy
  thật được với broker — cần xác nhận lại khi triển khai thật trên máy chủ test.
- **ĐÃ kiểm chứng THẬT (không suy đoán)** đường H2 (mục 3b) — người dùng báo lỗi thật
  `HikariPool ... PSQLException: The connection attempt failed` khi đưa lên máy chủ test chưa có
  Postgres. Đã build lại `td-worker.jar` với cấu hình H2 file cục bộ, chạy thật 2 lần LIÊN TIẾP trỏ
  cùng 1 file dữ liệu (`java -jar td-worker.jar`, profile `simpki`, Rabbit/control-plane tắt để cô
  lập đúng vấn đề DB): cả 2 lần đều `Started WorkerApplication` thành công, log đúng
  `"Schema management=script: chạy db/worker-schema-h2.sql"` + `"Đăng ký 10 TaskHandler: [...,
  simpki.sign, ...]"`, KHÔNG có lỗi "table already exists" ở lần chạy thứ 2 (xác nhận fix
  `IF NOT EXISTS` trong `worker-schema-h2.sql` hoạt động đúng cho persistent H2 qua nhiều lần
  restart). Trong lúc làm, tự phát hiện + sửa 1 bug thật:
  `worker.db.driver-class-name` trong `application.yml` gốc là literal cứng (không có `${...}`)
  nên biến môi trường `WORKER_DB_DRIVER_CLASS_NAME` KHÔNG ghi đè được — đã sửa thành placeholder
  `${WORKER_DB_DRIVER_CLASS_NAME:org.postgresql.Driver}` (giá trị mặc định GIỮ NGUYÊN, chỉ thêm khả
  năng cấu hình — không ảnh hưởng bất kỳ deployment Postgres nào đang có).
- **ĐÃ kiểm chứng THẬT (không suy đoán) bug ".env không tự map property không có placeholder"** —
  xem mục 0. Phát hiện qua chính người dùng: bật `WORKER_TASKS_SIMPKI_ENABLED=true` trong `.env` rồi
  chạy `java -jar` trực tiếp, endpoint debug trả `404`/handler không chạy dù `.env` "đúng". Đã cô
  lập nguyên nhân bằng 3 phép thử boot thật liên tiếp (Condition Evaluation Report,
  `java -jar ... --debug`): (1) `.env` (spring.config.import) → THẤT BẠI (`did not find property
  'enabled'`); (2) `-Dworker.debug.endpoint.enabled=true` (Java system property, tên dot đúng) →
  THÀNH CÔNG; (3) biến môi trường HỆ ĐIỀU HÀNH THẬT (`WORKER_DEBUG_ENDPOINT_ENABLED=true` export
  trước khi chạy) → THÀNH CÔNG. Đã sửa `run-simpki.sh`/`run-simpki.ps1` tự export `.env` thành biến
  môi trường thật — chạy lại qua chính 2 script đã sửa, xác nhận CẢ `worker.debug.endpoint.enabled`
  LẪN `worker.tasks.simpki.enabled` đều bind đúng (log `SimPkiSignTaskHandler` chạy tới bước gọi
  gateway, dừng đúng ở `KEYSTORE_FILE_NOT_FOUND` vì dùng keystore giả trong test, KHÔNG dừng ở
  "enabled=false" nữa).
- **Endpoint debug HTTP đã chạy THẬT end-to-end** (không chỉ đơn vị test mock): boot Worker qua
  `run-simpki.sh` (H2 + `WORKER_DEBUG_ENDPOINT_ENABLED=true` + `WORKER_TASKS_SIMPKI_ENABLED=true`),
  gọi CẢ `publish-test-http.sh` và `publish-test-http.ps1` → cả 2 trả `{"type":"FLOW_OK",...}` và
  log xác nhận `SimPkiSignTaskHandler` thực sự chạy (`simpki.sign: documentId=test-doc-1,
  msisdn=***193 ...` → `KEYSTORE_FILE_NOT_FOUND` như kỳ vọng với keystore giả).

Tài liệu liên quan: [17-checklist-tich-hop-kyso-simpki.md](17-checklist-tich-hop-kyso-simpki.md) (bối
cảnh nghiệp vụ/rủi ro), [05-multi-instance.md](05-multi-instance.md) (cơ chế nhiều instance),
[10-deployment.md](10-deployment.md) (deployment tổng quát).
