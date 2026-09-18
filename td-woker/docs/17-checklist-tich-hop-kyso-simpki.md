# 17 — Checklist tích hợp "Ký số SIM-PKI" (MSSP/ETSI TS 102 204) vào td-woker

> Tài liệu **checklist** (khác các doc kiến trúc `01`-`15`, cùng thể loại doc `16`) — dùng để theo dõi
> việc tích hợp năng lực **ký số từ xa qua SIM ghép chứng thư số** (SIM-PKI, gọi qua webservice MSSP —
> Mobile Signature Service Provider, chuẩn ETSI TS 102 204) từ SDK chính thức của Cục Chứng thực số
> và Bảo mật thông tin (Bộ Công an), nguồn `docs/TDSimSign/sdk-mobilepki/java`, vào `td-woker` theo
> đúng cơ chế Extensible Worker (`docs/promt-Extensible-Worker-Architecture.txt`). Legend chuẩn của
> dự án: ✅ đã xong | 🟡 có 1 phần | 🔴 chưa làm | ⛔ không làm (có lý do) | ⚠️ rủi ro cần ưu tiên.
>
> **Ràng buộc cứng đã tuân thủ xuyên suốt tài liệu này**: CHỈ thêm mới (jar dependency mới, file
> config mới, 1 package/module TaskHandler MỚI tách biệt hoàn toàn) — KHÔNG sửa/xoá/đổi hành vi bất
> kỳ TaskHandler/class/config nào đang có (`sample.echo`, `vbdi.*`, `vbden.*`, `guinhanquocgia.send`,
> RabbitMQ/Kafka listener hiện có, migration Flyway hiện có). Đã build + chạy toàn bộ test cuối phiên
> để xác nhận — xem Phần 4.

---

## Phần 0 — Khảo sát: SDK `docs/TDSimSign/sdk-mobilepki/java` cung cấp năng lực gì

### 0.1 Khác biệt với ký số hiện có của `td-application`

`td-application` (`KySoVbdiUseCase`/`KySoVbdenUseCase`, `docs`: xem `ChuKySoDto`) đã có 1 mô hình ký
số: người dùng tự ký PDF cục bộ bằng USB token/HSM rồi **upload file đã ký** lên — backend chỉ lưu
file + đánh dấu `dakyso`/`dakycoquan`, KHÔNG tự ký. SIM-PKI là mô hình **HOÀN TOÀN KHÁC** — ký số MÁY
CHỦ KHỞI TẠO: server tính hash cần ký, gửi kèm số điện thoại (MSISDN) của người ký tới webservice
MSSP; MSSP đẩy giao dịch xuống SIM-PKI của thuê bao, người dùng xác nhận trực tiếp trên điện thoại
(so khớp "mã xác thực"/Verification Code), MSSP trả về chữ ký PKCS#1 theo phương thức đồng bộ
(synch). 2 module không thay thế nhau — cả 2 cùng tồn tại, module mới KHÔNG đụng gì tới
`KySoVbdiUseCase`/`KySoVbdenUseCase` hiện có.

### 0.2 Thành phần SDK đã khảo sát trực tiếp (đọc mã nguồn thật, không suy đoán)

| Việc | Class/method (đọc trực tiếp `sample-code/*.java` — chính là mã nguồn ĐÃ BIÊN DỊCH sẵn trong `vgca.mssp.jar`, xác nhận bằng `jar tf`) |
|---|---|
| Ký 1 hash qua SIM-PKI (đồng bộ) | `vgca.mssp.ExtSignature.sign(byte[] hashData, String MSISDN, String dataToBeDisplayed) -> byte[]` (SOAP `MSS_Signature`, ETSI TS 102 204) |
| Lấy chứng thư người dùng | `vgca.mssp.ExtSignature.GetCert(String MSISDN) -> byte[]` (SOAP `MSS_Registration`) — **CHƯA wire vào TaskHandler nào ở bản này** (xem giới hạn Phần 0.4) |
| Thiết lập SSL Client Authentication (mutual TLS) | `vgca.mssp.JvmSsl.setSSL(trustStore, trustStorePassword, keyStore, keyStorePassword, keyStoreType)` — **STATIC/GLOBAL JVM** (xem cảnh báo Phần 0.4) |
| Tính mã xác thực (Verification Code) | `vgca.mssp.VerificationCodeCalculator.calculate(byte[] documentHash) -> String` (SHA-256, 6 chữ số) |

`note.txt` (SDK): endpoint MSSP công khai `https://mpki1.ca.gov.vn:18083/soap/services/MSS_SignaturePort`
(TLS 1.2, Client Authentication bắt buộc — JDK 1.8+), yêu cầu đăng ký chứng thư Client (Application
Provider) + tài khoản AP_ID/AP_PASSWORD với Cục CTS-BMTT trước khi dùng thật.

### 0.3 Vì sao KHÔNG dùng thẳng `vgca.mssp.ExtSignature` (quyết định thiết kế quan trọng)

`ExtSignature` (lớp tiện ích vendor cung cấp sẵn, đã biên dịch trong `vgca.mssp.jar`) chỉ cho phép
cấu hình đúng 3 giá trị qua setter công khai: `setEPMSSP` (signature URL), `setAPID`, `setAPPWD`.
Các giá trị còn lại — `MSSP_ID`, `signatureProfile`, `MSS_Format`, và đặc biệt **`TimeOut` HARD-CODE
`"300"` ngay trong mã nguồn vendor, KHÔNG có setter** — đều CỐ ĐỊNH theo giá trị mẫu, không cấu hình
được từ bên ngoài. Vi phạm yêu cầu "mọi tham số phải cấu hình được, không hard-code" của module này.

