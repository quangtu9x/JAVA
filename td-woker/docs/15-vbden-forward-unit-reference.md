# 13 — vbden.forward_unit — Chuyển xử lý cho đơn vị con có tenant riêng

## 1. Bối cảnh

Yêu cầu người dùng 08/09/2026 (trong lúc hoàn thiện "Lấy lại VB" của module `vbdi` — xem
[11-vbdi-reference.md](11-vbdi-reference.md) §8): "còn 1 trường hợp nữa là văn bản đến cơ quan vào
sổ, phân xử lý cho đơn vị (đơn vị có văn thư) vào sổ riêng". Đối chiếu Domino thật
(`docs/agent_domino/io/BeforeMailin.ja/DocumentInProcess.java#copyDocumentProcessToDocumentIn()`,
form mail-in `document_process_unit`, xử lý bởi `DocumentInProcessUnit.java`) xác nhận: đây là 1 cơ
chế **cross-tenant** — khi "chuyển xử lý" (`ChuyenXuLyVbdenUseCase`) giao `unit_process` cho 1 đơn vị
chạy CSDL/tenant RIÊNG (đơn vị con có văn thư riêng, tự "vào sổ" độc lập) — người dùng xác nhận trực
tiếp đây LÀ tenant/database khác, giống hệt mô hình Domino (1 CSDL vật lý riêng/đơn vị).

Rà soát toàn bộ `JAVA/` (grep `document_process_unit`/`DocumentInProcessUnit`) xác nhận **KHÔNG có
tương đương** trước bản port này.

## 2. Luồng 2 chiều

```
Tenant GỐC (đơn vị đang "chuyển xử lý")      Tenant ĐÍCH (đơn vị con, unit_process)
  ChuyenXuLyVbdenUseCase
    → publishForwardUnitIfCrossTenant()
       (unit_process khớp Organization
        có linkedTenantId KHÁC tenant
        hiện tại → publish 1 unit/đơn vị) ──►  Worker: vbden.forward_unit task
                                                  (VbdenForwardUnitTaskHandler)
                                                  • INSERT 1 bản sao "văn bản đến" TỐI THIỂU
                                                    vào documents (document_type=vbden)
                                                  • publish vbden.forward_unit.reply ngược
       ◄──────────────────────────────────────  (routing worker.vbden_forward_unit_reply)
  Worker: vbden.forward_unit.reply task
   (chạy TRONG tenant GỐC)
   (VbdenForwardUnitReplyTaskHandler)
   • loại identifier khỏi unit_process (văn bản gốc)
   • ghi vết forwarded_units (append-only, chuẩn bị cho revoke giai đoạn 2)
```

## 3. Trigger — `ChuyenXuLyVbdenUseCase#publishForwardUnitIfCrossTenant`

Tự động phát hiện (người dùng xác nhận — FE KHÔNG cần chọn "gửi cross-tenant" tường minh, giống
Domino): với MỖI identifier trong `request.getUnitProcess()` (";"-nối, cùng field
`updateChutri()` đã dùng để ghi `unit_process` cục bộ), tra `OrganizationRepository
.findByIdentifierAndDeletedOnIsNull(identifier)` → `Organization.linkedTenantId` khác
`CurrentTenantProvider.currentTenantId()` hiện tại thì publish 1 unit `vbden.forward_unit` (payload
`VbdenForwardUnitMessagePayload`) tới tenant đó. Cùng identifier KHÔNG có `linkedTenantId`/trùng
tenant hiện tại → bỏ qua (đơn vị nội bộ, `updateChutri` đã xử lý đủ, không cần relay).

Chạy SAU `updateParentDocument` (giao dịch "Chuyển xử lý" CHÍNH đã lưu DB xong), best-effort — lỗi ở
bước này (tổ chức không có `linkedTenantId` hợp lệ, outbox publish thất bại...) KHÔNG làm hỏng giao
dịch chính, cùng nguyên tắc `publishReplyStatusAfterPhanCong`/`recordProcessDiagramNodes` đã có sẵn
trong cùng use case.

## 4. `VbdenForwardUnitTaskHandler` (chạy tại tenant ĐÍCH)

INSERT 1 dòng `documents` (`document_type='vbden'`, `status='0'` — `STATUS_MOI`) với
`dynamic_fields`: `identifier_creator`, `origin_document_id`, `origin_tenant_id`, `code`,
`promulgation_date`, `deadline_date`, `unit_in_charge`, `unit_in_charge_identifier`,
`forwarded_by`, `forwarded_opinion`, `received_date`. Sau đó publish `vbden.forward_unit.reply` về
`originTenantId`.

