# 11 — VBDI Reference Implementation

## 1. Phạm vi

VBDI là **module/use case THAM KHẢO ĐẦU TIÊN** để kiểm chứng Worker Platform
(docs/promt.txt: "VBDI chỉ là module/use case tham khảo đầu tiên"). Worker Core hoàn toàn
generic; toàn bộ nghiệp vụ VBDI nằm gọn trong `com.td.worker.tasks.vbdi`.

## 2. Luồng 2 chiều (kế thừa docs/architecture/08 + 11)

```
Đơn vị GỬI (tenant A)                    Đơn vị NHẬN (tenant B)
  td-web: SendVbdiDocumentUseCase
    → publish vbdi.delivery envelope ───────►  Worker: vbdi.delivery task
        (sendLogId, documentId, sender...)        • ghi bảng dedup worker_vbdi_delivery_received
                                                   • publish vbdi.reply "Đã đến" (status 01) ngược
       ◄───────────────────────────────────────  (routing key = senderReplyRoutingKey)
  Worker: vbdi.reply task
   (chạy trong tenant A — đơn vị gửi ghi nhận)
   → bảng worker_vbdi_reply_log (idempotent theo unit_id — xem bugfix (5) §4)
```

## 3. Task vbdi.delivery — payload

`VbdiDeliveryPayload` khớp bảng field docs/architecture/11 §4.1:

`sendId, sendLogId, documentId, senderTenantId, senderIdentifier, senderOrganName,
senderReplyRoutingKey, sendType, recipientGroup, recipientIdentifier, recipientName,
recipientMailin, statusLabel, opinion, documentSnapshot{objectKey,sha256,...}`

Xử lý của `VbdiDeliveryTaskHandler`:

| Trường hợp | Hành động |
|---|---|
| Nơi nhận nội bộ (`internal_unit`/`coordinator`/`contact_unit`, có tenant) | Mở pool tenant đích qua `TenantDataSourceManager`; tạo bảng `worker_vbdi_delivery_received` (Worker TỰ QUẢN, không đụng schema td-web); `INSERT ... ON CONFLICT (send_log_id) DO NOTHING` = idempotent; trả `success()` (unit `DONE`) |
| **`contact_unit` (cơ quan/đơn vị KHÁC thuộc tỉnh) — 06/09/2026** | NGOÀI dedup, tạo THẬT 1 bản ghi "văn bản đến chờ vào sổ" trong bảng `documents` (schema `BusinessDocument` td-web đang dùng — INSERT trực tiếp qua JDBC thuần, KHÔNG gọi lại `TiepNhanVbdenUseCase`/phụ thuộc `td-application`, xem `VbdenIncomingDocumentWriter`). Dedup + văn bản nằm CHUNG 1 transaction DB tenant (`DataSourceTransactionManager`) — crash/lỗi giữa chừng ROLLBACK CẢ HAI, không để lại trạng thái nửa vời. `internal_unit` (đơn vị nội bộ CỦA CHÍNH cơ quan gửi, không "thuộc tỉnh") và `coordinator` (định danh nhận là username, không phải mã tổ chức) CHƯA áp dụng — vẫn chỉ dedup như cũ (quyết định đã chốt với người dùng, sửa lại cùng ngày sau khi nhầm nhóm ban đầu — xem addendum bên dưới). Cờ `worker.tasks.vbdi.create-incoming-document` (mặc định bật) tắt được để rollback vận hành. **Bổ sung 07/09/2026**: `documentSnapshot` nay mang thêm 10 field mô tả (`draftor`/`signer`/`signer_position`/`creator`/`delivery_method`/`document_type`/`sector`/`confidentiality`/`page_amount`/`document_replace_retrieve`) forward từ văn bản đi gốc, và `priority` không còn hard-code "Thường" — xem `AddVbdiSendLogUseCase.publishDeliveryMessage` (td-application, nguồn dữ liệu) + `VbdenIncomingDocumentWriter` (nơi ghi). Chi tiết đối chiếu field/hàng đợi FE + quyết định đã chốt: `docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md` addendum 06/09/2026 (3) và addendum 07/09/2026 |
| Nơi nhận ngoài tỉnh (`external_unit`) | **Không** ghi "Đã đến", **không** trả `success()` — chưa có gateway liên thông thật (đúng quyết định docs/analysis/07 §7: giả lập tích hợp SAI hơn là không làm). Trả `UnitResult.waitingDependency(...)` → unit ở trạng thái `WAITING_DEPENDENCY` (không tiêu `maxAttempts`, message vẫn ACK an toàn ở broker); `RetryPoller` tự kiểm tra lại theo `worker.core.dependency-retry-interval` (mặc định 5 phút) cho tới khi gateway được triển khai — xem [07 §2](07-idempotency-checkpoint.md) + [14-load-resilience.md](14-load-resilience.md) |
| Tùy chọn (chỉ nhánh nội bộ) | Kiểm tra `objectKey` tồn tại trên MinIO (`worker.tasks.vbdi.verify-object-exists`) |
| Tùy chọn (chỉ nhánh nội bộ) | Publish phản hồi "Đã đến" (status 01) về đơn vị gửi theo `senderReplyRoutingKey` (khi Rabbit bật) |