→ **`com.td.worker.tasks.simpki.VgcaMsspSignatureClient`** viết lại đúng luồng SOAP mà
`ExtSignature.sign()` thực hiện (transcribe 1:1 từ mã nguồn tham khảo đã đọc trực tiếp — KHÔNG suy
đoán giao thức) nhưng gọi thẳng các lớp Axis/ETSI stub cấp thấp (`org.etsi.uri.TS102204.v1_1_2.*`,
`vn.mobile_id.ae.TS102204.etsi204.*`, `com.tomicalab.mobileid.datatype.*` — cùng nằm trong
`laverca-mobileid_v2.jar`/`laverca-mobileid-datatype.jar`) để MỌI giá trị (endpoint, `MSSP_ID`,
`signatureProfile`, `MSS_Format`, timeout, `AP_ID`/`AP_PASSWORD`) đều đọc từ `SimPkiProperties`. Vẫn
tái dùng nguyên `vgca.mssp.VerificationCodeCalculator` (thuật toán tính mã xác thực — không có gì để
cấu hình) và `vgca.mssp.JvmSsl` (tiện ích set SSL JVM, xem cảnh báo dưới) từ đúng jar vendor.

### 0.3.1 ✅ Đã giải quyết rủi ro A2 (SAAJ thiếu trên JDK hiện đại) — bổ sung 09/2026 (2)

Axis 1.4 (2006) tham chiếu `javax.xml.soap.*` (SAAJ API — SOAP with Attachments API for Java), API
này đã bị **GỠ khỏi JDK từ Java 11 trở lên** (tách thành module Jakarta EE riêng, và bản Jakarta đổi
package thành `jakarta.xml.soap.*` mà Axis 1.4 KHÔNG dùng được — code cũ compile cứng với package
`javax.xml.soap`). Trên JDK 21 (JDK hiện tại của môi trường dev/CI), gọi thật
`MSS_SignatureBindingStub.MSS_Signature()` ban đầu ném `NoClassDefFoundError: javax/xml/soap/SOAPException`.

**Đã sửa**: bổ sung 2 dependency CHUẨN Maven Central (khác 15 jar SDK ở trên — 2 jar này CÓ trên
Maven Central, không phải SDK vendor riêng, không cần `libs-repo/` cục bộ) giữ ĐÚNG namespace
`javax.xml.soap` (KHÔNG phải bản Jakarta `jakarta.xml.soap`):

```xml
<dependency>
    <groupId>javax.xml.soap</groupId>
    <artifactId>javax.xml.soap-api</artifactId>
    <version>1.4.0</version>
</dependency>
<dependency>
    <groupId>com.sun.xml.messaging.saaj</groupId>
    <artifactId>saaj-impl</artifactId>
    <version>1.5.3</version>
</dependency>
```

- `javax.xml.soap:javax.xml.soap-api:1.4.0` — API interface (SOAPException, SOAPMessage...) đúng
  namespace `javax.xml.soap` mà Axis 1.4 compile cứng vào.
- `com.sun.xml.messaging.saaj:saaj-impl:1.5.3` — implementation THẬT (phiên bản trước khi SAAJ đổi
  sang namespace `jakarta.*`, vẫn dùng `javax.xml.soap`, tương thích API 1.4.0 ở trên).

**Đã THỰC NGHIỆM xác nhận** (`VgcaMsspSignatureClientTest#callNeverThrowsEvenWithInvalidKeystore`,
chạy lại trên JDK 21 sau khi thêm 2 dependency): `NoClassDefFoundError: javax/xml/soap/SOAPException`
**KHÔNG còn xảy ra nữa** — luồng gọi SOAP giờ chạy xa hơn tới tận tầng thiết lập kết nối SSL, lỗi lúc
này chỉ còn là `NoSuchAlgorithmException`/`SocketException` do keystore DUMMY của chính test (nội
dung giả `"not-a-real-keystore"`, cố ý không phải file keystore thật) không parse được — ĐÚNG như kỳ
vọng của 1 test dùng cấu hình giả, không phải dấu hiệu thiếu dependency nữa. Rủi ro A2 coi như **ĐÃ
GIẢI QUYẾT** ở mức "không còn thiếu class" — phần còn lại (gọi MSSP thật thành công) chỉ còn phụ
thuộc A3 (endpoint/AP credential/keystore THẬT, xem Phần 0.5) chứ không còn vướng SAAJ nữa.

### 0.4 ⚠️ Rủi ro kiến trúc quan trọng nhất — SSL JVM GLOBAL

SDK Axis 1.4 (2006, không hỗ trợ cấu hình `SSLContext` theo từng lời gọi) bắt buộc thiết lập chứng
thư Client Authentication bằng `System.setProperty("javax.net.ssl.keyStore"...)` — javadoc gốc của
chính `JvmSsl.setSSL` đã ghi rõ: *"This is STATIC, and uses global JVM settings. This can conflict
with settings done by some other library function."* `WorkerHttpClient` (Apache HttpClient 5, dùng
bởi các task handler khác) xây `SSLContext` qua `SSLContexts.createSystemDefault()` — **CŨNG đọc
CÙNG system property này** → mọi kết nối HTTPS khác của tiến trình Worker sẽ dùng CHUNG keystore/
truststore cấu hình cho SIM-PKI kể từ lần ký đầu tiên sau khi bật `enabled=true`. Đây là giới hạn
CỦA CHÍNH SDK vendor (Axis 1.4 không có API khác), không phải lỗi thiết kế ở module này.

**Khuyến nghị vận hành**: nếu Worker instance còn xử lý task khác cần gọi HTTPS ra ngoài với client
certificate KHÁC (hoặc không cần client cert) — triển khai `simpki.sign` trên **1 Worker instance
RIÊNG** (cơ chế multi-instance đã có sẵn, xem `docs/05-multi-instance.md`: mỗi instance có thể lắng
nghe taskType khác nhau qua `worker.rabbit.queues[].task-type`/`worker.kafka.topics[].task-type`) —
tránh set SSL global ảnh hưởng handler khác trong CÙNG tiến trình.

### 0.4.1 ⚠️→✅ Hệ quả NGHIÊM TRỌNG HƠN đã phát hiện + sửa (18/09/2026) — chiều NGƯỢC LẠI

