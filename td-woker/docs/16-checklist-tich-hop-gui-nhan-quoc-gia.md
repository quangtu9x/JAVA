# 16 — Checklist tích hợp "Gửi nhận quốc gia" (VPCP/VXP, eDXML QCVN 102) vào td-woker

> Tài liệu **checklist** (khác các doc kiến trúc `01`-`15`) — dùng để theo dõi việc tích hợp năng lực
> đã có sẵn ở project cũ `docs/td-guinhanquocgia/` (Maven project Java thuần, KHÔNG phải Spring Boot,
> đã dùng thật để gửi/nhận văn bản qua Trục liên thông văn bản quốc gia VPCP/VXP, chuẩn eDXML
> QCVN 102) vào `td-woker` theo đúng cơ chế Extensible Worker
> (`docs/promt-Extensible-Worker-Architecture.txt`). Đánh dấu trạng thái theo legend chuẩn của dự án:
> ✅ đã xong | 🟡 có 1 phần | 🔴 chưa làm | ⛔ không làm (có lý do) | ⚠️ rủi ro cần ưu tiên.
> Ngày tạo: 09/09/2026.
>
> **Ràng buộc cứng đã tuân thủ xuyên suốt tài liệu này**: CHỈ thêm mới (jar dependency mới, file
> config mới, 1 package/module TaskHandler MỚI tách biệt hoàn toàn) — KHÔNG sửa/xoá/đổi hành vi bất
> kỳ TaskHandler/class/config nào đang có (`sample.echo`, `vbdi.reply`, `vbdi.delivery`,
> `vbdi.revoke`, `vbden.forward_unit*`, RabbitMQ/Kafka listener hiện có, migration Flyway hiện có).
> Đã build + chạy toàn bộ test cuối phiên để xác nhận — xem Phần 4.

---

## Phần 0 — Khảo sát: `td-guinhanquocgia` cung cấp năng lực gì

### 0.1 Build/parse eDXML (SDK `EdXML102-0.0.3.jar`, package `com.vnpt.xml`)

| Việc | Class/method (đã xác nhận bằng `javap` trên jar thật, không đoán) |
|---|---|
| Build văn bản điện tử (edoc) | `com.vnpt.xml.ed.builder.EdXmlBuilder` kế thừa `BaseXmlBuilder.build(Base, String) -> Content` |
| Build thông điệp trạng thái (status) | `com.vnpt.xml.status.builder.StatusXmlBuilder.build(Status, String) -> Content` |
| Parse edoc | `com.vnpt.xml.ed.parser.EdXmlParser.getInstance().parse(InputStream) -> Ed` |
| Parse status | `com.vnpt.xml.status.parser.StatusXmlParser.parse(InputStream) -> Status` |
| Kết quả build | `com.vnpt.xml.base.Content(File, String)` — `getContent()`=file `.edxml`, `getHashCode()`=SHA-256 |

Dựng đối tượng `Ed`/`Header`/`MessageHeader`/`Bussiness`/... là builder pattern thủ công, nhiều
constructor (`MessageHeader` có 12 tham số) — mọi field (tổ chức, người ký, chữ ký số) mẫu demo đều
**hard-code** (`EdXMLBuild.java`), CHƯA có ánh xạ với dữ liệu nghiệp vụ thật của td-web (xem Phần 0.5).

### 0.2 Gọi SDK VXP gửi/nhận văn bản (SDK `SDKVXP-2.0.0.jar`, package `com.vpcp.services`)

| Việc | Method (đã xác nhận bằng `javap`) |
|---|---|
| Cấu hình client | `VnptProperties(String endpoint, String systemId, String secret, String storePathDir, int maxConnection, int retry)` |
| Danh bạ đơn vị liên thông | `AgencyServiceImp.getAgenciesList/registerAgency/deleteAgency/getSynchronizeUnit` |
| **Gửi văn bản đi** | `KnobstickServiceImp.sendEdoc(String jsonHeader, String edxmlFilePath) -> SendEdocResult` (`getStatus/getErrorCode/getErrorDesc/getDocID`) |
| Nhận danh sách văn bản đến | `KnobstickServiceImp.getReceivedEdocList(String jsonHeader) -> GetReceivedEdocResult` |
| Lấy 1 văn bản | `KnobstickServiceImp.getEdoc(String jsonHeader) -> GetEdocResult` |
| Cập nhật trạng thái xử lý | `KnobstickServiceImp.updateStatus(String jsonHeader) -> GetChangeStatusResult` |
| Tra cứu văn bản đã gửi | `KnobstickServiceImp.getSentEdocList(String jsonHeader) -> GetSendEdocResult` |

