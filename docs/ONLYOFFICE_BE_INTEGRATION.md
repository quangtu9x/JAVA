# OnlyOffice — Backend + wiring vào dự thảo (thử nghiệm)

> **Ngày**: 15/09/2026 — **Trạng thái**: ✅ Backend build/ký JWT config, ✅ endpoint `callbackUrl`
> lưu file, ✅ icon test riêng ở màn "Dự thảo" — **THỬ NGHIỆM**, chạy **song song** hoàn toàn với
> "Sửa tệp" cũ qua BTEAdapter (`tdeditor.js#TDSuaTepDinhKem`), không thay thế, không đổi hành vi cũ.
> Tiếp theo phần FE component đã làm ở `JAVA/docs/ONLYOFFICE_FE_INTEGRATION.md`.

## 1. 2 cách sửa file, chọn qua cấu hình

| | Cách cũ (BTEAdapter) | Cách mới (OnlyOffice) |
|---|---|---|
| Icon | "Sửa tệp" (`fa fa-edit`) — luôn hiện | "Sửa trực tuyến (OnlyOffice - thử nghiệm)" (`fa-solid fa-cloud-arrow-up`) — CHỈ hiện khi bật cờ |
| Bật/tắt | Luôn bật | `VITE_ENABLE_ONLYOFFICE_EDITOR=true\|false` (FE `.env`/`.env.development`) |
| Yêu cầu máy khách | Cài `BTEAdapter` (desktop, ws `127.0.0.1:58201`) | Chỉ cần trình duyệt (Document Server render qua iframe) |
| Điều kiện hiện | Mọi tệp doc/docx/xls/xlsx | Cùng định dạng NHƯNG chỉ tệp đã lưu qua Java/MinIO (`file.file_id` tồn tại) |
| Code | `ThanhHoaDEV/src/utils/tdbte/tdeditor.js` | `ThanhHoaDEV/src/app/components/OnlyOfficeEditor/OnlyOfficeEditorModal.jsx` (component core `OnlyOfficeEditor/` không đổi) |

2 nhánh **không dùng chung state/hàm nào** ở `iconlistduthao.jsx` (file Files/icon thật của module
dự thảo — xem mục 3) — tắt cờ `VITE_ENABLE_ONLYOFFICE_EDITOR` là icon mới biến mất hoàn toàn, "Sửa
tệp" cũ không bị ảnh hưởng gì.

## 2. Backend — 3 endpoint mới, độc lập hoàn toàn

Toàn bộ code mới nằm trong package `onlyoffice` riêng ở cả 3 module — **không sửa** bất kỳ
controller/use case/entity nghiệp vụ nào đang chạy (chỉ ĐỌC lại `GetFileUseCase` có sẵn):

- `td-application/.../onlyoffice/` — interface use case + request/response.
- `td-infrastructure/.../onlyoffice/` — `OnlyOfficeProperties`, 2 service JWT (xem mục 3), 2 impl use case.
- `td-web/.../v1/OnlyOfficeController.java` — 3 endpoint dưới `/api/v1/onlyoffice/`.

| Endpoint | Ai gọi | Auth |
|---|---|---|
| `GET /api/v1/onlyoffice/documents/{documentId}/files/{fileId}/editor-config` | FE (người dùng thật đang đăng nhập) | Keycloak/Tdtoken bình thường, role USER/ADMIN/PRODUCT_MANAGER/BRAND_MANAGER |
| `GET /api/v1/onlyoffice/content?token=...` | Document Server (tải nội dung tệp để mở) | Token nội bộ ngắn hạn ở query, KHÔNG qua Keycloak/Tdtoken |
| `POST /api/v1/onlyoffice/callback?token=...` | Document Server (gọi ngược lưu tệp đã sửa) | Token nội bộ ngắn hạn ở query + verify JWT riêng của Document Server (nếu `ONLYOFFICE_JWT_ENABLED=true`) |

`editor-config` trả nguyên payload `OnlyOfficeConfig` (đã ký JWT ở field `token` nếu Document
Server bật JWT) — FE (`OnlyOfficeEditorModal.jsx`) truyền thẳng vào prop `config` của component
`OnlyOfficeEditor` có sẵn, không sửa gì component đó.