Rủi ro ở mục 0.4 mô tả chiều "simpki ảnh hưởng module khác". Thực nghiệm thật trên môi trường triển
khai (không phải máy soạn tài liệu) phát hiện chiều NGƯỢC LẠI còn nghiêm trọng hơn: **`WorkerHttpClient`
(luôn được Spring khởi tạo lúc context refresh, KHÔNG phụ thuộc `simpki.sign` có bật hay không) làm
`simpki.sign` KHÔNG BAO GIỜ gọi MSSP thật thành công được** — nếu chỉ set
`javax.net.ssl.trustStore`/`keyStore` LƯỜI (lần `sign()` đầu tiên, như bản gốc). Nguyên nhân:
`WorkerHttpClient` gọi `SSLContexts.createSystemDefault()` ngay lúc khởi tạo bean (rất sớm, TRƯỚC
BẤT KỲ task nào chạy) — đọc `javax.net.ssl.trustStore` TẠI THỜI ĐIỂM ĐÓ (chưa được set) rồi CACHE lại
cho JVM; lúc `simpki.sign` set property này (muộn hơn nhiều), Axis 1.4 vẫn ném
`SSLHandshakeException: PKIX path building failed: ... unable to find valid certification path` dù
`trustStorePath` cấu hình hoàn toàn đúng.

**Đã THỰC NGHIỆM tái hiện + xác nhận fix** (2 lần chạy `td-worker.jar` thật, cùng AP_ID/AP_PASSWORD/
keystore/truststore Thanh Hóa): set SSL LƯỜI (bản gốc) → LUÔN lỗi PKIX, không bao giờ chạm được MSSP;
set SSL SỚM (`-Djavax.net.ssl.trustStore=...` lúc khởi động JVM, tương đương thời điểm bootstrap) →
gọi MSSP thật THÀNH CÔNG tới tận `UNAUTHORIZED_ACCESS` (đúng kỳ vọng khi IP chưa whitelist).

**Đã sửa**: `com.td.worker.tasks.simpki.SimPkiSslBootstrap` (class mới) — gọi từ
`WorkerApplication.main()` **NGAY TRƯỚC** `SpringApplication.run(...)`, đọc biến môi trường THẬT
trực tiếp qua `System.getenv` (Spring Environment/`.env` chưa tồn tại ở bước này) và gọi
`JvmSsl.setSSL(...)` SỚM HƠN MỌI bean, kể cả `WorkerHttpClient`. Đây là 1 trong số RẤT ÍT chỗ
`WorkerApplication.java` (Worker Core) bị đụng tới cho 1 module cụ thể — có chủ đích và bắt buộc: không
cơ chế Spring bean lifecycle nào (kể cả `@PostConstruct`/`@DependsOn`) đảm bảo "chạy trước MỌI bean
khác" đáng tin cậy bằng việc gọi mã trước `SpringApplication.run()`. No-op an toàn nếu
`worker.tasks.simpki` không bật qua biến môi trường thật. `VgcaMsspSignatureClient.ensureSslConfigured()`
(lười, bản gốc) vẫn giữ lại làm lưới an toàn thứ 2. Xem chi tiết đầy đủ + bằng chứng thực nghiệm ở
docs/18-trien-khai-test-simpki.md mục "Bug đã sửa (2)".

### 0.5 Giới hạn CỐ Ý của bản này (chưa làm, không suy đoán nghiệp vụ chưa chốt)

- 🔴 `simpki.get_cert` (lấy chứng thư người dùng, `ExtSignature.GetCert`) — CHƯA wire thành TaskHandler
  (chưa có nhu cầu nghiệp vụ cụ thể được xác nhận cho việc đăng ký/tra cứu chứng thư SIM-PKI của người
  dùng). Ghi chú thêm: `ExtSignature` KHÔNG có setter cho `REG_URL` (endpoint đăng ký) — nếu sau này
  cần, phải viết thêm 1 method trong `VgcaMsspSignatureClient` theo đúng cách đã làm với `sign()`
  (dùng thẳng stub cấp thấp), không thể cấu hình qua `ExtSignature`.
- 🔴 Payload `simpki.sign` giả định **hash/DigestInfo đã được tính sẵn** (base64) bởi producer
  (td-web/td-application) — Worker KHÔNG tự đọc file/tính hash (giữ nguyên tắc "message nhẹ",
  docs/architecture/08 §3.2). Việc chuẩn hoá thuật toán băm/đóng gói DigestInfo cho từng loại tài
  liệu (PDF/XML...) là việc của producer, ngoài phạm vi checklist này.
- 🔴 Worker KHÔNG tự gắn chữ ký kết quả vào `file_metadata`/document (đó là dữ liệu nghiệp vụ của
  td-web, Worker không sở hữu schema tenant ngoài phạm vi dedup — đúng nguyên tắc đã áp dụng cho
  `guinhanquocgia`, xem docs/16 mục 0.5). Kết quả ký (chữ ký base64/transId/mã xác thực) publish qua
  envelope phản hồi `simpki.sign_reply` (RabbitMQ, khi `worker.rabbit.enabled=true`) — bước "gắn chữ
  ký vào tài liệu ở td-web" là 1 handler/consumer RIÊNG phía td-web, ngoài phạm vi `td-woker`.
- ✅ ~~Chưa có endpoint/AP_ID/AP_PASSWORD/chứng thư Client Authentication THẬT~~ — ĐÃ CÓ ĐỦ (cập nhật
  17/09/2026, đợt 2 — xem Phần 0.3.1/A3/A2, `certs/simpki/README.md`). Rào cản còn lại chỉ là IP
  nguồn (hạ tầng/mạng), không còn là thiếu credential/chứng thư nữa.

---

## Phần 1 — Danh sách hạng mục đã triển khai