### 0.3 13 file `.edxml` mẫu trong `resources/` — ý nghĩa nghiệp vụ

| File | `BussinessDocType`/`StatusCode` | Ý nghĩa |
|---|---|---|
| `edoc_new.edxml` | 0 | Văn bản điện tử MỚI |
| `edoc_revocation.edxml` | 1 | THU HỒI văn bản |
| `edoc_update.edxml` | 2 | CẬP NHẬT văn bản |
| `edoc_replacement.edxml` | 3 | THAY THẾ văn bản |
| `edoc.edxml` | — | Mẫu chung/nội dung đính kèm demo |
| `status_inbox_ 01.edxml` | 01 | Đã đến (văn thư CHƯA xử lý) |
| `status_rejection_02.edxml` | 02 | Từ chối tiếp nhận |
| `status_acceptance_03.edxml` | 03 | Đã tiếp nhận |
| `status_assignment_04.edxml` | 04 | Phân công xử lý |
| `status_processing_05.edxml` | 05 | Đang xử lý |
| `status_finish_06.edxml` | 06 | Hoàn thành |
| `status_cancellation_13.edxml` | 13 | Huỷ bỏ |
| `status_change-acceptance_15.edxml` | 15 | Chấp nhận yêu cầu cập nhật/thu hồi |
| `status_change-rejection_16.edxml` | 16 | Từ chối yêu cầu cập nhật/thu hồi |
| `status.edxml` | — | Mẫu chung |

**Đối chiếu quan trọng**: 6 mã trạng thái 01/02/03/04/05/06 **trùng đúng ý nghĩa** với 6 mã
`StatusCode.java` (Domino) mà `vbdi.reply` (`td-woker`) đã port sẵn (`VbdiTaskConstants.STATUS_01_DA_DEN`
… `STATUS_06_HOAN_THANH`, xem `docs/11-vbdi-reference.md`) — **đây là tín hiệu tốt**: mô hình trạng
thái 2 chiều đã chứng minh chạy được ở vbdi có thể tái dùng Ý TƯỞNG (không phải code) cho module này;
13/16 và 15 (change-acceptance/rejection) là 2 mã KHÔNG có tương đương ở vbdi — cần thiết kế riêng khi
làm nghiệp vụ thật (🔴 chưa làm, xem Phần 2 nhóm 3).

### 0.4 Cấu hình bảo mật/kết nối (`resources/collaboration.properties` + doc `05`)

| Property gốc | Ý nghĩa | Giá trị mẫu trong file |
|---|---|---|
| `client.protocol` | Giao thức gọi API | `HTTPs` |
| `client.max-connection` | Số connection HTTP tối đa | `10` |
| `client.max-error-retry` | Số lần retry khi lỗi | `4` |
| `systemid` | Mã hệ thống/trung tâm liên thông (VPCP cấp) | *(trống — CHƯA có thật)* |
| `secret` | Khoá bí mật xác thực client (VPCP cấp) | *(trống — CHƯA có thật)* |
| `store-path-dir` | Thư mục lưu file `.edxml` tải về | `d:/test` (đường dẫn Windows tuyệt đối — KHÔNG dùng nguyên) |
| `service.endpoint` | URL endpoint dịch vụ Trung tâm | *(trống — CHƯA có thật)* |