## 3. Vì sao 2 secret khác nhau + 1 `SecurityFilterChain` riêng

- `ONLYOFFICE_JWT_SECRET` — secret **chia sẻ với chính Document Server thật** (đã cấu hình sẵn ở
  `10.10.10.107:8081`) — Backend dùng để (a) ký field `config.token` trả cho FE, (b) verify JWT
  Document Server đính kèm khi gọi `callback`.
- `ONLYOFFICE_INTERNAL_TOKEN_SECRET` — secret **riêng của Backend**, Document Server hoàn toàn
  không biết — ký token ngắn hạn (mặc định 240 phút, đổi qua `ONLYOFFICE_CONTENT_TOKEN_TTL_MINUTES`/
  `ONLYOFFICE_CALLBACK_TOKEN_TTL_MINUTES`) nhúng sẵn trong `document.url`/`callbackUrl` — đây là lớp
  xác thực DUY NHẤT bảo vệ 2 route `content`/`callback` vì Document Server không gửi được
  `Authorization`/`Tdtoken` của người dùng thật.
- **`SecurityConfig` có 2 `SecurityFilterChain`** (`@Order(1)` riêng cho đúng 2 path
  `/api/v1/onlyoffice/content` + `/api/v1/onlyoffice/callback`, `@Order(2)` là chain chính giữ
  NGUYÊN VẸN như trước). Lý do bắt buộc tách chain thay vì chỉ thêm `permitAll()` vào chain chính:
  Document Server có thể gửi JWT của chính nó trong header `Authorization` (`ONLYOFFICE_JWT_HEADER`,
  mặc định cũng là `"Authorization"`) — nếu vẫn dùng chung chain chính, `BearerTokenAuthenticationFilter`
  (Keycloak) sẽ cố verify JWT đó như JWT Keycloak, thất bại, trả 401 NGAY LẬP TỨC trước khi tới được
  `permitAll()`/Controller. Chain riêng không có `oauth2ResourceServer`/Tdtoken/`TenantFilter` nên
  né hoàn toàn xung đột này — tenant cho 2 route này do Controller tự set qua
  `TenantContext.runAsWithResult(...)` (đọc từ chính token nội bộ, không qua `TenantFilter`).

## 4. Lưu tệp — ghi ĐÈ đúng vị trí cũ, không tạo bản ghi mới

Khi Document Server callback với `status=2` (MustSave) hoặc `6` (force-save khi vẫn đang mở),
Backend tải nội dung mới rồi **ghi đè đúng `file_path`/MinIO object hiện có** + cập nhật
`file_size`/`uploaded_at` trên CHÍNH dòng `file_metadata` cũ (`SaveOnlyOfficeCallbackUseCaseImpl`)
— KHÔNG tạo `fileId` mới. Lý do: mọi nơi khác (danh sách tệp, tải xuống, ký số, lịch sử...) đang
tham chiếu theo `fileId` cố định — tạo dòng mới (như `UploadFileUseCase` vẫn làm cho luồng khác) sẽ
làm các tham chiếu đó trỏ vào bản CŨ, gây lỗi "chỉnh sửa xong nhưng chỗ khác vẫn thấy bản cũ".

## 5. Bắt buộc cấu hình trước khi test (`JAVA/.env`)

```
ONLYOFFICE_DOCSERVER_URL=http://10.10.10.107:8081   # đã có sẵn
ONLYOFFICE_JWT_ENABLED=true                          # đã có sẵn, khớp Document Server
ONLYOFFICE_JWT_SECRET=...                            # đã có sẵn, khớp Document Server
ONLYOFFICE_INTERNAL_TOKEN_SECRET=...                 # MỚI — đã điền giá trị dev, đổi khi lên thật
ONLYOFFICE_CALLBACK_BASE_URL=                        # MỚI — ĐANG ĐỂ TRỐNG, XEM CẢNH BÁO DƯỚI
```