**Đơn giản hoá CÓ CHỦ ĐÍCH so với Domino gốc** (Domino copy TOÀN BỘ field + tệp đính kèm +
`user_process`/`user_coordinate`/`user_to_read`/`user_track` sang bản sao qua `fieldsToUpdate` 10
field): bản port lần đầu này KHÔNG copy tệp đính kèm, KHÔNG đồng bộ 4 field xử lý — đơn vị nhận tự
phân công lại từ đầu trên bản sao của họ. Chưa gắn vào bất kỳ hàng đợi FE cụ thể nào (khác
`quamangnoibo` của vbdi) — văn thư đơn vị đích cần tự tìm/xử lý qua tra cứu trực tiếp cho tới khi có
yêu cầu gắn vào 1 view cụ thể.

**Bugfix 08/09/2026 (2) — người dùng test thật: "văn thư/người đại diện nhận đang không thấy văn bản
ở bất kỳ đâu"**: bản đầu KHÔNG set `identifier_creator` trên bản sao — field DUY NHẤT mà MỌI view
org-scoped của vbden (`chophanxuly`/`cuatochuc`/`phaixulyCuatochuc`... xem `ViewQueryService`) dùng để
lọc "văn bản CỦA tổ chức tôi" (qua `resolveCurrentIdentifier()`) — thiếu field này, bản sao tồn tại
trong DB nhưng KHÔNG view nào khớp được, "vô hình" với mọi người ở tenant đích. Đã sửa: LUÔN set
`identifier_creator = targetIdentifier` khi INSERT (giá trị MỚI, đại diện "bản sao này giờ thuộc tổ
chức đích" — KHÔNG forward từ payload). Kèm theo (người dùng báo chung "không đủ thông tin"): bổ sung
3 field còn thiếu forward NGUYÊN VẸN từ văn bản gốc (`deadline_date`/`unit_in_charge`/
`unit_in_charge_identifier` — vẫn là hạn xử lý/cơ quan ban hành BÊN NGOÀI gốc, không đổi ý nghĩa khi
chuyển tiếp nội bộ, không phải của đơn vị đang chuyển tiếp).

**Bổ sung 08/09/2026 (3) — người dùng test tiếp: "đã thấy trong 'Văn bản đến Cơ quan' nhưng chưa thấy
ở trong 'Văn bản đến trong tỉnh' (như văn bản đi phát hành sang)"; "thiếu files nữa, nhưng không phải
nhân ra mà đọc chung với đơn vị chuyển xử lý luôn"**:

- **Hiển thị "Qua mạng nội bộ"**: NGOÀI `identifier_creator`, bản sao còn set
  `contact_unit_identifier = targetIdentifier` + `document_internet = "1"` — đúng 2 field
  `ViewQueryService#quamangnoiboFilter` (và 3 view anh em `lienthong`/`dendientu`/`denquamang`) dùng
  để hiển thị "văn bản đến qua mạng nội bộ" — CÙNG 2 field `VbdenIncomingDocumentWriter` (module
  `vbdi`) đã dùng cho `contact_unit`, vì văn thư xử lý văn bản "đến từ cơ quan khác qua kênh điện tử"
  theo CÙNG 1 quy trình dù đến qua `vbdi` hay `vbden.forward_unit`. Từ đó văn thư "Tiếp nhận"/"vào
  sổ" rồi tự "chuyển xử lý" tiếp cho 1 người đại diện cụ thể — dùng LẠI nguyên
  `ChuyenXuLyVbdenUseCase` đã có sẵn, không cần code riêng cho bản sao này.
- **Tệp đính kèm — "đọc chung", KHÔNG copy**: `GetDocumentFileRefsUseCase` (interface MỚI,
  td-application + impl td-infrastructure) đọc lại `file_metadata` HIỆN CÓ của văn bản gốc
  (`objectKey`/`bucketName`, KHÔNG gọi MinIO `CopyObjectArgs` nào — khác hẳn quyết định của vbdi/
  `PrepareVbdiDeliveryFilesUseCase`, vốn copy sang namespace riêng để tách vòng đời). `ChuyenXuLyVbdenUseCase`
  forward danh sách này nguyên vẹn trong payload; `VbdenForwardUnitTaskHandler` chỉ tạo 1 dòng
  `file_metadata` MỚI cho mỗi tệp tại tenant đích, trỏ THẲNG CÙNG `objectKey`/`bucketName` — "đọc
  chung" 1 object vật lý duy nhất (`stored_filename` vẫn phải là id MỚI sinh riêng để không đụng
  UNIQUE constraint, cùng bugfix đã áp dụng ở `VbdenIncomingDocumentWriter#insertFileMetadata`), và
  ghi `dynamic_fields.files` (`{name,size,type,file_id}`) để FE hiển thị được ngay.