Về chữ ký số: **KHÔNG có keystore/PKCS#12 thật** trong project cũ — chỉ có dữ liệu mẫu hard-code
(`KeyInfo(new X509Data("CN=user05", "MIIFqTCCBJG"))`, thuật toán `rsa-sha1`/SHA-1 — **đã lỗi thời,
không hợp lệ pháp lý**, doc `09` đã ghi nhận rủi ro này). ⚠️ Khi triển khai thật PHẢI thay bằng chứng
thư số hợp lệ (PKCS#12/HSM) + thuật toán băm mạnh hơn — nằm ngoài phạm vi khung sườn này.

### 0.5 Mapping PostgreSQL đề xuất (doc `07`) — có khớp schema `td-woker`/`td-infrastructure` không?

Doc `07` đề xuất 5 bảng MỚI (`vpcp_agency`, `vpcp_edoc_outbox`, `vpcp_edoc_inbox`, `vpcp_status_log`,
`vpcp_edoc_file`) — **đây là bảng NGHIỆP VỤ ĐẦY ĐỦ**, dự kiến thuộc về `td-web`/`td-infrastructure`
(mỗi tenant 1 DB, liên kết `documents`/`file_metadata`/`tenants` — giống cách `vbdi_send_log_documents`
thuộc về td-web chứ không phải `td-woker`). **KHÔNG khớp trực tiếp** với bất kỳ bảng nào `td-woker`
đang tự quản (`worker_units`/`worker_messages`/`worker_vbdi_reply_log`/`worker_vbdi_delivery_received`)
— đây là **2 tầng khác nhau**, đúng nguyên tắc "Worker chỉ tự quản dedup/checkpoint, KHÔNG sở hữu dữ
liệu nghiệp vụ" (docs/06-multi-tenant.md, docs/11-vbdi-reference.md). Kết luận: 5 bảng đề xuất ở doc
`07` là việc của **td-web/td-infrastructure** khi triển khai nghiệp vụ đầy đủ (🔴 ngoài phạm vi
`td-woker`) — `td-woker` (phạm vi checklist này) chỉ cần 1 bảng dedup/audit RIÊNG rất rút gọn (xem
Phần 1 mục 5).

**Đính chính quan trọng cho ràng buộc "Flyway tự áp dụng cho mọi tenant"**: ghi chú chung của dự án
("Flyway tự migrate mọi tenant lúc khởi động") áp dụng cho `td-web`/`td-application` — **KHÔNG áp
dụng cho `td-woker`**. Đã xác nhận trực tiếp trong code: `WorkerSchemaMigrator` (javadoc nguyên văn)
*"Worker chỉ migrate database CỦA MÌNH — KHÔNG bao giờ migrate database tenant (td-web là người duy
nhất migrate tenant)"*. `td-woker` chỉ có **1 database riêng** (`worker.db`, vd `tdworker`), Flyway
của nó chỉ chạy 1 lần cho DB đó, không lặp theo tenant. Vì vậy migration Flyway mới ở mục 5 dưới đây
áp dụng cho DATABASE RIÊNG CỦA WORKER (tự động ở lần khởi động kế tiếp, đúng cơ chế hiện tại của
`WorkerSchemaMigrator`) — KHÔNG phải "mỗi tenant 1 bản sao".

### 0.6 Cơ chế Extensible Worker — chỗ nối chính xác

Đã xác nhận qua `docs/promt-Extensible-Worker-Architecture.txt` + đọc trực tiếp code:

- Interface: `com.td.worker.core.task.TaskHandler` — `String taskType()` +
  `UnitResult execute(TaskContext, JsonNode) throws Exception`.
- Registry: `com.td.worker.core.task.TaskHandlerRegistry` — Spring tự thu thập MỌI bean
  `implements TaskHandler` trong context (`List<TaskHandler>` constructor injection), map theo
  `taskType()`. Thêm task mới = thêm 1 `@Component implements TaskHandler`, KHÔNG sửa Core.
- Convention đặt tên: `<module>.<action>` (`vbdi.delivery`, `vbdi.reply`, `vbden.forward_unit`...).
  → Namespace mới dùng đúng convention này: **`guinhanquocgia.send`** (đã đăng ký ở bản scaffold
  này — xem Phần 1). `guinhanquocgia.receive` (nhận văn bản đến, bản chất POLLING chứ không phải
  PUSH qua broker) **CHƯA đăng ký** (🔴 xem Phần 2 nhóm 3).

---

## Phần 1 — Danh sách hạng mục đã triển khai (khung sườn)