**`ONLYOFFICE_CALLBACK_BASE_URL` để trống sẽ KHÔNG hoạt động được trong đa số trường hợp** — đây là
địa chỉ mà chính Document Server (`10.10.10.107:8081`) dùng để gọi ngược vào Backend Java qua mạng
để tải/lưu tệp. Để trống, Backend tự suy từ request hiện tại (host/port FE đang gọi tới, ví dụ
`http://localhost:8111` lúc dev) — **chỉ đúng nếu Document Server thật sự truy cập được đúng địa chỉ
đó qua mạng**, thường KHÔNG đúng khi Backend chạy trên máy dev cục bộ (`localhost` không phải là địa
chỉ mà server 10.10.10.107 gọi tới được). **Phải set biến này thành IP/host thật của Backend mà
`10.10.10.107` truy cập được** (ví dụ `http://10.10.10.x:8111`) trước khi bấm icon test — nếu không,
Document Server sẽ báo lỗi không tải được tài liệu ngay khi mở.

## 6. Cách test

1. Set đúng `ONLYOFFICE_CALLBACK_BASE_URL` ở `JAVA/.env` (mục 5) rồi khởi động lại `td-web`.
2. FE: đảm bảo `VITE_ENABLE_ONLYOFFICE_EDITOR=true` (đã bật sẵn ở `.env.development`).
3. Mở 1 dự thảo đang soạn (chưa ban hành/lưu trữ) có tệp đính kèm `.docx`/`.xlsx` tải lên qua hệ
   thống mới (không phải tệp cũ từ Domino) — icon mây (☁) xanh sẽ xuất hiện cạnh icon bút chì cũ.
4. Bấm icon mây → modal mở, tải cấu hình, hiển thị trình soạn thảo OnlyOffice. Sửa nội dung, đợi vài
   giây (autosave/forcesave đã bật ở `customization.forcesave`) hoặc đóng modal — Document Server sẽ
   gọi `callback` để lưu.
5. Tải lại tệp qua icon "Tải tệp"/"Đọc tệp đính kèm" cũ để xác nhận nội dung đã cập nhật.

## 7. Bugfix 15/09/2026 — "File không tìm thấy" khi mở icon từ tab "Quá trình xử lý"

`BuildOnlyOfficeEditorConfigUseCaseImpl` lúc đầu tra tệp bằng CẢ `documentId` lẫn `fileId` (giống
`GetFileUseCase` — bắt buộc khớp cả 2). Phát hiện qua log thực tế: khi mở icon từ tab "Quá trình xử
lý" (`duthaofile.jsx` → `ListIconFiles`/`iconlistduthao.jsx`), `resdata` truyền vào component đó là
1 dòng **lịch sử xử lý** (`draft_process_documents`), KHÔNG phải chính dự thảo — nên `resdata.id` FE
gửi lên KHÁC `document_id` thật của tệp trong `file_metadata`, dù tệp hoàn toàn hợp lệ (đã kiểm
chứng trực tiếp trong DB). Đây là ambiguity CÓ SẴN của component `ListIconFiles` (nhiều màn khác
nhau truyền `resdata` với ý nghĩa khác nhau — xem `tdeditor.js#TDSuaTepDinhKem` vẫn nhận riêng
`parentdocid`/`docid`) — khả năng "Tải tệp"/"Đọc tệp đính kèm" cũ cũng gặp y hệt ở đúng ngữ cảnh này
(chưa xác nhận, không thuộc phạm vi sửa lần này).

**Cách sửa**: `BuildOnlyOfficeEditorConfigUseCaseImpl` giờ tra tệp CHỈ theo `fileId` (khoá chính,
luôn duy nhất) qua thẳng `FileMetadataJpaRepository.findById(fileId)`, rồi lấy `document_id` THẬT từ
chính dòng tìm được — không tin theo `documentId` phía FE gửi lên nữa. `documentId` trong URL
(`/api/v1/onlyoffice/documents/{documentId}/files/{fileId}/editor-config`) giữ nguyên cho đúng quy
ước REST nhưng KHÔNG còn dùng để lọc — chỉ `fileId` mới quyết định. `content`/`callback` không cần
sửa vì đã nhận `documentId` ĐÚNG (embed từ bước build config này) trong token nội bộ, không tự suy
lại từ FE.

## 8. Bugfix 15/09/2026 — `KeyLengthException: The secret length must be at least 256 bits`