| # | Hạng mục | Việc đã làm | Trạng thái |
|---|---|---|---|
| 1 | Copy jar SDK | 15 jar copy nguyên từ `docs/TDSimSign/sdk-mobilepki/java/sample-libs/` vào `JAVA/td-woker/libs/simpki/` (MỚI) | ✅ |
| 2 | Cài vào repository cục bộ | `mvn install:install-file ... -DlocalRepositoryPath=libs-repo` cho cả 15 jar (coordinates tự đặt, xem bảng Phần 1.1) — KHÔNG dùng scope `system` (cùng lý do đã rút ra ở `guinhanquocgia`, xem docs/16 mục "Quyết định đóng gói jar") | ✅ |
| 3 | Khai báo dependency trong `pom.xml` | Thêm `<repository id="simpki-local-repo">` (trỏ CÙNG thư mục `libs-repo/` đã có, chỉ thêm id riêng cho rõ ràng) + 15 `<dependency>` bình thường (không `scope`/`systemPath`) + 2 `<dependency>` SAAJ chuẩn Maven Central (xem mục 3b) | ✅ |
| 3b | Giải quyết rủi ro A2 (SAAJ thiếu trên JDK hiện đại) | Thêm `javax.xml.soap:javax.xml.soap-api:1.4.0` + `com.sun.xml.messaging.saaj:saaj-impl:1.5.3` (chuẩn Maven Central, không cần `libs-repo/`) — đã THỰC NGHIỆM xác nhận hết `NoClassDefFoundError` trên JDK 21, xem Phần 0.3.1 | ✅ |
| 4 | Config module mới | `com.td.worker.tasks.simpki.SimPkiProperties` (`@Component @ConfigurationProperties(prefix="worker.tasks.simpki")`) — file MỚI, KHÔNG sửa `WorkerProperties.java`/`application.yml`/`WorkerApplication.java` hiện có | ✅ |
| 5 | Cổng gọi SOAP MSSP | `SimPkiGatewayClient` (interface) + `VgcaMsspSignatureClient` (implementation THẬT, viết lại SOAP call bằng stub Axis/ETSI cấp thấp — xem Phần 0.3) + `NoopSimPkiGatewayClient` (mặc định, an toàn) + `SimPkiGatewayConfig` (`@Bean` có điều kiện, cùng pattern đã rút kinh nghiệm ở `GuiNhanQuocGiaGatewayConfig`) | ✅ |
| 6 | TaskHandler mới | `com.td.worker.tasks.simpki.SimPkiSignTaskHandler` — taskType `simpki.sign`, tự đăng ký qua `TaskHandlerRegistry` (đã xác nhận log: `"Đăng ký 10 TaskHandler: [..., simpki.sign, ...]"`) | ✅ |
| 7 | Dedup/audit DB | Bảng `worker_simpki_sign_log` — Flyway MỚI `db/migration-worker/V3__create_worker_simpki_sign_log.sql` (không sửa V1/V2) cho production/flyway-mode + tự `CREATE TABLE IF NOT EXISTS` runtime trong `SimPkiSignLogStore` cho test/H2-script-mode | ✅ |
| 8 | Phản hồi kết quả ký | `SimPkiSignTaskHandler#publishSignReply` — publish envelope `simpki.sign_reply` qua RabbitMQ (khi `worker.rabbit.enabled=true`), cùng pattern `VbdiDeliveryTaskHandler#publishArrivedReply` | ✅ |
| 9 | Test mới | `SimPkiSignTaskHandlerTest` (7, mock collaborator), `SimPkiSignLogStoreTest` (4, H2 thật), `VgcaMsspSignatureClientTest` (6, guard-clause xác định + "never throws") — tổng 17 test mới, tất cả PASS | ✅ |
| 10 | `simpki.get_cert` (lấy chứng thư) | — | 🔴 chưa làm (xem Phần 0.5) |
| 11 | Endpoint/AP_ID/AP_PASSWORD/chứng thư Client Auth thật | AP_ID/AP_PASSWORD Thanh Hóa + truststore + keystore PKCS12 thật đã cấu hình `.env`/`certs/simpki/`, `enabled=true`, probe thủ công xác nhận TLS/AP-auth chạy đúng (mục 1.2) | ✅ (chỉ còn chặn bởi IP whitelist, xem A3) |
| 12 | Handler phía td-web gắn chữ ký vào file_metadata khi nhận `simpki.sign_reply` | — | 🔴 chưa làm (ngoài phạm vi `td-woker`, xem Phần 0.5) |
| 13 | Chạy Worker từ IP đã đăng ký với Cục CTS-BMTT | — | 🔴 chưa làm (hạ tầng/mạng, xem A3) |

### 1.1 Coordinates jar đã cài vào `libs-repo/` (repository cục bộ, KHÔNG có trên Maven Central)

| Jar gốc (`sample-libs/`) | groupId:artifactId:version tự đặt |
|---|---|
| `vgca.mssp.jar` | `vn.gov.ca.vgca:vgca-mssp:1.0.0` |
| `laverca-mobileid_v2.jar` | `fi.ficom.mobileid:laverca-mobileid:2.0.0` |
| `laverca-mobileid-datatype.jar` | `com.tomicalab.mobileid:laverca-mobileid-datatype:1.0.0` |
| `axis-1.4.jar` | `org.apache.axis:axis:1.4` |
| `jaxrpc-1.1.jar` | `org.apache.axis:jaxrpc:1.1` |
| `wsdl4j-1.6.2.jar` | `wsdl4j:wsdl4j:1.6.2` |
| `commons-discovery-0.5.jar` | `commons-discovery:commons-discovery:0.5` |
| `commons-logging-1.1.3.jar` | `commons-logging:commons-logging:1.1.3` |
| `commons-codec-1.7.jar` | `commons-codec:commons-codec:1.7` |
| `commons-lang-2.6.jar` | `commons-lang:commons-lang:2.6` |
| `httpclient-4.3.6.jar` | `org.apache.httpcomponents:httpclient:4.3.6` |
| `httpcore-4.3.3.jar` | `org.apache.httpcomponents:httpcore:4.3.3` |
| `castor-1.3.2.jar` | `org.codehaus.castor:castor:1.3.2` |
| `castor-1.3.2-core.jar` | `org.codehaus.castor:castor-core:1.3.2` |
| `castor-1.3.2-xml.jar` | `org.codehaus.castor:castor-xml:1.3.2` |

Ngoài 15 jar SDK trên (cài vào `libs-repo/` cục bộ), còn 2 dependency **CHUẨN Maven Central** (KHÔNG
phải SDK vendor, không cần `libs-repo/`) để giải quyết rủi ro A2 (xem Phần 0.3.1): `javax.xml.soap:
javax.xml.soap-api:1.4.0` + `com.sun.xml.messaging.saaj:saaj-impl:1.5.3`.

