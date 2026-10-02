# TDSimPki — Proxy Backend (ký PDF qua SIM-PKI + chuyển đổi Office)

> **Ngày**: 21/09/2026, cập nhật 22/09/2026 — **Trạng thái**: ✅ Proxy 4 endpoint, ✅ cấu hình
> `app.simpki.*`, ✅ đã sửa bug thật khiến `/convert`/`/sign` báo 415 (xem §7), ⚠️ chưa xác nhận ký
> thật với thiết bị SIM thật qua đường HTTP này (chỉ verify bằng test tự động với gateway mock — xem
> `TDSimPki/docs/19-...md` §"Kiểm chứng"). Không sửa gì luồng `simpki.sign` message-queue hiện có của
> `td-woker` (khác hoàn toàn, xem `JAVA/td-woker/docs/17/18-...md`).

## 1. Vì sao cần lớp proxy này (không để FE gọi thẳng TDSimPki)

[TDSimPki](../../TDSimPki) (`C:\code_projects\HSCV\TDSimPki`) là service ký PDF qua SIM-PKI + chuyển
đổi Office→PDF, xác thực bằng `X-API-Key` + `X-Tenant-Id` (xem
`TDSimPki/docs/19-backend-webapi-pdf-checklist.md`). **Không bao giờ** để Frontend giữ/gửi
`X-API-Key` — biến `VITE_*` bị nhúng công khai vào bundle JS, ai cũng đọc được qua devtools, và một
khi lộ, bất kỳ ai cũng có thể tự gửi yêu cầu ký tới SỐ ĐIỆN THOẠI BẤT KỲ (rủi ro lừa đảo/spam SIM-PKI
thật). Vì vậy Backend Java này đứng giữa: giữ `X-API-Key` phía server, FE chỉ cần xác thực bình
thường (Keycloak/Tdtoken) như mọi endpoint khác của hệ thống.

Cùng mô hình 2-tầng `controller → use case → infrastructure client` đã dùng cho OnlyOffice (xem
`ONLYOFFICE_BE_INTEGRATION.md`) — package riêng `simpki`, không sửa bất kỳ controller/use case nghiệp
vụ nào đang chạy.

## 2. 3 endpoint mới, generic (không gắn module nghiệp vụ nào)

- `td-application/.../simpki/` — interface use case (`SubmitSimPkiSignatureUseCase`,
  `GetSimPkiSignStatusUseCase`, `DownloadSignedPdfUseCase`, `ConvertDocumentToPdfUseCase`) +
  request/response DTO (`SimPkiSignaturePlacement` khớp field TDPdfViewer 1:1).
- `td-infrastructure/.../simpki/` — `TdSimPkiProperties`, `TdSimPkiClient` (HTTP thuần JDK — module
  này KHÔNG có `spring-web`/`RestTemplate`, xem javadoc `HttpLgspTokenClient`), 4 impl use case,
  `MultipartRequestBuilder` (dựng multipart thủ công).
- `td-web/.../v1/SimPkiSignController.java` — endpoint dưới `/api/v1/simpki/`.

| Endpoint | Method | Mô tả |
|---|---|---|
| `/api/v1/simpki/sign` | `POST` (multipart: `file`, `msisdn`, `displayMessage`?, `signatures`? JSON) | Upload + ký gộp 1 lời gọi. Async ở TDSimPki — trả ngay `{simPkiFileId, status:"SIGNING"}` |
| `/api/v1/simpki/{simPkiFileId}/status` | `GET` | `{status: READY\|SIGNING\|SIGNED\|FAILED, errorDetail, signed}` |
| `/api/v1/simpki/{simPkiFileId}/download` | `GET` | Tải PDF (đã ký nếu `SIGNED`) |
| `/api/v1/simpki/convert` | `POST` (multipart: `file`) | Đồng bộ — Word/Excel/PPT → PDF, trả thẳng bytes |

Auth: `@PreAuthorize("hasAnyRole('USER','ADMIN','PRODUCT_MANAGER','BRAND_MANAGER')")` — giống hệt
`editor-config` của OnlyOffice, không route "gọi ngược từ service ngoài" nào ở đây (khác OnlyOffice
có thêm 2 route `content`/`callback` — TDSimPki không cần callback vào Backend này).