Bản đầu `OnlyOfficeDocumentJwtService` ký/verify JWT bằng Nimbus (`MACSigner`/`MACVerifier`) — thư
viện này BẮT BUỘC secret HS256 ≥ 32 byte (256 bit) theo đúng khuyến nghị RFC 7518, ném
`KeyLengthException` ngay khi khởi tạo nếu ngắn hơn. Secret THẬT đang cấu hình trên Document Server
dev (`ONLYOFFICE_JWT_SECRET=ThayDoiMatKhauBaoMatNay123`, 26 byte) ngắn hơn mức này — không thể đổi
secret (phải khớp đúng Document Server), nên đổi thư viện ký: `OnlyOfficeDocumentJwtService` giờ tự
build/verify JWT compact serialization bằng `javax.crypto.Mac("HmacSHA256")` (JCE chuẩn, KHÔNG giới
hạn độ dài khoá — HMAC tự pad 0 nội bộ với khoá ngắn hơn block size, đây chỉ là lựa chọn nghiêm ngặt
hơn của Nimbus chứ không phải giới hạn thật của thuật toán). Vẫn đúng chuẩn JWT
(`base64url(header).base64url(payload).base64url(hmac)`), tương thích 100% với Document Server thật
(dùng thư viện `jsonwebtoken` Node.js, vốn không áp giới hạn này).

**Cập nhật (cùng ngày, fix triệt để thay vì chỉ 1 trường hợp)**: `OnlyOfficeAccessTokenService`
(secret RIÊNG của Backend — `ONLYOFFICE_INTERNAL_TOKEN_SECRET`) LÚC ĐẦU vẫn dùng Nimbus, tức vẫn có
thể ném CÙNG lỗi `KeyLengthException` nếu ai đó cấu hình secret này ngắn (placeholder ban đầu ở
`.env.example` cũng chỉ ~20 ký tự — rủi ro thật vì đây là secret do NGƯỜI VẬN HÀNH BACKEND tự chọn,
không bị ràng buộc phải khớp hệ thống ngoài như secret Document Server). Đã đổi NỐT service này sang
cùng cách tự ký/verify bằng `javax.crypto.Mac` — cả 2 secret liên quan OnlyOffice trong toàn hệ
thống giờ đều không còn giới hạn độ dài khoá, không chỉ riêng trường hợp gặp lỗi lúc test.

## 9. Còn lại ngoài phạm vi lần này