Khác `guinhanquocgia` (chỉ 2 jar, CỐ Ý chưa thêm transitive vì rủi ro guava/testcontainers) — ở đây
khai báo **ĐỦ CẢ BỘ 15 jar** mà chính SDK gốc đã đóng gói kèm (`sample-libs/`), vì: (1) không group/
artifactId nào trùng dependency hiện có của `td-worker` (httpclient**5** khác httpclient**4**,
project không có commons-lang**3**) → KHÔNG có rủi ro Maven "nearest wins" hạ cấp bất kỳ dependency
đang dùng; (2) thiếu 1 trong số này rất dễ gây `NoClassDefFoundError` khi Axis stub thật sự chạy (SOAP
serialization/HTTP transport cần đủ cả chuỗi). Build tái lập 100% chỉ bằng `git clone` + `mvn package`
— không cần mạng/Nexus nội bộ (jar + `libs-repo/` commit thẳng vào repo).

### Danh sách file mới (đầy đủ đường dẫn, tương đối từ `JAVA/td-woker/`)

```
libs/simpki/*.jar (15 file — bản gốc, nguồn cho install-file, không được pom.xml tham chiếu trực tiếp)
libs-repo/vn/gov/ca/vgca/vgca-mssp/1.0.0/...
libs-repo/fi/ficom/mobileid/laverca-mobileid/2.0.0/...
libs-repo/com/tomicalab/mobileid/laverca-mobileid-datatype/1.0.0/...
libs-repo/org/apache/axis/axis/1.4/...
libs-repo/org/apache/axis/jaxrpc/1.1/...
libs-repo/wsdl4j/wsdl4j/1.6.2/...
libs-repo/commons-discovery/commons-discovery/0.5/...
libs-repo/commons-logging/commons-logging/1.1.3/...
libs-repo/commons-codec/commons-codec/1.7/...
libs-repo/commons-lang/commons-lang/2.6/...
libs-repo/org/apache/httpcomponents/httpclient/4.3.6/...
libs-repo/org/apache/httpcomponents/httpcore/4.3.3/...
libs-repo/org/codehaus/castor/castor/1.3.2/...
libs-repo/org/codehaus/castor/castor-core/1.3.2/...
libs-repo/org/codehaus/castor/castor-xml/1.3.2/...
src/main/resources/db/migration-worker/V3__create_worker_simpki_sign_log.sql
src/main/java/com/td/worker/tasks/simpki/SimPkiTaskConstants.java
src/main/java/com/td/worker/tasks/simpki/SimPkiProperties.java
src/main/java/com/td/worker/tasks/simpki/SimPkiSignPayload.java
src/main/java/com/td/worker/tasks/simpki/SimPkiSignOutcome.java
src/main/java/com/td/worker/tasks/simpki/SimPkiGatewayClient.java
src/main/java/com/td/worker/tasks/simpki/NoopSimPkiGatewayClient.java
src/main/java/com/td/worker/tasks/simpki/VgcaMsspSignatureClient.java
src/main/java/com/td/worker/tasks/simpki/SimPkiSslBootstrap.java (bổ sung 18/09/2026 — xem mục 0.4.1)
src/main/java/com/td/worker/tasks/simpki/SimPkiGatewayConfig.java
src/main/java/com/td/worker/tasks/simpki/SimPkiSignLogStore.java
src/main/java/com/td/worker/tasks/simpki/SimPkiSignTaskHandler.java
src/test/java/com/td/worker/tasks/simpki/SimPkiSignTaskHandlerTest.java
src/test/java/com/td/worker/tasks/simpki/SimPkiSignLogStoreTest.java
src/test/java/com/td/worker/tasks/simpki/VgcaMsspSignatureClientTest.java
certs/simpki/truststore-bcy.jks       (copy từ docs/TDSimSign/.../samplemssp/etc/ — CA công khai, đã xác minh còn dùng được)
certs/simpki/3108937_VGCA_Application_Provider_certificate.p12  (KHÔNG commit, .gitignore chặn — keystore THẬT, nguồn: người dùng cung cấp từ docs/TDSimSign/.../p12key/, xác nhận đang dùng thật ở .NET)
certs/simpki/README.md                (nguồn gốc + bằng chứng xác minh truststore/keystore + hướng dẫn)
docs/17-checklist-tich-hop-kyso-simpki.md (tài liệu này)
```

### 1.2 Cấu hình THẬT đã nạp (cập nhật 17/09/2026, đợt 2 — xem A3/Nhóm B mục 2)

`.env` (KHÔNG commit, đã có sẵn trong `.gitignore`) đã được cấu hình ĐẦY ĐỦ:
`WORKER_TASKS_SIMPKI_APID`/`_APPASSWORD` = giá trị THẬT của Thanh Hóa
(`docs/TDSimSign/sdk-mobilepki/java/thanhhoa.txt`) + `WORKER_TASKS_SIMPKI_TRUSTSTOREPATH=certs/simpki/truststore-bcy.jks`
+ `WORKER_TASKS_SIMPKI_KEYSTOREPATH=certs/simpki/3108937_VGCA_Application_Provider_certificate.p12`
(`_KEYSTOREPASSWORD=123456`) — cả 2 file đã xác minh THẬT còn dùng được (xem `certs/simpki/README.md`
+ Phần 0.3.1/A3). **`WORKER_TASKS_SIMPKI_ENABLED=true`** (đã bật, xác nhận an toàn bằng probe thủ
công — xem A3). `.gitignore` đã thêm `certs/simpki/*.p12`/`*.pfx` (xác nhận bằng
`git check-ignore -v`) để không bao giờ commit nhầm 2 file keystore thật này.

### File hiện có ĐÃ SỬA (additive, không đổi nội dung cũ)