`simPkiFileId` là ID bản ghi PDF **phía TDSimPki** — KHÁC HOÀN TOÀN `documentId`/`fileId` của hệ
thống này, không dùng lẫn.

## 3. `signatures[]` — vị trí tem/chữ ký nhìn thấy (tuỳ chọn)

Field khớp 1:1 payload TDPdfViewer xuất ra (`page, x, y, width, height, imageBase64?, type?, text?,
color?`) — xem chi tiết đầy đủ (3 dạng nội dung: ảnh/viết tay, ghi chú dạng chữ, hoặc mặc định) ở
`TDSimPki/docs/19-backend-webapi-pdf-checklist.md` §3.1. Module này CHỈ chuyển tiếp nguyên vẹn
(`SimPkiSignaturePlacement` → JSON gửi TDSimPki), không diễn giải/quy đổi gì thêm.

## 4. Cấu hình (`app.simpki.*` / `SIMPKI_*` trong `JAVA/.env`)

```dotenv
SIMPKI_ENABLED=false          # bật khi có instance TDSimPki thật đang chạy
SIMPKI_BASE_URL=http://localhost:8091
SIMPKI_API_KEY=ThayDoiApiKeyNay123   # PHẢI khớp đúng WORKER_API_KEY của chính instance TDSimPki đó
SIMPKI_TENANT_ID=thanhhoa
```

**Nếu instance TDSimPki được deploy kèm subpath riêng** (`WORKER_API_BASE_PATH=/xyz` phía TDSimPki,
ví dụ để đi qua 1 reverse-proxy/ingress dùng chung) — `SIMPKI_BASE_URL` PHẢI bao gồm luôn subpath đó
(vd `http://192.168.2.17:8091/td-esign`), vì `TdSimPkiClient` chỉ ghép thẳng
`baseUrl + "/api/pdf/..."`, không tự biết/đọc `WORKER_API_BASE_PATH` từ instance đích. Thiếu subpath
→ mọi lời gọi `/convert`/`/sign`/`/status`/`/download` đều 404 (đã gặp thật 22/09/2026, instance thật
tại `192.168.2.17:8091` dùng subpath `/td-esign`).