**Idempotent 23/10/2026 (chạy nhiều máy chủ td-woker)**: trước đây id bản sao, id `file_metadata` và
unitId của reply đều `UUID.randomUUID()` ⇒ unit chạy lại (crash sau commit, lease hết hạn, timeout rồi
máy khác nhận lại) sinh **bản sao thứ 2** ở đơn vị nhận. Nay cả 3 id suy ra TẤT ĐỊNH:
`VbdenTaskConstants.unitScopedId(ctx, "document")` = `UUID.nameUUIDFromBytes("vbden.forward_unit|document|<tenant>|<unitId>")`;
id tệp = hash(id bản sao + thứ tự + objectKey); unitId reply = hash("vbden.forward_unit.reply|<id bản
sao>"). `INSERT documents … ON CONFLICT (id) DO NOTHING` — đã có thì bỏ qua cả `file_metadata`, chỉ
gửi lại reply. unitId do `ChuyenXuLyVbdenUseCase` sinh 1 lần, lưu trong outbox ⇒ mỗi lượt "Chuyển xử
lý" MỚI vẫn tạo bản sao mới như trước. Không cần migration.

## 5. `VbdenForwardUnitReplyTaskHandler` (chạy tại tenant GỐC)

1. Đọc `dynamic_fields` văn bản gốc, loại `targetIdentifier` khỏi mảng `unit_process` (đọc khoan
   dung cả dạng scalar ";"-nối lẫn JSON array — cùng nguyên tắc `readMultiValue` phía
   `td-application` — LUÔN ghi lại dưới dạng array, đúng quy ước `ChuyenXuLyVbdenUseCase
   #updateChutri` đang dùng).
2. Ghi thêm 1 phần tử vào `forwarded_units` (mảng APPEND-ONLY `{identifier, tenantId, documentId}`)
   — chuẩn bị dữ liệu liên kết cho revoke cascade (giai đoạn 2, xem §6).

Không tìm thấy văn bản gốc (đã bị xoá) → log cảnh báo, KHÔNG fail unit (best-effort).

**Bổ sung 08/09/2026 — yêu cầu người dùng: "muốn phản hồi trạng thái 'Đã đến'... vào quá trình xử
lý"**: bước 2 (ghi `forwarded_units`) chạy CÙNG transaction với `insertArrivalHistoryRow` — ghi THÊM
1 dòng `vbden_res_documents` (`parentid = originDocumentId`, `action = "da_den_don_vi_khac"`,
`opinion = "Đã đến đơn vị " + targetName`) để tab "Quá trình xử lý" của văn bản gốc hiện được sự kiện
này ngay, không cần đăng nhập sang tenant khác. Xem thêm §6b.

**Sửa 23/10/2026 — mất cập nhật + ghi trùng**: đọc `dynamic_fields` bằng `SELECT … FOR UPDATE` (khoá
dòng văn bản gốc tới hết giao dịch) — trước đây 2 reply đồng thời cho cùng 1 văn bản gốc (chuyển cho
nhiều đơn vị con, xử lý trên 2 thread/2 máy) đọc cùng bản cũ rồi ghi đè nhau, mất phần tử
`forwarded_units` / đơn vị không được gỡ khỏi `unit_process`. Đồng thời: bản sao (`createdDocumentId`)
đã có trong `forwarded_units` ⇒ coi là reply gửi lại, bỏ qua — không thêm dòng "Đã đến" trùng.
`vbden.forward_unit.status`/`.revoke` (§6, §6b): id dòng `vbden_res_documents` tất định theo unit +
`ON CONFLICT (id) DO NOTHING`; revoke thấy dòng lịch sử của unit đã có thì bỏ qua cả UPDATE (không ghi
đè `delete_date`).

## 6. Giai đoạn 2 — ĐÃ PORT 08/09/2026 ("giai đoạn 1 cơ bản đã xong, làm giai đoạn 2
LayLaiVanBanUseCase/ThuHoiVanBanUseCase")

`LayLaiVanBanUseCase`/`ThuHoiVanBanUseCase` (vbden "Lấy lại VB"/"Thu hồi" nội bộ — KHÁC `vbdi`'s "Lấy
lại VB" đã sửa ở tài liệu 11 §8) giờ CASCADE revoke sang bản sao cross-tenant qua `forwarded_units`
(ghi bởi bước 5.2):

1. **`PublishForwardUnitRevokeUseCase`** (td-application, dùng CHUNG bởi cả 2 use case) — đọc
   `forwarded_units` trên `fields` (đã có sẵn trong bộ nhớ, TRƯỚC `parent.replaceDynamicFields(...)`),
   publish `vbden.forward_unit.revoke` cho MỖI entry CHƯA `revoked`, rồi MUTATE lại mảng đó với cờ
   `revoked: true` — cờ này được lưu CÙNG 1 giao dịch/1 lần `save()` với thay đổi routing chính (không
   cần lưu thêm lần 2), đảm bảo IDEMPOTENT (gọi "Thu hồi" nhiều lần không publish trùng).
2. **`VbdenForwardUnitRevokeTaskHandler`** (td-woker, chạy TẠI TENANT ĐÍCH) — đánh dấu bản sao:
   set `reply="1"`/`is_revoke="1"`/`delete_date` (CÙNG khuôn `VbdiRevokeTaskHandler`, loại khỏi
   `quamangnoibo`/`lienthong`/`dendientu`/`denquamang`) **VÀ** xoá rỗng 7 field điều hướng hàng đợi
   (`user_process`/`unit_process`/`user_coordinate`/`unit_coordinate`/`user_to_read`/`unit_to_read`/
   `user_track` — cần thêm bước này vì `chophanxulyFilter` lọc theo rỗng `user_process`, KHÔNG theo
   `reply`). Ghi thêm 1 dòng `vbden_res_documents` audit ("Văn bản đã bị 'Lấy lại VB'/'Thu hồi' bởi
   đơn vị gốc...") để văn thư/người đại diện tại tenant đích biết lý do văn bản biến mất khỏi hàng đợi.

**Phạm vi CÓ CHỦ ĐÍCH (đơn giản hoá)**: cascade TẤT CẢ entry `forwarded_units` còn hiệu lực, KHÔNG lọc
thêm theo "ai đã forward"/"cùng nhánh tổ chức với người thao tác" — vì `ChuyenXuLyVbdenUseCase
#createProcessingRecord` chưa bao giờ ghi `unit_process` lên bản ghi lịch sử `vbden_res` (chỉ ghi
username-level), nên không có dữ liệu để truy ngược "lần chuyển xử lý cross-tenant nào do CHÍNH người
đang thao tác tạo ra" — khác cách 2 use case lọc revoke USERNAME (dựa vào snapshot `_obj` sẵn có). Cả
2 hành động đều có thẩm quyền cao (chỉ người trong `author`/người từng giao mới thấy nút) nên cascade
TOÀN BỘ là lựa chọn AN TOÀN, tránh bản sao "mồ côi". Xem javadoc đầy đủ ở
`PublishForwardUnitRevokeUseCase`.

Vẫn CHƯA port: đồng bộ `user_process`/`user_coordinate`/`user_to_read`/`user_track` mức USERNAME cụ
thể ở BƯỚC TẠO bản sao (§5, chỉ xử lý `unit_process` mức ĐƠN VỊ) — không liên quan revoke cascade,
xem lại gap này khi có yêu cầu cụ thể.

## 6a. Bổ sung 08/09/2026 (4) — "không cho xóa đính kèm gốc"

Yêu cầu người dùng: *"điều chỉnh thêm dấu hiệu nếu là văn bản nhận được bằng chức năng 'chuyển xử
lý' thì không cho xóa đính kèm gốc"*.

Vì `file_metadata` của bản sao trỏ THẲNG cùng `objectKey`/`bucketName` với văn bản gốc (§4, "đọc
chung", không copy MinIO), `DeleteFileUseCaseImpl` (xóa file bất kỳ trong hệ thống) gọi
`MinioService#removeObject` VÔ ĐIỀU KIỆN trước khi xóa dòng DB — nếu đơn vị đích xóa dòng "dùng
chung" này, object vật lý bị xóa THẬT, kéo theo văn bản GỐC ở tenant khác cũng mất tệp.

Khắc phục:
- Migration `V1.0.50` thêm cột `file_metadata.is_shared` (mặc định `FALSE`).
- `VbdenForwardUnitTaskHandler#insertFileMetadataRefs` set `is_shared = TRUE` cho MỌI dòng nó tạo.
- `DeleteFileUseCaseImpl` chặn xóa HOÀN TOÀN (không gọi MinIO, không xóa dòng DB, trả `Result.failure`
  với thông báo giải thích) khi `entity.getIsShared() == TRUE` — áp dụng chung cho MỌI module
  (không riêng vbden), vì cơ chế chia sẻ file vật lý này có thể tái sử dụng ở nơi khác sau này.

## 6b. Bổ sung 08/09/2026 (5) — echo trạng thái xử lý về "quá trình xử lý" văn bản gốc

Yêu cầu người dùng: *"muốn phản hồi trạng thái 'Đã đến' và các trạng thái như chuyển xử lý... cho
đơn vị 'Chuyển xử lý' vào quá trình xử lý"*.

Phần "Đã đến" (1 lần duy nhất, lúc tạo bản sao) → §5 (`insertArrivalHistoryRow`). Phần "các trạng
thái như chuyển xử lý..." được TỔNG QUÁT HOÁ thành: **MỌI** hành động xử lý tiếp theo trên bản sao
(không riêng "chuyển xử lý", dù đó là ví dụ người dùng nêu — bản sao có thể phân công/phối hợp/chuyển
tiếp nhiều lần, kể cả forward tiếp sang tenant thứ 3) được echo ngược về văn bản GỐC:

1. `ChuyenXuLyVbdenUseCase#publishStatusBackToOriginIfForwardedCopy` (td-application) chạy ở CUỐI
   `execute()`, sau MỌI hành động xử lý (không riêng cross-tenant) — nhận diện "văn bản đang xử lý
   chính là bản sao nhận qua forward_unit" qua 2 field `origin_document_id`/`origin_tenant_id` (do
   `VbdenForwardUnitTaskHandler#insertForwardedCopy` ghi lúc tạo bản sao, không method nào trong lớp
   này động tới nên luôn đọc được nguyên vẹn). Nếu có, publish task `vbden.forward_unit.status` kèm
   `action`/`actionLabel` (nhãn tiếng Việt render SẴN ở phía gửi, tránh trùng lặp business constants
   sang td-woker), `reportingIdentifier`/`reportingName` (đơn vị đang giữ bản sao — tra tên qua
   `OrganizationRepository`), `actingUsername`, `opinion`, `newAssigneesLabel`.
2. `VbdenForwardUnitStatusTaskHandler` (td-woker) chạy TẠI TENANT GỐC — ghi 1 dòng
   `vbden_res_documents` mới (`parentid = originDocumentId`) với `opinion` render sẵn dạng
   `"{actionLabel} tại đơn vị {reportingName} (giao cho: {newAssigneesLabel}): {opinion}"` — hiện lên
   CÙNG tab "Quá trình xử lý" như mọi vết xử lý nội bộ khác, người xem không cần biết đây là echo từ
   tenant khác.

Domino gốc KHÔNG có cơ chế tương đương cho phần này (chỉ có reply 1 lần lúc tạo bản sao) — đây là
phần MỞ RỘNG theo yêu cầu người dùng, không phải port thuần từ Domino.

## 7. Cấu hình

Cờ RIÊNG `worker.tasks.vbden.enabled` (mặc định bật), độc lập với `worker.tasks.vbdi.enabled` — 2
module task khác nhau (`com.td.worker.tasks.vbden` khác `com.td.worker.tasks.vbdi`).

## 8. Test

- `ChuyenXuLyVbdenUseCaseTest#execute_unitProcessResolvesToDifferentTenant_*`/
  `execute_unitProcessResolvesToSameTenant_*`/`execute_withoutUnitProcess_*`/
  `execute_onForwardedCopy_shouldPublishStatusBackToOriginTenant`/
  `execute_onNormalDocument_shouldNotPublishStatusBack` (td-application).
- `VbdenForwardUnitTaskHandlerIntegrationTest`/`VbdenForwardUnitReplyTaskHandlerIntegrationTest`/
  `VbdenForwardUnitStatusTaskHandlerIntegrationTest`/`VbdenForwardUnitRevokeTaskHandlerIntegrationTest`
  (td-woker, Testcontainers).
- `DeleteFileUseCaseImplTest#execute_whenFileIsShared_shouldBlockDeletionEntirely`/
  `execute_whenFileIsNotShared_shouldDeleteNormally` (td-infrastructure).
- `LayLaiVanBanUseCaseTest#execute_withForwardedUnits_shouldPublishForwardUnitRevokeAndMarkRevoked`/
  `execute_withoutForwardedUnits_shouldNotPublishAnything`;
  `ThuHoiVanBanUseCaseTest#execute_withForwardedUnits_shouldPublishForwardUnitRevokeAndMarkRevoked`/
  `execute_withAlreadyRevokedForwardedUnit_shouldNotPublishAgain`/
  `execute_withoutForwardedUnits_shouldNotPublishAnything` (td-application, giai đoạn 2).
