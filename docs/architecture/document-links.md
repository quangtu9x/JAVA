# Liên kết văn bản — bảng `document_links`

> Áp dụng từ 06/10/2026.
> - `V1.0.69__Create_Document_Links.sql` — giai đoạn 1: **hồ sơ công việc ↔ văn bản**, **dự thảo → văn bản đi**.
> - `V1.0.70__Document_Links_Related_Snapshots.sql` — giai đoạn 2: **"Trả lời cho văn bản đến"** và
>   **"Văn bản kèm theo"** của văn bản đi / dự thảo (cột `link_data`).
>
> Không còn ghi song song field cũ: bảng `document_links` là nguồn dữ liệu duy nhất cho các liên kết dưới đây.

## 1. Vì sao đổi

Trước đây một liên kết giữa hai văn bản được lưu bằng cách **ghi id nối `;` (hoặc mảng JSON bản chụp) vào
`dynamic_fields` của văn bản**, nhiều khi của cả hai phía, mỗi loại liên kết một field riêng:

| Liên kết | Field cũ trên văn bản | Field cũ trên hồ sơ |
|---|---|---|
| Hồ sơ → dự thảo | `document_profiles_id`, `hoso_attached_id` | `duthao_attached_id` |
| Hồ sơ → văn bản đi | `document_profiles_id` | `document_out_attached_id` |
| Hồ sơ → văn bản đi phát hành | `issuance_document_profiles_id`, `document_profiles_from_draft_id` | `document_out_attached_id` |
| Hồ sơ → văn bản đến / đến xử lý | `document_profiles_id` / `document_profiles_handling_id` | `document_in_attached_id` |
| Văn bản đi / dự thảo → văn bản đến trả lời | `documentin_obj` (mảng bản chụp) | — |
| Văn bản đi / dự thảo → văn bản kèm theo | `vanban_lienquan_obj` (mảng bản chụp) | — |

Hệ quả đã gặp thật:

- Quên ghi 1 trong 2–3 chỗ ⇒ hai phía lệch nhau (gắn báo thành công nhưng danh sách không hiện).
- Ghi kiểu đọc → sửa → ghi lại **toàn bộ** `dynamic_fields` ⇒ hai thao tác gần nhau ghi đè mất dữ liệu.
- Màn lưu chung gửi lại cả mảng `_obj` ⇒ ghi đè mất các văn bản trả lời gắn sau (dự thảo tạo từ văn bản đến).
- Truy vấn phải `LIKE '%;id;%'`, dính bẫy hoa/thường, số/chuỗi, mảng/chuỗi.

## 2. Thiết kế

```sql
document_links (
  id, source_type, source_id, target_type, target_id, link_type,
  link_data JSONB,   -- bản chụp phần tử hiển thị (giai đoạn 2), '{}' nếu không cần
  created_by, created_on, last_modified_*, deleted_on, deleted_by, version_no
)
UNIQUE (source_id, target_id, link_type) WHERE deleted_on IS NULL
```

- **1 dòng = 1 liên kết có hướng** (nguồn → đích). Tra được cả hai chiều qua index nguồn / đích.
- **Gỡ liên kết = xoá mềm** (`deleted_on`), đúng quy ước "xoá luôn là soft delete". Gắn lại sau khi gỡ
  tạo dòng mới. Gắn lại liên kết đang có là no-op (giữ bản chụp cũ — như dedup theo id trước đây).
- Thứ tự hiển thị = thứ tự gắn (`created_on` tăng dần).
- Mỗi tenant có bảng riêng (database per tenant) — Flyway tự tạo khi khởi động.

### Loại liên kết (`DocumentLinkType`, cột `link_type`)

| `link_type` | Nguồn → đích | Ghi bởi |
|---|---|---|
| `hoso_duthao` | hosocv → duthao | `GanKemVanBanUseCase` (actionName `gankemduthao`); FE gọi thêm sau khi tạo dự thảo từ hồ sơ |
| `hoso_vbdi` | hosocv → vbdi | `GanKemVanBanUseCase` (`ganvanbandi`) |
| `hoso_vbdi_phathanh` | hosocv → vbdi | `GanKemVanBanUseCase` (`ganvanbanphathanh`); **và** tự ghi khi văn bản đi được phát hành từ dự thảo của hồ sơ (`ChuyenThanhVanBanDiUseCase`, hoặc gắn dự thảo đã có văn bản đi) |
| `hoso_vbden` | hosocv → vbden | `GanKemVanBanUseCase` (`ganvanbanden`) |
| `hoso_vbden_xuly` | hosocv → vbden | `GanKemVanBanUseCase` (`ganvanbandenxuly`) |
| `hoso_phieutrinh` | hosocv → phiếu trình TD | `GanKemVanBanUseCase` (`gankemphieutrinh`, tra bảng `phieutrinhtd_documents`); FE gọi thêm khi tạo phiếu trình từ hồ sơ (từ 06/10/2026, cùng đợt cutover FE phiếu trình) |
| `duthao_vbdi` | duthao → vbdi | `ChuyenThanhVanBanDiUseCase` |
| `vbdi_traloi_vbden` / `duthao_traloi_vbden` | vbdi / duthao → vbden | `TraLoiVanBanDenUseCase` / `TraLoiVanBanDenDuThaoUseCase`; tạo mới văn bản/dự thảo có `documentin_obj` trong payload |
| `vbdi_kemtheo_vbden` / `vbdi_kemtheo_vbdi` | vbdi → vbden / vbdi | `GanVanBanLienQuanUseCase` (theo `database_type` của phần tử) |
| `duthao_kemtheo_vbden` / `duthao_kemtheo_vbdi` | duthao → vbden / vbdi | `GanVanBanLienQuanDuThaoUseCase` |