`enabled=false` (mặc định) → mọi lời gọi 4 endpoint trên trả lỗi nghiệp vụ rõ ràng ("Tich hop
TDSimPki chua duoc cau hinh") thay vì cố kết nối rồi timeout mù mờ — cùng tiền lệ
`hosocv.lgsp.enabled`/`MAIL_SENDING_ENABLED`.

## 5. Người dùng đầu tiên (không phải người dùng duy nhất được thiết kế cho)

FE `ThanhHoaDEV` (icon "Ký SIM — PDF Viewer mới" ở Dự thảo, xem
`ThanhHoaDEV/src/utils/tdpdfviewer/README.md`) là caller đầu tiên, dùng `/convert` để mở doc/docx/
xls/xlsx trong TDPdfViewer và `/sign` + `/status` + `/download` để ký thật sau khi người dùng đặt vị
trí tem. Endpoint hoàn toàn generic — module khác (vbdi/vbden/hosocv...) có thể gọi thẳng theo đúng
hợp đồng ở mục 2, không cần sửa gì ở đây.

## 6. Việc CHƯA làm / rủi ro còn lại

- Chưa test end-to-end với thiết bị SIM thật qua ĐÚNG đường HTTP proxy này (đã test bằng
  `mvn test` với `SimPkiGatewayClient` mock ở cả 2 phía TDSimPki lẫn luồng gọi qua controller — xem
  `TDSimPki/src/test/java/com/td/worker/api/BackendApiIntegrationTest.java`
  `signAcceptsOptionalVisiblePlacementsAndReachesSignedThroughTheRealProviderBean`). Luồng
  `simpki.sign` message-queue (khác hẳn, xem `td-woker/docs/18`) ĐÃ ký thật thành công 18/09/2026 —
  chứng minh gateway MSSP hoạt động đúng, nhưng KHÔNG chứng minh riêng đường HTTP `/api/pdf/{id}/sign`
  này đã chạm MSSP thật.
- Chưa có rate limiting/CORS riêng cho 4 endpoint này (kế thừa cấu hình chung của `td-web`).
- Timeout ký (`worker.tasks.simpki.timeout-seconds`, mặc định 300s phía TDSimPki) dài hơn timeout
  HTTP thông thường — client (FE) PHẢI poll `/status`, không chờ đồng bộ 1 request duy nhất.

## 7. BUG THẬT đã gặp + fix (22/09/2026): `/convert` và `/sign` báo 415 dù request đúng chuẩn

**Triệu chứng:** FE gửi multipart hoàn toàn đúng chuẩn (đã verify tận `Content-Type` thật trong
DevTools: `multipart/form-data; boundary=----WebKitFormBoundary...`) tới
`https://qlvbv6.tandan.com.vn/api/v1/simpki/convert`, vẫn nhận về:
```json
{"status":false,"code":415,"message":"Dinh dang noi dung khong duoc ho tro","errorId":"..."}
```
`curl` gửi multipart CHUẨN tới CÙNG URL (không kèm token) chỉ nhận 401 (thiếu xác thực) — chứng minh
route/consumes của `SimPkiSignController` tự nó hoàn toàn đúng.

**Nguyên nhân THẬT:** `FormDataToJsonFilter` (`td-web/.../config/FormDataToJsonFilter.java`) — 1
filter TOÀN CỤC (`@Order(HIGHEST_PRECEDENCE + 10)`, áp dụng MỌI request POST/PUT/PATCH của TOÀN BỘ
`td-web`, đứng SAU Spring Security nên request có token hợp lệ mới bị filter này xử lý, giải thích vì
sao `curl` không kèm token không tái hiện được bug) — tự động chuyển MỌI multipart POST/PUT/PATCH
sang JSON để các endpoint khác nhận được cả JSON lẫn HTML-form/FormData mà không cần sửa controller,
TRỪ những path có chứa `"/files"` hoặc `"/upload"` (danh sách thủ công, cứng trong code). 2 endpoint
mới `/api/v1/simpki/convert` và `/api/v1/simpki/sign` KHÔNG khớp quy ước đặt tên đó → bị filter coi
là "form text-only": nó gọi `request.getParts()`, CHỦ Ý bỏ qua mọi part có `filename=` (tưởng đó là
file đính kèm phụ, không phải nội dung chính) — nhưng với 2 endpoint này, part có `filename=` CHÍNH
LÀ file PDF/Office cần xử lý! Kết quả: JSON rỗng `{}`, `Content-Type` bị ĐỔI THÀNH
`application/json`, khiến `consumes = MULTIPART_FORM_DATA_VALUE` của controller không còn khớp nữa →
`HttpMediaTypeNotSupportedException` → 415 — xảy ra ở TẦNG FILTER, TRƯỚC KHI code controller (kể cả
`getFileNameForConversion` phía FE) kịp chạy, nên hoàn toàn không liên quan tới bug tên file đã sửa
trước đó cùng ngày.

**Fix:** thêm `"/simpki/convert"` và `"/simpki/sign"` vào danh sách loại trừ
(`FILE_UPLOAD_SEGMENTS`) của `FormDataToJsonFilter`. Đã thêm `FormDataToJsonFilterTest.java` (test
mới, 3 case: 2 endpoint simpki phải pass-through nguyên vẹn, 1 endpoint KHÁC không nằm trong danh
sách vẫn phải bị convert sang JSON như cũ — tránh vô tình vô hiệu hoá toàn bộ filter).

**Bài học cho MỌI endpoint multipart-file-upload mới sau này ở `td-web`:** PHẢI thêm segment path
vào `FormDataToJsonFilter.FILE_UPLOAD_SEGMENTS`, KHÔNG chỉ định nghĩa `@RequestPart MultipartFile`
trong controller là đủ — filter này không tự suy luận từ annotation, chỉ so khớp chuỗi path thủ công.
Việc test qua `curl`/Postman KHÔNG kèm token thật dễ bỏ sót bug này (vì filter đứng sau xác thực) —
phải test với token hợp lệ (qua UI thật hoặc token thật) mới tái hiện được.

## 8. Định vị vị trí chữ ký theo tên người ký (29/09/2026)

Endpoint MỚI phục vụ tính năng **tự đặt chữ ký** ở modal Ký SIM (ThanhHoaDEV → TDPdfViewer):

| Endpoint | Method | Mô tả |
|---|---|---|
| `/api/v1/simpki/locate-signatures` | `POST` (multipart: `file`, `signerName`?, `signatureLocators`? JSON mảng regex, `occurrence`? FIRST\|LAST) | Đồng bộ, **không lưu tệp** ở TDSimPki (gọi bản stateless `POST /api/pdf/locate-signatures`). Trả `ApiResponse<[{regex, matchedText, page, x, y, width, height}]>` — PDF pt, gốc dưới-trái; **mảng rỗng = không tìm thấy** (không phải lỗi). |

- Tầng: `LocateSimPkiSignaturesUseCase` + `LocateSimPkiSignaturesRequest` + `SimPkiSignatureLocation`
  (td-application) → `LocateSimPkiSignaturesUseCaseImpl` + `TdSimPkiClient#locateSignatures`
  (td-infrastructure) → `SimPkiSignController#locateSignatures` (td-web).
- `signerName` → server tự dựng regex (`buildSignerNameRegex`): chuẩn hoá NFC, escape từng từ (tên là
  dữ liệu, không phải regex), `\s+` giữa các từ, nhánh IN HOA, ranh giới từ Unicode tường minh
  (`(?<![\p{L}\p{M}\p{N}])…(?![\p{L}\p{M}\p{N}])` — "Văn An" không khớp "Văn Anh"; KHÔNG dùng `\b` vì
  trượt khi tên có dấu câu), capture group 1 = phần tên. Mặc định `occurrence=LAST` khi tìm theo tên
  (khối chữ ký nằm cuối văn bản, tên có thể đã xuất hiện ở nội dung). Regex dài quá 256 ký tự (giới hạn
  TDSimPki) → bỏ nhánh IN HOA; vẫn quá → không gửi.
- Regex gửi TDSimPki dạng JSON **escape non-ASCII** (`\uXXXX`) — không phụ thuộc charset servlet phía
  TDSimPki dùng để đọc field text multipart. `MultipartRequestBuilder#addField` (mới) cho field text.
- `400 SIGNATURE_LOCATOR_NOT_FOUND` từ TDSimPki được đổi thành mảng rỗng tại `TdSimPkiClient`.
- `FormDataToJsonFilter.FILE_UPLOAD_SEGMENTS` đã thêm `"/simpki/locate-signatures"` (cùng bẫy 415 ở §7)
  + test `doFilter_shouldPassThroughUnchanged_forSimPkiLocateSignaturesMultipartUpload`.
- Test: `td-infrastructure` `LocateSimPkiSignaturesUseCaseImplTest` (8 case: regex tiếng Việt/khoảng
  trắng/IN HOA, ranh giới từ, escape + NFD→NFC, giới hạn độ dài, map kết quả + LAST mặc định, không tìm
  thấy → rỗng, lỗi client → failure, thiếu input không gọi TDSimPki).
- **Cần TDSimPki bản 29/09/2026 trở đi** (có route stateless — `TDSimPki/docs/27-...md`). Bản cũ trả
  404/405 → FE hiện "Không tự đặt được chữ ký", người dùng vẫn đặt tay như trước.
- **30/09/2026:** mặc định khi có `signerName` đổi từ `LAST` sang `ALL_PAGES` — mỗi trang có tên người
  ký trả 1 vị trí (lần xuất hiện cuối trong trang), FE đặt chữ ký ở TẤT CẢ các trang đó (tối đa 20).
- **30/09/2026 — chỉ đặt ở vị trí PHÙ HỢP:** khi tìm theo `signerName`, proxy gửi thêm điều kiện cho
  TDSimPki: tên phải **đứng riêng 1 dòng** + phía trên có **≥ 36pt trống** trong dải rộng 150pt
  (`DEFAULT_MIN_CLEARANCE_ABOVE_PT`/`DEFAULT_CLEARANCE_WIDTH_PT` ở `LocateSimPkiSignaturesUseCaseImpl`;
  ghi đè qua `LocateSimPkiSignaturesRequest.minClearanceAbovePt`, 0 = tắt). Tên nằm trong câu nội dung bị bỏ
  qua. Kết quả `SimPkiSignatureLocation` thêm `clearanceAbove`, `aloneOnLine`.