- Không ghi "Vết hiệu chỉnh"/lịch sử soạn thảo khi sửa qua OnlyOffice (luồng BTEAdapter cũ có hỏi "Ý
  kiến xử lý" rồi ghi log — luồng OnlyOffice hiện chỉ ghi đè file, không hỏi/không log) — cần làm
  riêng nếu quyết định đưa OnlyOffice ra khỏi giai đoạn thử nghiệm.
- Mới wiring vào module **dự thảo** (`iconlistduthao.jsx`) — các module khác (vbden/vbdi/vbcddh...)
  chưa có icon này, cần lặp lại đúng pattern ở mục 1 cho từng module khi có yêu cầu.
- Chưa có cấu hình `ONLYOFFICE_CALLBACK_BASE_URL`/`ONLYOFFICE_DOCSERVER_URL` riêng cho production.

---

## Cập nhật 25/09/2026: lưu bản sửa thành TỆP MỚI, không ghi đè tệp gốc

**Yêu cầu người dùng:** "sửa file ở OnlyOffice → tôi muốn lưu vết và file mới, không lưu đè vào file cũ,
có thể tham khảo chức năng sửa file cũ (bte)".

**Hành vi mới** (tham khảo luồng "Sửa tệp" BTE, `ThanhHoaDEV/src/utils/tdbte/tdeditorJavaFile.js`):

| Bước | Thành phần | Chi tiết |
|---|---|---|
| Mở trình soạn thảo | `BuildOnlyOfficeEditorConfigUseCaseImpl#buildDocumentKey` | `key` thêm hậu tố `_e<số bản đã sửa ra từ tệp gốc>`. Sau mỗi lần lưu xong, lần mở sau có key mới, tức phiên mới; người cùng mở lúc chưa ai lưu vẫn chung phiên. |
| Callback status 2/6 | `SaveOnlyOfficeCallbackUseCaseImpl` | Lần đầu của phiên: tạo dòng `file_metadata` **mới** (cùng `document_id`, cùng thư mục MinIO, `source_file_id` = tệp gốc, `edit_session_key` = `payload.key`), tên `<tên gốc trước dấu _>_<username>_<ddMMyyyyHHmmss>.<đuôi>` giống BTE. Các lần sau trong cùng phiên ghi đè đúng tệp mới đó, không tạo trùng. **Tệp gốc không bị đụng tới.** |
| FE chờ lưu xong | `GET /api/v1/onlyoffice/files/{fileId}/edited-file?key=` (`GetOnlyOfficeEditedFileUseCase`) | Trả `{file_id, name, document_id, size}` khi phiên đã lưu ra tệp mới, `data=null` nếu chưa. |
| FE sau khi có tệp mới | `OnlyOfficeEditorModal.jsx#logSavedAsNewFile` | Nếu là dự thảo và mở từ danh sách phân loại (`tepbanhanh`/`tepkemtheo`) có tệp cũ: đổi tên tệp cũ thành tệp mới trong danh sách đó (`luuDuThao`). Sau đó ghi vết quá trình xử lý (`ganKemTepDuThao`): "Đã sửa tệp "X" qua OnlyOffice (giữ nguyên bản gốc), lưu thành tệp mới "Y", thay thế ở tệp văn bản phát hành. Ý kiến: …". |

- **Schema:** migration `V1.0.65__Add_File_Edit_Source_Tracking.sql` thêm `file_metadata.source_file_id` và
  `edit_session_key`.
- **FE:** thời gian chờ tăng lên khoảng 30 giây (20 × 1,5s), vì Document Server thường gọi callback khoảng
  10 giây sau khi phiên đóng. Hết thời gian mà chưa có tệp mới thì hiện cảnh báo "Chưa xác nhận được tệp
  mới…"; tệp vẫn được lưu khi callback tới, chỉ thiếu bước đổi danh sách phân loại và ghi vết.
- **Module khác dự thảo** (dùng chung `Files/index.jsx`): chỉ có tệp mới, không đổi danh sách phân loại và
  không ghi vết, vì vết quá trình hiện chỉ có API cho dự thảo.
- **Kiểm chứng:**
  - `SaveOnlyOfficeCallbackUseCaseImplTest` (4 test): tạo tệp mới, không ghi tệp gốc; cùng phiên thì ghi
    tiếp đúng tệp; tệp gốc không tồn tại thì lỗi; quy tắc đặt tên.
  - Migration chạy thật trên `h05_01_sokhcn` trong transaction rồi ROLLBACK.
  - **Chưa thử với Document Server thật.**

### Điều chỉnh 25/09/2026 (2): vết gọn + tệp hiện dưới vết + không cắt tên

Làm theo tiền lệ của luồng ký số (`ThanhHoaDEV/src/utils/tdesign/tdsignJavaFile.js`):

- **Vết ở "Quá trình xử lý"** chỉ ngắn gọn: `Đã sửa`, có ý kiến thì thành `Đã sửa. Ý kiến: …`.
- **Chuỗi chi tiết** (sửa từ tệp nào, thay bằng tệp nào, ở đâu) lưu vào `file_metadata.description` của tệp
  mới, để sau này tra lại được đã sửa ở tệp nào.
- **Tệp mới hiện ngay dưới vết**, đọc/tải được như gắn kèm tệp thủ công:
  - Endpoint mới `POST /api/v1/onlyoffice/files/{fileId}/link-process`, body `{processId, description}`
    (`LinkOnlyOfficeEditedFileToProcessUseCase`), gắn `process_id` = id vết vừa ghi.
  - Chỉ nhận tệp do sửa trực tuyến tạo ra (`source_file_id` có giá trị) và vết thuộc cùng dự thảo.
- **Bugfix tên tệp bị cắt:** bản đầu cắt tên tại dấu `_` đầu tiên như BTE, nên
  "phan_cong_ct_…_final.docx" thành "phan_…". Nay giữ nguyên tên gốc:
  `<tên gốc đầy đủ>_<username>_<ddMMyyyyHHmmss>.<đuôi>`. Nếu sửa tiếp một bản đã sửa thì lần ngược về tệp gốc
  ban đầu qua `source_file_id`, nên không chồng nhiều hậu tố.
- **Test:** `SaveOnlyOfficeCallbackUseCaseImplTest` (5), `LinkOnlyOfficeEditedFileToProcessUseCaseImplTest` (3).