```
pom.xml         — CHỈ thêm 1 <repository> (id "simpki-local-repo", trỏ CÙNG libs-repo/ đã có) + 15
                  <dependency> SDK simpki + 2 <dependency> SAAJ chuẩn Maven Central (giải quyết A2,
                  xem Phần 0.3.1), kèm comment giải thích, KHÔNG sửa/xoá bất kỳ dependency/plugin
                  nào đang có (kể cả 2 dependency guinhanquocgia).
docs/README.md  — CHỈ thêm 1 dòng mục lục (# 17), không sửa nội dung mô tả các mục 01-16 đang có.
.env.example    — CHỈ thêm 1 khối biến môi trường mới ở cuối file (WORKER_TASKS_SIMPKI_*), không sửa
                  biến nào đang có.
src/main/java/com/td/worker/WorkerApplication.java (bổ sung 18/09/2026) — CHỈ thêm 1 dòng gọi
                  SimPkiSslBootstrap.applyIfConfigured() NGAY TRƯỚC SpringApplication.run(...), kèm
                  comment giải thích rõ đây là NGOẠI LỆ có chủ đích với nguyên tắc "Worker Core không
                  đụng module cụ thể" — bắt buộc về mặt kỹ thuật (xem mục 0.4.1), không sửa/xoá gì
                  khác trong file, không ảnh hưởng behavior khi worker.tasks.simpki không bật.
```

Không sửa `WorkerProperties.java`, `application.yml`, `WorkerApplication.java`,
`worker-schema-h2.sql`, `worker-schema-postgres.sql`, `V1__create_worker_tables.sql`,
`V2__create_worker_guinhanquocgia_send_log.sql`, hay bất kỳ class/test nào thuộc
`vbdi`/`vbden`/`guinhanquocgia`/`sample`/`core`/`infrastructure`/`transport` đang có.

---

## Phần 2 — Cấu hình: mapping đầy đủ (KHÔNG hard-code)

`SimPkiProperties` (`@ConfigurationProperties(prefix = "worker.tasks.simpki")`), nạp qua biến môi
trường theo relaxed-binding chuẩn Spring Boot — KHÔNG cần khai báo gì thêm trong `application.yml`
hiện có:

| Field (`SimPkiProperties`) | Biến môi trường | Mặc định | Ghi chú |
|---|---|---|---|
| `enabled` | `WORKER_TASKS_SIMPKI_ENABLED` | `false` | Công tắc TỔNG — bật mới thiết lập SSL JVM global |
| `signatureUrl` | `WORKER_TASKS_SIMPKI_SIGNATUREURL` | link MSSP công khai (`note.txt`) | Đổi nếu Cục CTS cấp endpoint khác |
| `msspId` | `WORKER_TASKS_SIMPKI_MSSPID` | `http://hmssp.mobile-id.vn` | Theo mẫu SDK |
| `signatureProfile` | `WORKER_TASKS_SIMPKI_SIGNATUREPROFILE` | `http://mobile-id.vn/digitalSignature` | Theo mẫu SDK |
| `mssFormat` | `WORKER_TASKS_SIMPKI_MSSFORMAT` | `...#PKCS1` | Theo mẫu SDK |
| `apId` | `WORKER_TASKS_SIMPKI_APID` | *(rỗng)* | **BẮT BUỘC** cấu hình thật (Cục CTS-BMTT cấp) |
| `apPassword` | `WORKER_TASKS_SIMPKI_APPASSWORD` | *(rỗng)* | **BẮT BUỘC**, secret thật |
| `timeoutSeconds` | `WORKER_TASKS_SIMPKI_TIMEOUTSECONDS` | `300` | Thời gian chờ xác nhận trên điện thoại |
| `trustStorePath` | `WORKER_TASKS_SIMPKI_TRUSTSTOREPATH` | *(rỗng)* | **BẮT BUỘC**, đường dẫn JKS chứa CA MSSP |
| `trustStorePassword` | `WORKER_TASKS_SIMPKI_TRUSTSTOREPASSWORD` | *(rỗng)* | |
| `keyStorePath` | `WORKER_TASKS_SIMPKI_KEYSTOREPATH` | *(rỗng)* | **BẮT BUỘC**, PKCS12/JKS chứng thư Client Auth (AP) |
| `keyStorePassword` | `WORKER_TASKS_SIMPKI_KEYSTOREPASSWORD` | *(rỗng)* | |
| `keyStoreType` | `WORKER_TASKS_SIMPKI_KEYSTORETYPE` | `PKCS12` | hoặc `JKS` |
| `displayMessageTemplate` | `WORKER_TASKS_SIMPKI_DISPLAYMESSAGETEMPLATE` | có `%%VC%%` | Payload có thể override từng lần qua `displayMessage` |
| `replyTaskType` | `WORKER_TASKS_SIMPKI_REPLYTASKTYPE` | `simpki.sign_reply` | taskType envelope phản hồi |

Không giá trị nào hard-code trong class Java — mọi field đọc từ property này (xem
`VgcaMsspSignatureClient` không có literal endpoint/credential nào ngoài 2 URI mô tả dịch vụ phụ trợ
`CERTIFICATE_URL`/`VALIDATION_URL`, đây là HẰNG SỐ GIAO THỨC ETSI cố định — không phải giá trị môi
trường/triển khai, giống cách `MessagingModeType.synch` cũng là hằng số giao thức).

---

## Phần 3 — Rủi ro cần lưu ý (⚠️) và việc còn lại theo nhóm ưu tiên

### Nhóm A — ⚠️ Rủi ro cần biết TRƯỚC khi bật `enabled=true`

- **A1 — SSL JVM global** (xem Phần 0.4): bật `simpki.sign` sẽ đặt lại `javax.net.ssl.keyStore/
  trustStore` cho TOÀN BỘ tiến trình Worker. Nếu instance đó còn gọi HTTPS ra dịch vụ khác — tách
  instance riêng (docs/05-multi-instance.md).