> `code` của loại liên kết là dữ liệu đã lưu — **không đổi tên** sau khi có dữ liệu; thêm loại mới thì
> thêm hằng mới vào enum.

`hoso_vbdi_phathanh` cho văn bản đi phát hành từ dự thảo được **ghi tường minh**, không suy ra qua chuỗi
hồ sơ → dự thảo → văn bản đi. Nhờ vậy nút "gỡ" ở tab "Văn bản phát hành" gỡ được đúng dòng đó.

## 3. Cách dùng trong code

```java
// Liên kết thường (gắn / gỡ idempotent)
documentLinkService.link(DocumentLinkType.HOSO_DUTHAO, hosocvId, duThaoId);
documentLinkService.unlink(DocumentLinkType.HOSO_DUTHAO, hosocvId, duThaoId);
documentLinkService.findTargetIds(hosocvId, List.of(DocumentLinkType.HOSO_VBDI, DocumentLinkType.HOSO_VBDI_PHATHANH));
documentLinkService.findSourceIds(duThaoId, List.of(DocumentLinkType.HOSO_DUTHAO)); // hồ sơ chứa dự thảo

// Liên kết có bản chụp ("Trả lời cho văn bản đến" / "Văn bản kèm theo") - qua RelatedDocumentSnapshots
relatedDocumentSnapshots.linkItem(DocumentLinkType.VBDI_TRALOI_VBDEN, vbdiId, item); // item = {id, code, ...}
relatedDocumentSnapshots.replyTargetIds(vbdiId, "vbdi");     // id văn bản đến, theo thứ tự gắn
relatedDocumentSnapshots.decorate(fields, vbdiId, "vbdi");   // dựng lại documentin_obj / vanban_lienquan_obj
```

`RelatedDocumentSnapshots` là điểm **duy nhất** biết khuôn của `documentin_obj` / `vanban_lienquan_obj`:

- **Đọc:** API chi tiết (`GetVbdiDetailUseCase`, `GetDuThaoDetailUseCase`) và dòng danh sách
  (`ViewQueryService`, khi `items` xin 1 trong 2 field hoặc `getall`) gọi `decorate` — FE nhận đúng mảng
  như trước, **không phải sửa FE**. Không có liên kết thì bỏ key (giữ điều kiện hiện panel cũ của FE).