| # | Hạng mục | Việc đã làm | Trạng thái |
|---|---|---|---|
| 1 | Copy 2 jar SDK | `EdXML102-0.0.3.jar` + `SDKVXP-2.0.0.jar` copy từ `docs/td-guinhanquocgia/lib/` vào `JAVA/td-woker/libs/guinhanquocgia/` (MỚI, không đụng thư mục nào khác) | ✅ |
| 2 | Khai báo dependency trong `pom.xml` | ~~Thêm 2 `<dependency>` scope `system`, `systemPath`~~ — **sửa 09/09/2026 đợt 2**: Maven cảnh báo `systemPath` "unresolvable by dependent projects", chuyển sang repository file cục bộ `libs-repo/` (cài bằng `install:install-file -DlocalRepositoryPath=libs-repo`, commit jar+POM synthesize sẵn vào repo) + `<repository>` trong `pom.xml` + 2 `<dependency>` bình thường (không còn `scope`/`systemPath`) — CHỈ THÊM, không sửa dependency nào đang có, build vẫn tái lập chỉ bằng checkout code | ✅ |
| 3 | Config module mới | `com.td.worker.tasks.guinhanquocgia.GuiNhanQuocGiaProperties` (`@Component @ConfigurationProperties(prefix="worker.tasks.guinhanquocgia")`) — file MỚI, KHÔNG sửa `WorkerProperties.java`/`application.yml`/`WorkerApplication.java` hiện có (xem giải thích kỹ thuật ở mục "Quyết định thiết kế" bên dưới) | ✅ |
| 4 | TaskHandler mới | `com.td.worker.tasks.guinhanquocgia.GuiNhanQuocGiaSendTaskHandler` — taskType `guinhanquocgia.send`, tự đăng ký qua `TaskHandlerRegistry` (đã xác nhận log: `"Đăng ký 9 TaskHandler: [..., guinhanquocgia.send, ...]"`) | ✅ (khung sườn) |
| 5 | Dedup/audit DB | Bảng `worker_guinhanquocgia_send_log` — Flyway MỚI `db/migration-worker/V2__create_worker_guinhanquocgia_send_log.sql` (file mới, không sửa V1) cho production/flyway-mode + tự `CREATE TABLE IF NOT EXISTS` runtime trong `GuiNhanQuocGiaSendLogStore` cho test/H2-script-mode (không sửa `worker-schema-h2.sql`/`worker-schema-postgres.sql` hiện có) | ✅ |
| 6 | Cổng gọi SDK VXP thật | `GuiNhanQuocGiaGatewayClient` (interface) + `VpcpGuiNhanQuocGiaGatewayClient` (implementation thật, gọi `KnobstickServiceImp.sendEdoc`) + `NoopGuiNhanQuocGiaGatewayClient` (mặc định, an toàn) + `GuiNhanQuocGiaGatewayConfig` (`@Bean` có điều kiện) | ✅ (khung sườn — xem rủi ro #A) |
| 7 | Test mới | `GuiNhanQuocGiaSendTaskHandlerTest` (7, mock collaborator), `GuiNhanQuocGiaSendLogStoreTest` (4, H2 thật), `VpcpGuiNhanQuocGiaGatewayClientTest` (2) — tổng 13 test mới, tất cả PASS | ✅ |
| 8 | Build eDXML từ dữ liệu nghiệp vụ thật | Ánh xạ tổ chức/người ký/chữ ký số từ `documents`/`categories` sang `MessageHeader`/`Bussiness` | 🔴 chưa làm |
| 9 | `guinhanquocgia.receive` (nhận văn bản đến) | TaskHandler/scheduled job polling `getReceivedEdocList` | 🔴 chưa làm |
| 10 | Đồng bộ danh bạ đơn vị (`AgencyServiceImp`) | — | 🔴 chưa làm |
| 11 | Endpoint/credential thật từ VPCP | — | 🔴 chưa làm (phụ thuộc bên ngoài) |
| 12 | Chứng thư số hợp lệ (PKCS#12/HSM) | — | 🔴 chưa làm (phụ thuộc bên ngoài) |

### Danh sách file mới (đầy đủ đường dẫn tuyệt đối)

```
JAVA/td-woker/libs/guinhanquocgia/EdXML102-0.0.3.jar                 (copy nguyên từ docs/td-guinhanquocgia/lib/ — nguồn install-file)
JAVA/td-woker/libs/guinhanquocgia/SDKVXP-2.0.0.jar                   (copy nguyên từ docs/td-guinhanquocgia/lib/ — nguồn install-file)
JAVA/td-woker/libs-repo/com/vnpt/xml/edxml102/0.0.3/edxml102-0.0.3.jar        (mới đợt 2 — repository file cục bộ, thay scope "system")
JAVA/td-woker/libs-repo/com/vnpt/xml/edxml102/0.0.3/edxml102-0.0.3.pom        (POM synthesize bởi install-file -DgeneratePom=true)
JAVA/td-woker/libs-repo/com/vpcp/services/sdkvxp/2.0.0/sdkvxp-2.0.0.jar      (mới đợt 2)
JAVA/td-woker/libs-repo/com/vpcp/services/sdkvxp/2.0.0/sdkvxp-2.0.0.pom      (mới đợt 2)
JAVA/td-woker/src/main/resources/db/migration-worker/V2__create_worker_guinhanquocgia_send_log.sql
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaTaskConstants.java
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaProperties.java
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaSendPayload.java
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaSendOutcome.java
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaGatewayClient.java
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/NoopGuiNhanQuocGiaGatewayClient.java
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/VpcpGuiNhanQuocGiaGatewayClient.java
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaGatewayConfig.java
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaSendLogStore.java
JAVA/td-woker/src/main/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaSendTaskHandler.java
JAVA/td-woker/src/test/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaSendTaskHandlerTest.java
JAVA/td-woker/src/test/java/com/td/worker/tasks/guinhanquocgia/GuiNhanQuocGiaSendLogStoreTest.java
JAVA/td-woker/src/test/java/com/td/worker/tasks/guinhanquocgia/VpcpGuiNhanQuocGiaGatewayClientTest.java
```

### File hiện có ĐÃ SỬA (chỉ 1 — additive, không đổi nội dung cũ)

```
JAVA/td-woker/pom.xml   — CHỈ thêm 1 <repositories><repository> (trỏ libs-repo/) + 2 <dependency> mới
                           (đợt 2: bỏ scope "system" theo cảnh báo Maven, chuyển sang repository file
                           cục bộ) kèm comment giải thích, KHÔNG sửa/xoá bất kỳ dependency/plugin nào
                           đang có.
```

Không sửa `WorkerProperties.java`, `application.yml`, `WorkerApplication.java`,
`worker-schema-h2.sql`, `worker-schema-postgres.sql`, `V1__create_worker_tables.sql`, hay bất kỳ
class/test nào thuộc `vbdi`/`vbden`/`sample`/`core`/`infrastructure`/`transport` đang có.

### Mapping `collaboration.properties` → `GuiNhanQuocGiaProperties` (Spring, prefix `worker.tasks.guinhanquocgia`)

| Property gốc (`collaboration.properties`) | Field Spring (`GuiNhanQuocGiaProperties`) | Biến môi trường (relaxed-binding tự động, KHÔNG cần khai báo thêm trong `application.yml`) |
|---|---|---|
| `client.protocol` | `protocol` | `WORKER_TASKS_GUINHANQUOCGIA_PROTOCOL` |
| `client.max-connection` | `maxConnection` | `WORKER_TASKS_GUINHANQUOCGIA_MAXCONNECTION` |
| `client.max-error-retry` | `maxErrorRetry` | `WORKER_TASKS_GUINHANQUOCGIA_MAXERRORRETRY` |
| `systemid` | `systemId` | `WORKER_TASKS_GUINHANQUOCGIA_SYSTEMID` |
| `secret` | `secret` | `WORKER_TASKS_GUINHANQUOCGIA_SECRET` |
| `store-path-dir` | `storePathDir` | `WORKER_TASKS_GUINHANQUOCGIA_STOREPATHDIR` |
| `service.endpoint` | `endpoint` | `WORKER_TASKS_GUINHANQUOCGIA_ENDPOINT` |
| *(mới)* | `enabled` (mặc định `false`) | `WORKER_TASKS_GUINHANQUOCGIA_ENABLED` |

### Mã nguồn tham khảo → class mới (viết lại theo DI, KHÔNG copy nguyên)

| Class tham khảo (`docs/td-guinhanquocgia/src/main/java/com/vpcp/example/`) | Class MỚI viết lại | Ghi chú |
|---|---|---|
| `ServiceTest.java` case 7 (`sendEdoc`) | `VpcpGuiNhanQuocGiaGatewayClient.callSdk()` | Bỏ `static main`/hard-code; SỬA lỗi cú pháp JSON demo gốc (dòng ~64: `"mail":cc""` sai định dạng) bằng escape đúng |
| `ServiceTest.getJsonHeader()` case 7 | `VpcpGuiNhanQuocGiaGatewayClient.buildSendJsonHeader()` | Viết lại bằng nối chuỗi CÓ escape, không dùng `StringBuffer` nối tay thô |
| `EdXMLBuild.java` (4 method build edoc) | *(chưa dùng ở bản scaffold — xem hạng mục #8)* | Cần ánh xạ dữ liệu nghiệp vụ thật trước khi viết lại |

---

## Phần 2 — Rủi ro cần lưu ý (⚠️) và việc còn lại theo nhóm ưu tiên

### Nhóm A — ⚠️ Rủi ro cần biết TRƯỚC khi bật `enabled=true` (đã tự thực nghiệm, không suy đoán)

- **A1 — Thiếu dependency transitive của SDK**: CỐ Ý CHƯA thêm `guava-14.0.1`/`guice-4.2.0`/
  `gson-2.2`/`jdom2-2.0.3`/`httpclient-4.5.6`/`xercesImpl-2.12.0` vào `pom.xml` — lý do: `guava
  14.0.1` (2013) rất có thể xung đột phiên bản với `guava` mới hơn mà `testcontainers` (dependency
  test hiện có của `td-woker`) kéo theo transitive; khai báo `guava 14.0.1` trực tiếp có thể làm
  Maven "nearest wins" hạ cấp guava xuống bản cũ cho TOÀN BỘ project, có nguy cơ làm hỏng
  Testcontainers (không kiểm chứng được ngay vì môi trường hiện tại KHÔNG có Docker — 8 test class
  Testcontainers báo "Tests run: 0", không phải "pass", nên rủi ro này KHÔNG bị bắt bởi lần chạy test
  này). **Đã THỰC NGHIỆM xác nhận hậu quả**: gọi `VpcpGuiNhanQuocGiaGatewayClient.sendEdoc()` thật
  (test `VpcpGuiNhanQuocGiaGatewayClientTest#realSdkCallNeverThrowsEvenWithoutTransitiveJars`) ném
  `NoClassDefFoundError: org/apache/http/message/BasicHeader` — đã bị bắt bởi `catch (Throwable)` và
  chuyển thành `GuiNhanQuocGiaSendOutcome.failed("SDK_ERROR", ...)`, KHÔNG làm sập test/Worker. Kết
  luận: **TaskHandler an toàn ngay cả khi thiếu jar** (trả FAILED rõ ràng), nhưng **`enabled=true`
  chưa dùng được thật** cho tới khi giải quyết A1 — xem Phần 2 nhóm B mục 1.
- **A2 — Ký số SHA-1/chưa có chứng thư hợp lệ**: xem Phần 0.4 — không được dùng cấu hình mẫu cho môi
  trường thật.
- **A3 — `store-path-dir` mặc định rỗng**: SDK gốc dùng đường dẫn tuyệt đối Windows (`d:/test`) —
  `GuiNhanQuocGiaProperties.storePathDir` mặc định để rỗng (`""`), PHẢI cấu hình đường dẫn hợp lệ trên
  máy chủ Linux production trước khi bật `enabled=true` (SDK có thể ghi file xuống đó).

### Nhóm B — Nên làm trước khi bật `enabled=true` lần đầu (không phụ thuộc quyết định nghiệp vụ)

1. 🔴 **Giải quyết A1**: quyết định 1 trong 2 hướng — (a) copy toàn bộ jar còn lại của
   `docs/td-guinhanquocgia/lib/` vào `libs/guinhanquocgia/` + khai báo thêm scope `system` cho từng
   jar (KHÔNG kéo transitive Maven Central, tránh đụng guava của testcontainers), rồi chạy lại toàn
   bộ test **CÓ Docker** để xác nhận Testcontainers không bị ảnh hưởng; hoặc (b) nếu dự án có
   Nexus/Artifactory nội bộ, cài 2+N jar này qua `mvn deploy:deploy-file` lên đó và dùng
   `<dependencyManagement>` ép version guava mới hơn thắng thế — xem "Quyết định đóng gói jar" dưới.
2. 🔴 Xác nhận endpoint/systemId/secret thật từ VPCP/VNPT (phụ thuộc bên ngoài, không tự làm được).
3. 🔴 Thay chứng thư số mẫu SHA-1 bằng chứng thư hợp lệ khi có yêu cầu ký số thật (nếu nghiệp vụ dùng
   `EdXMLBuild`/chữ ký — bản scaffold hiện tại CHƯA build eDXML nên chưa cần ngay).

### Nhóm C — Nghiệp vụ còn bỏ ngỏ (cần quyết định nghiệp vụ trước khi làm)

1. 🔴 **Build eDXML từ dữ liệu nghiệp vụ thật** (hạng mục #8, Phần 1) — cần chốt ánh xạ tổ chức/
   người ký với `documents`/`categories`/tenant hiện có của td-web trước — đây là việc LỚN nhất còn
   lại, đúng như doc `09-rui-ro-va-cong-viec-con-bo-ngo.md` (`docs/td-guinhanquocgia`) đã ghi nhận.
2. 🔴 `guinhanquocgia.receive` (nhận văn bản đến) — bản chất POLLING (`getReceivedEdocList`), khác
   hẳn cơ chế PUSH qua RabbitMQ/Kafka của `vbdi`/`vbden` — cần thiết kế `@Scheduled` riêng (tần suất,
   idempotency theo `doc_id` Trung tâm trả về) trước khi viết 1 TaskHandler thật.
3. 🔴 Đồng bộ danh bạ đơn vị liên thông (`AgencyServiceImp`) — chưa có nhu cầu nghiệp vụ cụ thể được
   xác nhận, chưa làm.
4. 🔴 5 bảng nghiệp vụ đề xuất ở doc `07` (`vpcp_agency`/`vpcp_edoc_outbox`/`vpcp_edoc_inbox`/
   `vpcp_status_log`/`vpcp_edoc_file`) — thuộc phạm vi `td-web`/`td-infrastructure`, KHÔNG phải
   `td-woker` (xem Phần 0.5) — ⛔ ngoài phạm vi checklist này, cần 1 checklist riêng khi làm.
5. 🔴 Mã trạng thái 13 (huỷ bỏ)/15/16 (chấp nhận/từ chối thay đổi) — KHÔNG có tương đương ở `vbdi`,
   cần thiết kế state machine riêng khi làm nghiệp vụ `guinhanquocgia.receive`/cập nhật trạng thái.

### Quyết định đóng gói jar (đánh giá đã thực hiện, sửa 09/09/2026 đợt 2)

**Bản đầu** đã chọn scope `system` trỏ thẳng `${project.basedir}/libs/guinhanquocgia/*.jar` — build
chạy được (1 lần đầu), nhưng Maven cảnh báo rõ ràng lúc build:
`'dependencies.dependency.systemPath' ... should not point at files within the project directory,
... will be unresolvable by dependent projects`. Vấn đề thật: dependency scope `system` KHÔNG được
đóng gói vào transitive metadata — nếu sau này có project Java khác `<dependency>` vào `td-woker`
(dùng như thư viện thay vì chỉ chạy standalone), 2 jar này sẽ KHÔNG resolve được cho project đó.

**Đã sửa** — chuyển sang **repository file cục bộ trong chính project** (`libs-repo/`, layout Maven
chuẩn + POM synthesize sẵn), KHÔNG dùng `~/.m2` cá nhân, KHÔNG cần Nexus/Artifactory nội bộ:

```
mvn install:install-file -Dfile=libs/guinhanquocgia/EdXML102-0.0.3.jar \
  -DgroupId=com.vnpt.xml -DartifactId=edxml102 -Dversion=0.0.3 -Dpackaging=jar \
  -DgeneratePom=true -DlocalRepositoryPath=libs-repo
# (tương tự cho SDKVXP-2.0.0.jar → com.vpcp.services:sdkvxp:2.0.0)
```

rồi khai báo `<repository><url>file://${project.basedir}/libs-repo</url></repository>` +
`<dependency>` bình thường (không `scope`/`systemPath`). Ưu điểm so với bản đầu: hết cảnh báo Maven,
dependency resolve đúng chuẩn kể cả khi có project khác phụ thuộc `td-woker`, vẫn commit thẳng
`libs-repo/` vào repo (build tái lập 100% chỉ bằng `git clone` + `mvn package`, không cần chạy lại
lệnh `install:install-file` trên máy khác — khác `~/.m2` cá nhân là nơi KHÔNG commit được).
`libs/guinhanquocgia/*.jar` (bản gốc, chưa qua install-file) vẫn giữ lại làm nguồn tham chiếu/để chạy
lại lệnh trên nếu cần tái tạo `libs-repo/` từ đầu — không còn được `pom.xml` tham chiếu trực tiếp.

🟡 Nếu sau này dự án dựng Nexus/Artifactory nội bộ (cho mục đích khác, không chỉ riêng module này),
có thể chuyển tiếp sang `mvn deploy:deploy-file` lên đó — không bắt buộc, `libs-repo/` đã giải quyết
đúng vấn đề cảnh báo Maven mà không cần thêm hạ tầng ngoài.

---

## Phần 3 — Việc còn lại theo nhóm ưu tiên (tóm tắt nhanh)

- [ ] 🔴 **Ưu tiên cao**: giải quyết rủi ro A1 (dependency transitive SDK) trước khi bật
      `enabled=true` ở BẤT KỲ môi trường nào (kể cả dev) — nếu không, mọi lần gọi `sendEdoc` thật sẽ
      luôn FAILED với `SDK_ERROR`/`NoClassDefFoundError` (đã thực nghiệm xác nhận).
- [ ] 🔴 **Ưu tiên cao**: có endpoint/systemId/secret thật từ VPCP/VNPT (phụ thuộc bên ngoài).
- [ ] 🔴 **Ưu tiên trung bình**: thiết kế + build eDXML từ dữ liệu nghiệp vụ thật (hạng mục lớn nhất,
      cần quyết định ánh xạ tổ chức/người ký).
- [ ] 🔴 **Ưu tiên trung bình**: thiết kế `guinhanquocgia.receive` (polling, khác PUSH).
- [ ] ⛔ **Ngoài phạm vi `td-woker`**: 5 bảng nghiệp vụ đầy đủ (doc `07`) — thuộc `td-web`/`td-infrastructure`.
- [ ] 🔴 **Thấp**: đồng bộ danh bạ đơn vị, mã trạng thái 13/15/16, chứng thư số hợp lệ (chỉ cần khi
      thật sự build+ký eDXML).

---

## Phần 4 — Xác nhận build + test (cuối phiên 09/09/2026)

**Trước khi sửa** (khảo sát baseline, chưa đụng code): `mvn test -DskipITs` → **24/24 test pass, 0
lỗi** (5 test class thật: `WorkerApplicationContextTest` 1, `JdbcCheckpointStoreTest` 9,
`EnvelopeParserTest` 6, `MessageFlowRunnerTest` 2, `VbdiDeliveryTaskHandlerTest` 6) + 8 test class
Testcontainers báo "Tests run: 0" (môi trường không có Docker — `NpipeSocketClientProviderStrategy`
lỗi API version, KHÔNG phải fail).

**Sau khi thêm module `guinhanquocgia`**: `mvn test -DskipITs` → **37/37 test pass, 0 lỗi, 0 fail**
(24 test cũ + 13 test mới: `GuiNhanQuocGiaSendTaskHandlerTest` 7, `GuiNhanQuocGiaSendLogStoreTest` 4,
`VpcpGuiNhanQuocGiaGatewayClientTest` 2). 8 test class Testcontainers vẫn báo "Tests run: 0" — không
đổi so với baseline (môi trường vẫn không có Docker, không phải do thay đổi của phiên này).

**Sự cố phát hiện + đã tự sửa trong phiên**: lần chạy đầu tiên sau khi thêm
`NoopGuiNhanQuocGiaGatewayClient`/`VpcpGuiNhanQuocGiaGatewayClient` dùng `@Component` +
`@ConditionalOnMissingBean`/`@ConditionalOnProperty` trực tiếp làm **`WorkerApplicationContextTest`
(test CŨ) FAILED** (`NoSuchBeanDefinitionException: GuiNhanQuocGiaGatewayClient`) — do
`@ConditionalOnMissingBean` trên `@Component` quét bằng component-scan KHÔNG đảm bảo thứ tự xử lý
(đúng cảnh báo chính thức của Spring Boot). Đã sửa ngay bằng cách chuyển sang `@Configuration` +
`@Bean` (`GuiNhanQuocGiaGatewayConfig`) — pattern được Spring Boot đảm bảo đúng thứ tự. Sau khi sửa,
chạy lại xác nhận **toàn bộ test cũ pass lại 100%**, không còn vi phạm ràng buộc cứng.

`mvn package -DskipTests` cũng **BUILD SUCCESS** (xác nhận `spring-boot-maven-plugin` repackage vẫn
hoạt động bình thường với 2 dependency scope `system` mới).

---

## Phần 5 — Kết luận

Khung sườn TaskHandler `guinhanquocgia.send` đã được thêm **tách biệt hoàn toàn** khỏi mọi module
đang có (`sample.echo`/`vbdi.*`/`vbden.*` không bị đụng tới — đã xác nhận bằng `git diff`/kiểm tra thủ
công chỉ 1 file cũ bị sửa là `pom.xml`, thuần additive). Cơ chế Extensible Worker hoạt động đúng thiết
kế: registry tự nhận diện handler mới chỉ qua `@Component implements TaskHandler`, không cần sửa
Worker Core.

**Giá trị thật đã có**: (1) 2 jar SDK đã tích hợp vào build Maven CLI (project gốc `docs/td-guinhanquocgia`
thực ra KHÔNG tự build được bằng `mvn package` vì thiếu khai báo 2 jar này trong `pom.xml` — chỉ build
được qua Eclipse `.classpath`; `td-woker` giờ build được bằng `mvn` thuần); (2) đã xác nhận THỰC
NGHIỆM (không suy đoán) rằng gọi SDK thật khi thiếu dependency transitive sẽ ném `NoClassDefFoundError`
nhưng được xử lý an toàn, không sập ứng dụng; (3) cơ chế dedup/idempotency + audit trạng thái gửi đã
có, kiểm chứng bằng test thật trên H2; (4) toàn bộ 24 test cũ vẫn pass nguyên vẹn sau khi thêm — xác
nhận ràng buộc "không làm lỗi chức năng đã có" được tuân thủ.

**Còn thiếu để dùng được thật**: dependency transitive của SDK (rủi ro A1), endpoint/credential thật
từ VPCP, và quan trọng nhất — thiết kế ánh xạ dữ liệu nghiệp vụ thật sang eDXML (hạng mục lớn nhất,
cần quyết định nghiệp vụ trước khi viết code, không nên đoán). `guinhanquocgia.receive` (nhận văn bản
đến) hoàn toàn chưa làm, cần thiết kế polling riêng biệt với cơ chế PUSH hiện có.