## 4. Task vbdi.reply — payload

`VbdiReplyPayload`: `sendLogId, documentId, statusCode (01-06), statusLabel, statusDetails,
recipientIdentifier, recipientName, senderTenantId, recipientGroup, actorDisplayName`.

`VbdiReplyTaskHandler` chạy **trong tenant của ĐƠN VỊ GỬI** (bên nhận phản hồi), ghi vào bảng
`worker_vbdi_reply_log` (PK `unit_id` — bugfix 07/09/2026 (5), xem dưới; TRƯỚC ĐÓ là
`(send_log_id, status_code)`, SAI cho mã có thể lặp lại hợp lệ) = idempotent) **và** (bổ sung
07/09/2026, cùng transaction, chỉ khi lần đầu — chưa dedup trùng) append 1 dòng MỚI vào bảng nghiệp
vụ `vbdi_send_log_documents` ("Nhật ký gửi nhận" — `parent_id`/`recipient_*` lấy TỪ CHÍNH dòng gốc
theo `sendLogId`, không dùng lại giá trị relay qua payload) — trước đó Worker chỉ ghi bảng tự quản,
"Nhật ký gửi nhận" chưa từng thấy bất kỳ trạng thái phản hồi nào.

**Bugfix 07/09/2026 (5)** — báo lỗi người dùng: "Trả lại" lần 2/3 không ghi vết, văn bản không mất
khỏi danh sách dù bấm lại nhiều lần. Dedup theo `(send_log_id, status_code)` SAI vì `send_log_id` cố
định suốt đời văn bản — lần thao tác THỨ 2 (statusCode giống lần đầu, vd bấm "Trả lại" lại) bị coi
nhầm là redelivery của lần đầu. Khoá ĐÚNG là `TaskContext#unitId` (sinh mới ngẫu nhiên mỗi lần
`PublishVbdiReplyStatusUseCase` publish, giữ nguyên khi redeliver ĐÚNG message đó) — đã đổi PK bảng
sang `unit_id`, kèm `migrateOldSchemaIfNeeded` tự DROP+tạo lại bảng cũ (chỉ dedup tự quản, không mất
dữ liệu nghiệp vụ). Chi tiết:
`docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md` addendum 07/09/2026 (10).

6 mã trạng thái giữ đúng `StatusCode.java` của Domino (docs/architecture/11 §3.1):
01 Đã đến, 02 Từ chối tiếp nhận, 03 Đã tiếp nhận, 04 Phân công xử lý, 05 Đang xử lý, 06 Hoàn thành.
**Đã port broker 2 chiều đầy đủ cho CẢ 6 mã** (07/09/2026, hoàn thiện 05/06 ở addendum (6), 02 ở
addendum (7)) — 01 tự động khi tạo văn bản đến thành công (`VbdiDeliveryTaskHandler`, đã sửa bug
routing nghiêm trọng khiến message trước đó bị exchange âm thầm huỷ — xem docs/architecture/11
addendum 07/09/2026 (4)); 02 khi "Từ chối tiếp nhận" (`TuChoiTiepNhanVbdenUseCase`, MỚI); 03 khi văn
thư "vào sổ" (`LuuVanBanDenUseCase`); 04/05 khi phân công/chuyển xử lý (`ChuyenXuLyVbdenUseCase`, 05
CHỈ bắn ở LẦN ĐẦU phân công — status trước đó = "0"); 06 khi 1 người "Kết thúc xử lý" phần việc của
họ (`KetThucXuLyUseCase`, bắn VÔ ĐIỀU KIỆN mỗi lần thành công, đúng Domino thật). Tất cả qua
`PublishVbdiReplyStatusUseCase` (td-application).