- **Lưu chung:** `LuuDuThao`, `HieuChinhVbdi` gọi `strip` (bỏ 2 field khỏi payload); `TaoDuThao`,
  `SoanThaoVbdi` gọi `strip` rồi `importFromPayload` (chuyển phần tử thành liên kết — luồng "tạo dự thảo
  từ văn bản đến").

Danh sách văn bản có phân trang / từ khoá theo liên kết: lấy tập id qua `findTargetIds`, rồi gọi
`findByIds(...)` của repository từng module (`DocumentRepository`, `VbdiDocumentRepository`,
`DuThaoDocumentRepository`). Xem `ViewQueryService#queryDocumentsLinkedFrom`.

**Quy tắc:** liên kết văn bản mới **không** thêm field id / mảng bản chụp vào `dynamic_fields` nữa — dùng
`DocumentLinkService` (+ `RelatedDocumentSnapshots` nếu cần bản chụp) và thêm `DocumentLinkType`.

## 4. Nơi đọc từ bảng

| Chức năng | Code |
|---|---|
| Tab "Văn bản dự thảo / đi / đến" của hồ sơ (`vwDocument_Profiles_ID`) | `ViewQueryService#queryDocumentsLinkedToHoSo` |
| Tab "Văn bản phát hành" (`vwIssuance_Document_Profiles_ID`) | `ViewQueryService` → `hoso_vbdi_phathanh` |
| Tab "Văn bản đến xử lý" (`vwDocument_Profiles_Handling_ID`) | `ViewQueryService` → `hoso_vbden_xuly` |
| Hồ sơ chứa 1 văn bản (`vwSelect_Drafts_Groups_By_Document_Profiles_ID`) | `ViewQueryService#queryHoSoContainingDocument` — mục "Hồ sơ công việc" ở chi tiết dự thảo (`HoSoLienQuanPanel.jsx`) |
| Chi tiết hồ sơ (`van_ban_lien_quan`) | `GetHoSoChiTietUseCase` |
| Tải toàn bộ tệp hồ sơ | `DownloadFilesHoSoCVUseCase` |
| Chuyển hồ sơ sang lưu trữ (copy văn bản) | `TaoHoSoLuuTruTuHoSoCVUseCase` |
| Tab "Phiếu trình" của hồ sơ (`vwDocument_Profiles_ID`, `phieutrinhtd`) | `PhieuTrinhTdViewQuery` → `hoso_phieutrinh` |
| `documentin_obj` / `vanban_lienquan_obj` trong chi tiết + danh sách văn bản đi / dự thảo | `RelatedDocumentSnapshots#decorate` |
| Gửi phát hành kèm tệp văn bản đến (`phathanhkemvbden`) | `SendVbdiDocumentUseCase#resolvePhatHanhKemVbdenIds` |
| Ghi quá trình "cập nhật văn bản đi" lên văn bản đến | `CapNhatVanBanDiUseCase` |
| Chép liên kết dự thảo → văn bản đi khi phát hành | `ChuyenThanhVanBanDiUseCase` → `RelatedDocumentSnapshots#copyFromDraftToVbdi` |

Thay đổi hành vi có chủ ý:

- "Tải toàn bộ tệp" và "Chuyển lưu trữ" nay gồm cả văn bản đi **phát hành** và văn bản đến **xử lý** của
  hồ sơ (trước chỉ đọc `document_out_attached_id` / `document_in_attached_id`).
- Phần tử có id không phải UUID (văn bản Domino cũ) không còn được nhận khi gắn / không còn hiển thị.

## 5. Xử lý nghiệp vụ đi kèm (giữ trong use case)

Bảng chỉ thay phần **lưu** liên kết. Các xử lý sau vẫn nằm trong use case như cũ:

- `TraLoiVanBanDen*UseCase`: chuyển người đang giữ văn bản đến từ `user_process` sang `user_processing`,
  tạo `vbden_res` `ganduthaotraloi`. Gỡ (`XoaTraLoiVanBanDen*`) **không** hoàn tác (đúng Domino).
- `CapNhatVanBanDiUseCase`: tạo `vbden_res` `capnhatvanbandi` cho từng văn bản đến được trả lời.

## 6. Field cũ và dữ liệu cũ

- Không còn use case nào **ghi** các field liên kết cũ. Giá trị cũ còn nằm trong `dynamic_fields` không
  được đọc nữa (API dựng lại từ bảng, ghi đè giá trị cũ) — không cần xoá.
- `draft_id` (văn bản đi) / `document_out_id` (dự thảo) **vẫn ghi**: là field nghiệp vụ (thu hồi, kết thúc
  dự thảo... đọc), song song với liên kết `duthao_vbdi`.
- Backfill: `V1.0.69` (giai đoạn 1) và `V1.0.70` (giai đoạn 2) chuyển dữ liệu từ field cũ (chuỗi `a;b`,
  mảng JSON hoặc chuỗi JSON). Chỉ giữ id là UUID văn bản Java; id Domino cũ bị bỏ qua; chuỗi JSON hỏng
  (vd `"[{id=1, code=...}]"` do lỗi cũ) bị bỏ qua thay vì làm hỏng migration. Chạy thử (rollback) trên
  tenant `h05_01_sokhcn` ngày 06/10/2026: giai đoạn 1 = 28 liên kết, giai đoạn 2 = 47 liên kết.

## 7. Chưa làm

- Phiếu trình: **đã xong** từ 06/10/2026 cùng đợt cutover FE module phiếu trình (xem
  [../PHIEU_TRINH_CUTOVER_FE.md](../PHIEU_TRINH_CUTOVER_FE.md)) — mọi liên kết văn bản giờ đều nằm trong bảng.
- `documentin_obj` dạng **1 đối tượng** trên hồ sơ công việc / phiếu trình / hồ sơ lưu trữ (văn bản đến
  sinh ra hồ sơ / phiếu trình) — nguồn gốc của bản ghi, không phải danh sách liên kết; chưa chuyển.

**Không thuộc phạm vi** (trùng tên nhưng khác nghĩa — không phải liên kết văn bản):
`document_profiles_id` trên "văn bản khác" của hồ sơ (`LuuVanBanKhacUseCase`, con trỏ cha) và trên hồ sơ
lưu trữ (`HoSoLuuTruLifecycleStatus.FIELD_DOCUMENT_PROFILES_ID`, con trỏ về hồ sơ công việc gốc).

## 8. Tách microservices

Bảng liên kết là điểm dùng chung giữa các module. Khi tách service, nó nên thành một service "liên kết"
riêng; các module chỉ trao đổi id, không join chéo bảng của nhau. Hiện `findByIds` của từng module đã
tách đúng hướng đó (module tự đọc bảng của mình theo tập id).