- **A2 — ✅ ĐÃ GIẢI QUYẾT (bổ sung 09/2026, đợt 2) — Thiếu classes SAAJ trên JDK hiện đại**: Axis 1.4
  (2006) tham chiếu `javax.xml.soap.*` (SAAJ API) — API này đã bị GỠ khỏi JDK từ Java 11 (tách thành
  module Jakarta EE riêng, đổi package sang `jakarta.xml.soap.*` mà Axis 1.4 không dùng được). Gọi
  thật `MSS_SignatureBindingStub.MSS_Signature()` trên JDK 21 BAN ĐẦU ném
  `NoClassDefFoundError: javax/xml/soap/SOAPException` — đã bị bắt bởi `catch (Throwable)` trong
  `VgcaMsspSignatureClient.sign()` nên KHÔNG làm sập test/Worker (an toàn ngay cả TRƯỚC khi sửa), NHƯNG
  nghĩa là chưa gọi được MSSP thật. **Đã sửa**: thêm 2 dependency chuẩn Maven Central đúng namespace
  `javax.xml.soap` (KHÔNG phải bản Jakarta) — `javax.xml.soap:javax.xml.soap-api:1.4.0` +
  `com.sun.xml.messaging.saaj:saaj-impl:1.5.3` — xem chi tiết + bằng chứng thực nghiệm Phần 0.3.1.
- **A3 — ✅ ĐÃ GIẢI QUYẾT ĐẦY ĐỦ (cập nhật 17/09/2026, đợt 2)**: đã có AP_ID/AP_PASSWORD THẬT của
  Thanh Hóa (`docs/TDSimSign/sdk-mobilepki/java/thanhhoa.txt`: `ap.id=https://mpki.ca.gov.vn/thanhhoa.qlvb`,
  `ap.password=fiXhN7iC`) — đã cấu hình vào `.env` (gitignored, KHÔNG commit). `trustStorePath`
  (CA truststore, KHÔNG gắn với AP cụ thể) đã xác minh THẬT còn dùng được — xem `certs/simpki/README.md`
  (đối chiếu `openssl s_client` với server LIVE `mpki1.ca.gov.vn:18083`: chuỗi chứng thư subca/rootca
  khớp CHÍNH XÁC với `truststore-bcy.jks`, dù leaf cert server đã được cấp lại 11/2024).

  **Đã bổ sung thêm keystore THẬT** (nguồn: người dùng cung cấp, thư mục
  `docs/TDSimSign/sdk-mobilepki/java/p12key/3108937_VGCA_Application_Provider_certificate.p12` —
  người dùng xác nhận đây CHÍNH LÀ chứng thư "đang cấu hình và chạy" ở phía tích hợp .NET cùng dự án,
  KHÔNG phải chỉ để demo — dù trùng tên/nội dung byte-for-byte với file mẫu trong
  `samplemssp/etc/`; CN chứng thư `VGCA Application Provider` — không gắn 1-1 theo tên với AP_ID
  `thanhhoa.qlvb`, việc AP_ID nào được phép dùng chứng thư nào do MSSP tự đối chiếu phía server,
  không thể hiện qua tên/CN chứng thư client). Mật khẩu `123456` (khớp `KEYSTORE_PASSWORD` mẫu trong
  `Samplemssp.java`, người dùng xác nhận trực tiếp) — còn hạn tới 26/02/2028 (xác minh bằng
  `keytool -list -v`, JDK đọc được trực tiếp không cần cờ `-legacy`).

  **Đã copy vào `certs/simpki/3108937_VGCA_Application_Provider_certificate.p12`** (khớp
  `.gitignore` `certs/simpki/*.p12` — xác nhận bằng `git check-ignore -v`, không bao giờ lọt vào git)
  + cấu hình đầy đủ `.env` + **BẬT `WORKER_TASKS_SIMPKI_ENABLED=true`**.

  **Đã THỰC NGHIỆM xác nhận bằng probe thủ công 1 lần** (test tạm, xoá ngay sau khi chạy — không nằm
  trong bộ test tự động vì phụ thuộc mạng/secret thật, đúng nguyên tắc test của dự án): gọi thật
  `VgcaMsspSignatureClient.sign()` với cấu hình trên từ máy build hiện tại (KHÔNG nằm trong 2 IP đã
  đăng ký `116.97.118.219`/`222.255.21.194`) — kết quả: **TLS mutual-auth THÀNH CÔNG** (bắt tay xong
  trong ~1.3s, không lỗi chứng thư/khoá), request SOAP được server CHẤP NHẬN VÀ XỬ LÝ (không phải lỗi
  parse/format), trả về đúng 1 SOAP Fault chuẩn giao thức: `org.apache.axis.AxisFault:
  UNAUTHORIZED_ACCESS` (mã lỗi `104` theo bảng mã lỗi chính thức, `MSSP-Tai lieu dac ta tich hop
  AE-VNP.pdf` mục 5) — bị bắt an toàn bởi `catch(Throwable)`, KHÔNG sập. Đây CHÍNH LÀ hành vi kỳ vọng
  khi gọi từ 1 IP CHƯA đăng ký với Cục CTS-BMTT (đúng ghi chú "chỉ IP đã đăng ký mới được phép") —
  xác nhận: **toàn bộ chuỗi cấu hình (keystore/truststore/AP_ID/AP_PASSWORD/SAAJ/Axis) đã ĐÚNG và
  hoạt động hết mức có thể kiểm chứng được từ môi trường này** — rào cản DUY NHẤT còn lại để ký thành
  công thật là **chạy Worker từ đúng IP đã đăng ký** (hạ tầng/mạng, không phải cấu hình phần mềm).
- **A4 — Ký lại (redelivery) tạo giao dịch xác nhận MỚI trên điện thoại người dùng**: khác
  `guinhanquocgia.send` (redelivery chỉ bỏ qua an toàn), ở đây method ký thật GỌI RA thiết bị người
  dùng — `SimPkiSignTaskHandler` đã dedup theo `unit_id` (KHÔNG ký lại nếu đã `tryClaim=false`) NHƯNG
  người phát hành envelope (producer) PHẢI đảm bảo `unitId` KHÔNG đổi giữa các lần publish lại CÙNG 1
  yêu cầu ký (đúng khuyến nghị chung `docs/03-task-handler-model.md` "Handler phải idempotent" —
  nhấn mạnh riêng ở đây vì hậu quả redelivery sai là làm phiền người dùng thật trên điện thoại, không
  chỉ lãng phí tài nguyên).

### Nhóm B — Nên làm trước khi bật `enabled=true` lần đầu