**`buildContent` (`VbdiReplyTaskHandler`)** dựng cột "Nội dung" (`content`) đúng công thức từng mã
đối chiếu Domino thật (không còn ghi trơn `statusDetails` producer gửi): 01 nối `"ID: ..."` sau nhãn;
03 TỰ dựng `"Đơn vị " + recipientName + " đã đồng ý yêu cầu"` (bỏ qua `statusDetails` producer, dùng
`recipientName` đã tra từ dòng gốc); 04 nối danh sách người được giao trong ngoặc; 06 nối ý kiến sau
dấu hai chấm; "tralai" (xem dưới) TỰ dựng `"Đơn vị " + recipientName + " đã trả lại văn bản"` (bỏ
qua statusDetails, giống 03); 02/05 giữ nhãn trơn (Domino không có opinion riêng cho các mã này).

**Bổ sung 07/09/2026 (2) — phát hiện quan trọng, sửa lại hiểu nhầm ở trên**: STATUS_02 THẬT ra publish
từ `ReplyDocumentOut.java` (package `vn.com.tandan.xsp.services.vbdi`, nhưng `databasetype` là THAM
SỐ FE truyền vào — gọi với `databasetype=vbden` thì thao tác THẲNG trên văn bản đến, KHÔNG phải flow
"văn bản đi" tách biệt như đoán ban đầu ở addendum (4)/(6)). Đã port qua `TuChoiTiepNhanVbdenUseCase`
(td-application, MỚI) — gộp 2 endpoint Domino RIÊNG (`ReplyDocumentOut` rồi
`CapNhatVanBanDen?action=khongvaoso`) cho CÙNG 1 nút bấm thành 1 giao dịch Java atomic. Nhánh
`type=="vaoso"` của `ReplyDocumentOut` (xử lý văn bản "thay thế"/"thu hồi") VẪN CHƯA port — tính năng
KHÁC, lớn hơn, ngoài phạm vi. Chi tiết đầy đủ:
`docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md` addendum 07/09/2026 (7).