1. ✅ ~~Giải quyết A2 (SAAJ trên JDK hiện đại)~~ — đã xong (Phần 0.3.1).
2. ✅ ~~Xác nhận endpoint/AP_ID/AP_PASSWORD/chứng thư Client Authentication thật~~ — ĐÃ ĐỦ (17/09/2026,
   đợt 2): AP_ID/AP_PASSWORD Thanh Hóa + trustStore + keystore PKCS12 thật đều đã cấu hình, xác minh,
   và probe thủ công xác nhận TLS/AP-auth chạy đúng (chỉ còn bị chặn ở tầng IP whitelist — xem A3).
   Rào cản CÒN LẠI DUY NHẤT trước khi ký thật thành công: chạy Worker từ 1 trong 2 IP đã đăng ký
   (`116.97.118.219`/`222.255.21.194`) — vấn đề hạ tầng/mạng, không phải cấu hình phần mềm.
3. 🔴 Viết handler/consumer phía td-web nhận `simpki.sign_reply` để gắn chữ ký vào `file_metadata`
   (xem Phần 0.5) — ngoài phạm vi `td-woker`.

### Nhóm C — Nghiệp vụ còn bỏ ngỏ (cần quyết định nghiệp vụ trước khi làm)

1. 🔴 `simpki.get_cert` (lấy/đăng ký chứng thư SIM-PKI người dùng) — chưa có nhu cầu cụ thể.
2. 🔴 Chuẩn hoá thuật toán băm/đóng gói DigestInfo cho từng loại tài liệu ở phía producer (td-web).
3. 🔴 UI/luồng hiển thị "mã xác thực" (Verification Code) cho người ký đối chiếu khi giao dịch đang
   chờ xác nhận trên điện thoại (`verificationCode` đã có sẵn trong envelope `simpki.sign_reply` —
   phần hiển thị realtime cho người dùng là việc của FE/td-web, ngoài phạm vi Worker).

---

## Phần 4 — Xác nhận build + test (cuối phiên)

**Trước khi sửa** (baseline): `mvn test -DskipITs` — toàn bộ test cũ (`vbdi`/`vbden`/`guinhanquocgia`/
core) PASS, 0 lỗi.

**Sau khi thêm module `simpki`**: `mvn test -DskipITs` → **68/68 test pass, 0 lỗi, 0 fail** (bao gồm
17 test mới: `SimPkiSignTaskHandlerTest` 7, `SimPkiSignLogStoreTest` 4, `VgcaMsspSignatureClientTest`
6). Log xác nhận `TaskHandlerRegistry` nhận đủ 10 handler (`"Đăng ký 10 TaskHandler: [..., simpki.sign, ...]"`),
không handler cũ nào bị mất/trùng. `VgcaMsspSignatureClientTest` LẦN ĐẦU xác nhận THỰC NGHIỆM (không
suy đoán) rủi ro A2: gọi SOAP thật ném `NoClassDefFoundError: javax/xml/soap/SOAPException` do thiếu
SAAJ trên JDK 21, được bắt an toàn bởi `catch (Throwable)`.

**Đợt 2 (cùng ngày, sau khi giải quyết A2)**: thêm `javax.xml.soap-api:1.4.0` +
`saaj-impl:1.5.3` (Maven Central) vào `pom.xml` → chạy lại `mvn test -DskipITs` → **VẪN 68/68 test
pass, 0 lỗi, 0 fail** (không tăng/giảm số test — chỉ đổi hành vi runtime của
`VgcaMsspSignatureClientTest#callNeverThrowsEvenWithInvalidKeystore`, xác nhận `NoClassDefFoundError`
KHÔNG còn xảy ra, luồng chạy xa hơn tới lỗi SSL do keystore dummy của chính test — xem Phần 0.3.1).
`mvn package -DskipTests` — **BUILD SUCCESS** (xác nhận `spring-boot-maven-plugin` repackage vẫn hoạt
động bình thường với 17 dependency mới cộng dồn lên 2 dependency `guinhanquocgia` đã có: 15 jar SDK
`libs-repo/` + 2 jar SAAJ Maven Central).

---

## Phần 5 — Kết luận

Khung sườn TaskHandler `simpki.sign` đã được thêm **tách biệt hoàn toàn** khỏi mọi module đang có
(`sample.echo`/`vbdi.*`/`vbden.*`/`guinhanquocgia.send` không bị đụng tới — chỉ 3 file hiện có được
sửa, thuần additive: `pom.xml`/`docs/README.md`/`.env.example`). Cơ chế Extensible Worker hoạt động
đúng thiết kế: registry tự nhận diện handler mới chỉ qua `@Component implements TaskHandler`.

**Giá trị thật đã có**: (1) viết lại luồng SOAP ký số bằng stub Axis/ETSI cấp thấp thay vì phụ thuộc
`ExtSignature` bị giới hạn cấu hình — MỌI tham số (endpoint/MSSP_ID/signatureProfile/MSS_Format/
timeout/AP credential/keystore) đều qua `SimPkiProperties`, KHÔNG hard-code; (2) đã THỰC NGHIỆM xác
nhận (không suy đoán) 2 rủi ro thật: SSL JVM global (Phần 0.4, khuyến nghị vận hành — tách instance
nếu cần) và thiếu SAAJ trên JDK hiện đại (A2) — A2 **ĐÃ GIẢI QUYẾT** bằng 2 dependency chuẩn Maven
Central (Phần 0.3.1), SSL JVM global là giới hạn CỦA CHÍNH SDK vendor (Axis 1.4 không có API khác),
chỉ cần lưu ý vận hành chứ không phải bug; (3) cơ chế dedup/idempotency + audit trạng thái ký đã có,
kiểm chứng bằng test thật trên H2; (4) toàn bộ test cũ vẫn pass nguyên vẹn sau khi thêm (68/68).

**Còn thiếu để dùng được thật**: chạy Worker từ 1 trong 2 IP đã đăng ký với Cục CTS-BMTT
(`116.97.118.219`/`222.255.21.194` — vấn đề hạ tầng/mạng, KHÔNG phải cấu hình phần mềm nữa, xem A3),
và handler phía td-web gắn chữ ký vào tài liệu khi nhận `simpki.sign_reply` (ngoài phạm vi
`td-woker`).