**Bugfix 07/09/2026 (3) — quyết định ở (2) SAI, người dùng sửa lại**: nhánh `type=="tralai"` ("Từ
chối/Trả lại", KHÁC "Không vào sổ" — 2 khái niệm bị gộp nhầm 1 nhãn ở lần port trước) CŨNG PHẢI
publish broker giống 01/03, KHÔNG được bỏ qua chỉ vì `status_code="tralai"` phi chuẩn — đối chiếu lại
`StatusProcess.java` xác nhận "tralai" đi qua ĐÚNG cơ chế `response_status` như mọi mã khác, chỉ khác
literal string thay vì số. Đã thêm `VbdiTaskConstants.STATUS_TRA_LAI`/`LABEL_TRA_LAI`,
`buildContent` case `"tralai"` (nội dung cố định, bỏ qua statusDetails — đúng Domino), và
`markOriginalVbdiDocumentReplied` (MỚI) — RIÊNG nhánh "tralai" còn ghi ngược `is_document_reply=1`
lên CHÍNH văn bản `vbdi` gốc phía đơn vị gửi (worker đã chạy trong tenant đó, JDBC thuần qua toán tử
JSONB `||`). Chi tiết: `docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md` addendum
07/09/2026 (8).

**Bugfix 07/09/2026 (4)** — `markOriginalVbdiDocumentReplied` bản đầu UPDATE nhắm bảng `documents`
(bảng DÙNG CHUNG của BL-101/vbden) — SAI, module `vbdi` có bảng vật lý RIÊNG `vbdi_documents` (xem
`VbdiDocument.java` — `@Table(name = "vbdi_documents")`, yêu cầu người dùng "tách văn bản đi khỏi
bảng dùng chung" đã có từ trước). Cấu trúc cột giống hệt (`dynamic_fields` JSONB/`document_type`),
chỉ tên bảng khác — đã sửa. Đây là nguyên nhân của log WARN "khong tim thay van ban vbdi goc" MỌI
LẦN + văn bản không mất khỏi danh sách phía đơn vị gửi (xem `ViewQueryService.VIEW_VBDI_REPLY_REFUSE`
đọc đúng `is_document_reply` để quyết định hiển thị). Chi tiết:
`docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md` addendum 07/09/2026 (9).

## 5. Tái dùng các lớp của project tham khảo (KHÔNG copy code nghiệp vụ td-web)

- Hằng số 4 nhóm nơi nhận (`recipientGroup`) và `sendType` giữ NGUYÊN giá trị với
  `VbdiSendRecipientGroup`/`VbdiSendType` của `td-application` (chỉ khai báo hằng số, không
  import/module td-web).
- Bảng dedup/reply do Worker tự quản — tránh phụ thuộc migration của td-web.

**Bugfix 06/09/2026 (nghiêm trọng, đã sửa)**: `worker.tenant.pool-auto-commit` mặc định `false` cho
MỌI pool tenant → mọi câu lệnh JDBC (kể cả `CREATE TABLE IF NOT EXISTS` tự-chữa-lành) chạy NGOÀI 1
transaction Spring quản lý bị HikariCP tự `rollback()` khi trả connection về pool → 2 bảng dedup/reply
KHÔNG BAO GIỜ thực sự tồn tại, mọi `INSERT` sau đó lỗi `BadSqlGrammarException` ("relation does not
exist"). Cả `VbdiDeliveryTaskHandler` lẫn `VbdiReplyTaskHandler` nay bọc TOÀN BỘ câu lệnh JDBC trên
DataSource tenant trong `TransactionTemplate` để có commit thật. Xem
`docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md` addendum 06/09/2026 (2) (đã xác
minh trực tiếp trên DB tenant thật). **Cần rebuild + redeploy Worker để áp dụng fix** — các unit đã
`FAILED` (attempt 5/5) trước khi fix KHÔNG tự retry (trạng thái tận cùng), cần can thiệp thủ công
(reset `worker_units.status`/`attempts` hoặc gửi lại từ td-web) để xử lý lại.

**Bugfix 07/09/2026 (2, production log)**: `CREATE TABLE IF NOT EXISTS worker_vbdi_reply_log` không
atomic giữa các session Postgres khác nhau — 2+ unit `vbdi.reply` chạy đồng thời cho cùng tenant lần
bảng chưa tồn tại gây `DuplicateKeyException` (race đã biết của Postgres, không phải lỗi logic).
`ensureReplyLogTable` nay chạy TRANSACTION RIÊNG + bắt `DuplicateKeyException` (thua race = coi như
thành công). Xem `docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md` addendum
07/09/2026 (5).

## 6. Điểm chưa làm (ghi nhận gap, không tự chốt hộ)

- Ghi vào bảng nghiệp vụ `vbdi_send_log_documents` của td-web: Worker KHÔNG ghi vào đó để giữ
  độc lập triển khai; nếu tích hợp sâu cần quyết định (vd qua API td-web hoặc module dùng chung).
- Gateway liên thông quốc gia (`external_unit`, cơ quan NGOÀI tỉnh) — chưa có; unit giữ ở
  `WAITING_DEPENDENCY` (không đánh dấu DONE/"Đã đến" giả) và tự chạy lại định kỳ; khi gateway
  được triển khai chỉ cần thay nhánh `isExternal()` trong `VbdiDeliveryTaskHandler` — không cần
  đổi Worker Core hay lược đồ envelope.
- Nhánh `type=="vaoso"` của `ReplyDocumentOut.java` (xử lý văn bản "thay thế"/"thu hồi" khi vào sổ)
  — tính năng riêng, lớn hơn "Từ chối tiếp nhận" (đã port, xem §4) — CHƯA port.
- `internal_unit` (đơn vị nội bộ của chính cơ quan gửi) và `coordinator` (cá nhân, định danh nhận
  là username) chưa tạo văn bản đến thật như `contact_unit` — `coordinator` cần field cá nhân riêng
  (`user_process`, không phải `contact_unit_identifier`) nếu port sau này; `internal_unit` có thể
  không cần cơ chế cross-tenant này (cùng cơ quan, thường cùng tenant) — chưa phân tích riêng.
- "Vào sổ" (gán sổ/số văn bản) vẫn là thao tác NGƯỜI DÙNG chủ động ở td-web
  (`LuuVanBanDenUseCase`) — Worker chỉ tạo bản ghi "chờ vào sổ", không tự động hoá bước này.

## 7. Tệp đính kèm — copy 1 lần, tham chiếu (bổ sung 07/09/2026)

Văn bản đến tự tạo (`contact_unit`) nay mang ĐÚNG danh sách tệp đính kèm của văn bản đi gốc — KHÔNG
copy vật lý theo từng nơi nhận (tránh nhân bản/tăng dung lượng, đúng yêu cầu người dùng):

1. `SendVbdiDocumentUseCase` (td-application) gọi `PrepareVbdiDeliveryFilesUseCase` **ĐÚNG 1 LẦN
   cho cả lượt gửi** (không phải theo từng nơi nhận) — copy server-side (MinIO `CopyObjectArgs`,
   không tải byte về) mọi tệp của văn bản đi sang namespace RIÊNG:
   `vbdi-delivery/{senderIdentifier}/{yyyy}/{MM}/{dd}/{documentId}/{uuid}.{ext}`.
2. Danh sách tệp đã copy (`objectKey`/`bucketName`/tên/kích thước...) được forward qua envelope
   `vbdi.delivery` (`documentSnapshot.files`) tới MỌI nơi nhận của lượt gửi đó.
3. Mỗi tenant nhận (`VbdenIncomingDocumentWriter`, td-worker) tự INSERT 1 dòng `file_metadata`
   RIÊNG (trong CÙNG transaction với văn bản/dedup) trỏ **CÙNG object đã copy ở bước 1** — không
   copy vật lý thêm lần nào nữa. API tải tệp hiện có (`GetFileUseCase`/`DocumentsController`)
   hoạt động KHÔNG cần sửa gì.

Đánh đổi đã chốt với người dùng: bản copy ở bước 1 TÁCH HẲN khỏi vòng đời tệp gốc của văn bản đi
(người gửi xoá/thay thế sau này không ảnh hưởng) — khác "tham chiếu thẳng tới bản gốc" (rủi ro
đứt liên kết nếu người gửi xoá tệp, đã bị từ chối). Chi tiết đầy đủ + lý do:
`docs/architecture/11-rabbitmq-vbdi-worker-phan-hoi-trang-thai.md` addendum 07/09/2026 (3).

## 8. Task vbdi.revoke — "Thu hồi"/"Lấy lại" (bugfix 08/09/2026)

**Lỗi thật người dùng báo**: bấm "Lấy lại VB" — log td-worker báo `vbdi.delivery` DONE (publish tới
broker thành công) nhưng văn bản đến của đơn vị nhận VẪN còn nguyên trong danh sách hoạt động.

**Nguyên nhân gốc**: `AddVbdiSendLogUseCase` (td-application) publish CẢ 3 sendType "thu hồi"
(`thuhoivanban`/`laylaivanban`/`thuhoitoanbo`, xem `VbdiSendType`) qua CÙNG taskType `vbdi.delivery`
như 1 lượt GỬI MỚI. `VbdiDeliveryTaskHandler` xử lý y hệt: dedup theo `sendLogId` (LUÔN mới với hành
động thu hồi — không trùng sendLogId của lượt gửi gốc, không dedup được gì) rồi TẠO THÊM 1 văn bản
đến "chờ vào sổ" MỚI + phản hồi "Đã đến" — hoàn toàn sai nghiệp vụ. Đối chiếu Domino thật
(`docs/agent_domino/io/BeforeMailin.ja/StatusProcess.java`) xác nhận `RevokeDocumentOut.java`/
`RevokeAllDocumentOut.java` gửi mail form **`feedback_DocumentOut`** (xử lý ở
`StatusProcess.ExecuteVBDIBussiness() -> ActionRevokeDocument()`), KHÔNG PHẢI form `documentin` như
`SendDocument.java` — nghĩa là phía nơi nhận phải TÌM văn bản đến ĐÃ CÓ (theo `id_DocumentOut` = id
văn bản đi gốc) để đánh dấu "Đã lấy lại"/ẩn khỏi danh sách, KHÔNG được tạo thêm 1 văn bản đến MỚI.

**Sửa**: 3 sendType "thu hồi" giờ enqueue taskType RIÊNG **`vbdi.revoke`**
(`AddVbdiSendLogUseCase#REVOKE_FAMILY_SEND_TYPES`) — route sang handler MỚI
`VbdiRevokeTaskHandler` (td-woker), port đúng `ActionRevokeDocument`:

1. `UPDATE documents SET dynamic_fields = dynamic_fields || {'reply':'1','is_revoke':'1',
   'delete_date':...} WHERE document_type='vbden' AND dynamic_fields->>'parentid_send' = documentId
   AND dynamic_fields->>'contact_unit_identifier' = recipientIdentifier` — `parentid_send` là field
   `VbdenIncomingDocumentWriter` đã ghi sẵn trên MỌI văn bản đến tự tạo, khớp đúng `id_DocumentOut`
   Domino. `reply="1"` là field `ViewQueryService#quamangnoiboFilter` (và 3 view anh em
   `lienthong`/`dendientu`/`denquamang`) ĐÃ DÙNG SẴN để loại trừ khỏi danh sách hoạt động — set field
   này là ĐỦ, không cần soft-delete `documents` (Domino cũng không xoá thật, chỉ đổi `Form`).
2. Chỉ khi UPDATE khớp ít nhất 1 dòng (đúng Domino: `SendReplyToSender` chỉ gọi khi
   `docs.getCount() > 0`) — publish phản hồi `vbdi.reply` NGƯỢC về đơn vị gửi: `statusCode="101"`
   (phi chuẩn, giống `"tralai"` — không thuộc `StatusCode.java` 01-06), `statusLabel="Đã lấy lại"`.
   `VbdiReplyTaskHandler` phía đơn vị gửi KHÔNG cần sửa gì — cơ chế ghi "Nhật ký gửi nhận" đã generic
   theo mọi statusCode (case mặc định của `buildContent` đã cho kết quả đúng).
3. `internal_unit`/`coordinator`/`external_unit` (chưa từng tạo văn bản đến thật — chỉ dedup): câu
   UPDATE tự nhiên khớp 0 dòng — no-op an toàn, KHÔNG publish phản hồi.

Test: `VbdiRevokeTaskHandlerIntegrationTest` (td-woker, Testcontainers) +
`AddVbdiSendLogUseCaseTest#execute_revokeFamilySendTypes_*`/`execute_unitRevokeSendType_*`
(td-application, xác nhận `VbdiSendType#UNIT_REVOKE` — "gửi văn bản thay thế/thu hồi" — VẪN thuộc
họ GỬI, không bị gộp nhầm vào family "thu hồi" dù tên có chữ "revoke").

**Bugfix 08/09/2026 (2) — người dùng test lại sau khi deploy bản vá trên**: log báo
`"Đã lấy lại 1 văn bản đến"` (UPDATE chạy, rowcount=1) và `vbdi.reply` publish thành công, nhưng đối
chiếu TRỰC TIẾP DB tenant nhận (`snnmt`) cho thấy `dynamic_fields.reply`/`is_revoke` của văn bản đến
đó VẪN `null` — UPDATE chưa bao giờ thực sự persist. **Nguyên nhân**: CÙNG lỗi gốc đã sửa 2 lần trước
đó ở `VbdiDeliveryTaskHandler`/`VbdiReplyTaskHandler` (xem §5/addendum 06-07/09/2026 bên dưới) —
`worker.tenant.pool-auto-commit` mặc định `false` cho MỌI pool tenant, nên `jdbc.update(...)` chạy
NGOÀI 1 transaction Spring quản lý KHÔNG được commit tường minh — HikariCP tự ROLLBACK khi trả
connection về pool (rowcount trả về ĐÚNG lúc chạy, trong transaction đang mở, nhưng không bao giờ
thực sự lưu). `VbdiRevokeTaskHandler` khi viết lần đầu đã BỎ SÓT bọc `TransactionTemplate` quanh
`revokeIncomingDocument()` — đã sửa bằng cách bọc `TransactionTemplate`/`DataSourceTransactionManager`
y hệt mẫu 2 handler kia. **Bài học chung khi thêm handler MỚI ghi JDBC trực tiếp vào DB tenant ở
`td-woker`**: LUÔN bọc MỌI câu lệnh JDBC (kể cả 1 câu UPDATE/INSERT đơn lẻ) trong
`TransactionTemplate` tường minh — đừng tin `JdbcTemplate` tự commit, vì cấu hình pool tenant thật
KHÔNG auto-commit. `VbdiRevokeTaskHandlerIntegrationTest` (Testcontainers, `autoCommit=false` đúng
cấu hình thật) LẼ RA đã bắt được lỗi này ngay khi viết, nhưng môi trường dev không có Docker nên chưa
chạy được cho tới khi người dùng test trên server thật.

**Bổ sung 08/09/2026 (3) — người dùng yêu cầu "lấy lại tận gốc" sau khi xác nhận bugfix (2) đúng cho
ca đơn giản nhất**: 3 khoảng trống bổ sung, đối chiếu lại `ActionRevokeDocument` (Domino) kỹ hơn:

1. **Nhiều bản ghi "văn bản đến" cho CÙNG 1 văn bản đi** (gửi lại/gửi bổ sung nhiều lần trước khi bấm
   "Lấy lại" MỘT LẦN — mỗi lần gửi tạo 1 bản ghi riêng, dedup theo `sendLogId` chứ không theo
   `documentId`) — câu UPDATE vốn không giới hạn `LIMIT` nên đã lấy lại được HẾT, không cần sửa gì
   thêm ở điểm này; chỉ cần đảm bảo điều kiện khớp không bị thu hẹp sai (xem điểm 2).
2. **Văn bản ĐÃ "vào sổ" (`LuuVanBanDenUseCase`, R4 `loaiKhoiDanhSachChoVaoSo`) không lấy lại được**:
   hành động "vào sổ" XOÁ `contact_unit_identifier` khỏi văn bản gốc (loại khỏi hàng đợi "chờ vào sổ",
   đúng thiết kế) — điều kiện cũ `AND dynamic_fields->>'contact_unit_identifier' = recipientIdentifier`
   vì vậy KHÔNG còn khớp được văn bản đã vào sổ (0 dòng, "not found" ÂM THẦM). Đã bỏ hẳn điều kiện này,
   chỉ còn `parentid_send` (KHÔNG bị "vào sổ" đụng tới, và Worker đã chạy đúng tenant DB — database-
   per-tenant, an toàn không cần thêm điều kiện phân biệt tổ chức).
3. **Đã "vào sổ" còn kéo theo 1 bản ghi con `vbden_so`** (`document_type="vbden_so"`, `parentid` = id
   văn bản gốc — xem `TiepNhanVbdenUseCase#REGISTER_ENTRY_DOCUMENT_TYPE`/`GetSoVanBanDenUseCase`, tương
   đương database RIÊNG `vbden_so.nsf` của Domino). Domino thật CŨNG cập nhật bản ghi này (tra view
   `sovanban_by_idvbden` khoá `parentid`, đánh dấu y hệt bản ghi gốc — xem view `sovanban_bithuhoi`
   "isrevoke=1"). `VbdiRevokeTaskHandler` giờ làm 2 bước TRONG CÙNG 1 transaction: (a)
   `UPDATE ... RETURNING id` trên `documents(document_type='vbden')` để biết CHÍNH XÁC những văn bản
   gốc nào vừa bị lấy lại, (b) với MỖI id đó, `UPDATE documents WHERE document_type='vbden_so' AND
   dynamic_fields->>'parentid' = <id>`. **Kèm theo**: `GetSoVanBanDenUseCase` (td-application) TRƯỚC
   ĐÓ không hề loại trừ `reply="1"` khỏi danh sách "Sổ văn bản đến" (Domino có làm — đổi `Form` sang
   hậu tố "Deleted" khiến hết khớp `form="DocumentIn"` của view `sovanban`; Java không có field "Form"
   mutable tương đương) — đã thêm `excludeFieldValues({"reply": ["1"]})` vào `buildFilter()`, nếu
   không đánh dấu xong vẫn hiển thị nguyên, vô nghĩa.

**CHƯA PORT (ghi nhận gap, hỏi lại người dùng, KHÔNG tự chốt)**: "văn bản đến cơ quan vào sổ, sau đó
phân xử lý cho đơn vị (đơn vị có văn thư riêng) tự vào sổ riêng lần nữa" — đối chiếu Domino tương ứng
form mail-in `document_process_unit` (`DocumentInProcess.java`/`DocumentInProcess2.java` gửi,
`DocumentInProcessUnit.java` xử lý, xem `docs/agent_domino/io/BeforeMailin.ja`) — rà soát TOÀN BỘ
`JAVA/` không tìm thấy bất kỳ tham chiếu nào tới cơ chế này, nghĩa là luồng "đơn vị con tự vào sổ lại"
CHƯA được port sang Java ở bất kỳ đâu (không riêng gì revoke) — không có bản ghi Java tương ứng để mà
"lấy lại". Đây là gap CỦA TÍNH NĂNG GỐC, không phải riêng của revoke — cần người dùng xác nhận phạm vi
trước khi port (có thể đã tồn tại dưới tên/cơ chế khác chưa được đối chiếu).

Tài liệu tiếp: [12-observability.md](12-observability.md)