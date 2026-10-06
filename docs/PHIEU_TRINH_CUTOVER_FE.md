# Phiếu trình — cutover FE sang Java (06/10/2026)

Module **phiếu trình thẩm định** (`phieutrinhtd`, route FE `phieutrinhtd/*`) chuyển hẳn từ Domino
(`qlvb/get|add|delete|restore|view/query|search?databasetype=phieutrinhtd(_res)`) sang Java
`api/v1/phieutrinhtd` (`PhieuTrinhTdController`). Thiết kế port gốc (bảng, trạng thái):
`docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md`.

> Module `phieutrinh` (không "TD") **không có màn hình nào đang dùng** — chỉ còn code chết; không port.

## 1. Bảng ánh xạ Domino → Java

| Chức năng FE | Domino cũ | Java | FE gọi |
|---|---|---|---|
| Danh sách 16 tab (`xuly/*`) | `qlvb/view/query` + viewname Domino | `POST /GetView` — **nhận đúng tên view Domino** | `ItemsList.jsx` (đổi URL) |
| Tìm kiếm (thường + nâng cao) | `qlvb/search` (`search.form=submission_dossier`) | `/GetView` viewname `timkiem` + `keyword` + `search` | `ItemsList.jsx`, màn Tìm kiếm chung |
| Tra cứu nhóm (5 view `vwBy_*`) | `qlvb/view/query&viewaction=group` | `/GetView` + `ischild`/`navposition` | `GroupTable.jsx` / `TableInGroup.jsx` (rẽ nhánh riêng `phieutrinhtd`) |
| Chi tiết | `qlvb/get` | `/XemChiTiet` (kèm `files` + `quatrinh`) | `xemChiTietPhieuTrinh` |
| Tạo mới | `qlvb/add` (form `submission_dossier`) | `/TaoPhieuTrinh` | `taoPhieuTrinh` |
| Sửa / Kết thúc / Huỷ kết thúc | `qlvb/add?id=` | `/LuuPhieuTrinh` (merge field, không đổi status) | `luuPhieuTrinh` |
| Xoá / Phục hồi | `qlvb/delete` / `qlvb/restore` | `/XoaPhieuTrinh` / `/PhucHoiPhieuTrinh` (xoá mềm) | `xoaPhieuTrinh` / `phucHoiPhieuTrinh` |
| Trình thông qua (2), Trình duyệt (5), Thu hồi (0), Thông qua / Không thông qua (4/3), Duyệt / Không duyệt (7/6) | 2 lần `qlvb/add` (dòng `quatrinh` + cập nhật cha) | `/ChuyenTrangThai` (1 lần, trả `processId`) | `chuyenTrangThaiPhieuTrinh` |
| Cho ý kiến góp ý (người phối hợp) | `qlvb/add` form `document_coordinated_opinion` | `/ChoYKien` (**mới**) | `choYKienPhieuTrinh` |
| Tab Quá trình / Ý kiến góp ý | `qlvb/view/query?databasetype=phieutrinhtd_res` | `/GetView` `vwQuaTrinh_Submission_Dossier_ID` / `vwCoordinated_Opinion_Submission_Dossier_ID` | `QuaTrinhItemsList.jsx`, `YKienPhoiHopItemsList.jsx` |
| Tệp đính kèm | `Files` trong `qlvb/add`, `qlvb/vbden/getFile`, `qlvb/removefile` | API tệp chung `api/v1/documents/{id}/files` | `Files/index.jsx` (đã có nhánh Java) + `uploadPhieuTrinhFiles` |
| Gắn / gỡ phiếu trình vào hồ sơ, tab Phiếu trình của hồ sơ | `qlvb/hosocv/xulyganvaohscv` + view Domino | `hosocv/GanVanBan` (`gankemphieutrinh`) + `/GetView vwDocument_Profiles_ID` | bảng `document_links` (`hoso_phieutrinh`) |
| Tạo phiếu trình từ văn bản đến | `capnhatVBden("taophieutrinh")` → Domino | `vbden/TaoDuThao` với `action="taophieutrinh"` | `global.jsx#capnhatVBden` |
| Badge đếm (sidebar/dashboard) | — | `vbden/SoLuongCanXuLy` nay đủ 7/7 khoá (thêm `phieutrinh_chuaphoihop`) | `Notifications.tsx` |
| Cấp trình / lãnh đạo duyệt (danh mục) | `qlvb/danhmuc/publish` | `organizationApi#getCapBanHanhOptions` / `getOrganizationLeaders` | `ChiTietPage.jsx` |

Lớp FE: `ThanhHoaDEV/src/utils/phieutrinhtdApi.js`. Lớp Java mới: `PhieuTrinhTdViewQuery`,
`PhieuTrinhTdListCriteria` (+ `PhieuTrinhTdDocumentRepository#findByCriteria`), `PhieuTrinhTdFieldMapper`,
`ChoYKienPhieuTrinhTdUseCase`.

## 2. Danh sách — tên view Domino được nhận

`PhieuTrinhTdViewQuery` (được `ViewQueryService` chuyển tiếp **trước** switch chung vì nhiều tên view trùng
module khác). Khoá `$username$` / thiếu khoá = người đăng nhập. Mọi view cá nhân chỉ lấy phiếu trình **chưa
kết thúc** (`storaged != "1"`) như DXL; mới tạo nhất trước; hỗ trợ `keyword`.

| View Domino (tên Java cũ) | Điều kiện |
|---|---|
| `vwUser_All` (`phieutrinhtd_toanbo`) | người tham gia: người tạo, người thông qua (đã trình 2), đồng ý / từ chối, lãnh đạo duyệt (đã trình 5/25), người phối hợp |
| `vwUser_Creator`, `vwUser_Creator_0/2/3/4/5_25/6_26/7_27` | người tạo + trạng thái |
| `vwUser_NguoiThongQua_2` | người thông qua, trạng thái 2 |
| `vwUser_NguoiDuyet_5_25` | lãnh đạo duyệt cấp 1 khi 5 / cấp 2 khi 25 |
| `vwUser_Coordinator` / `_chua` / `_da` | người phối hợp (tất cả / chưa cho ý kiến / đã cho ý kiến) |
| `vwUser_Creator_Coordinated_Status` (+ khoá 2 `"1"`/`"0"`) | người tạo, có phối hợp (`"1"` mọi người đã cho ý kiến, `"0"` còn người chưa) |
| `vwStoraged` | người tham gia, **đã kết thúc** |
| `vwTrash` | thùng rác **của người tạo** (bản cũ trả thùng rác của mọi người — đã sửa) |
| `vwDocument_Profiles_ID` | phiếu trình gắn vào hồ sơ (khoá = id hồ sơ, bảng `document_links`) |
| `timkiem` | tìm kiếm: người tham gia (cả đã kết thúc) + `keyword` + `search` (`create_date` = [từ, đến], `status`, field khác = "chứa") |
| `vwBy_Create_Date/Creator/Nguoi_Thong_Qua/Nguoi_Duyet/Status` | tra cứu nhóm **1 cấp** (xem mục 4) |

## 3. Chuyển trạng thái

`ChuyenTrangThaiPhieuTrinhTdUseCase`: 1 lần gọi ghi dòng quá trình (`form=quatrinh`, `action_type`,
`subject`/`opinion`, người nhận) + `status` + `trinh<N>_date` / `ykientrinh<N>` + reset các bước sau.
Các field nghiệp vụ FE vẫn tự tính như bản Domino và gửi trong `fields` (người thông qua chọn ở bước
trình, `receivers_agree/refuse/revoke`, ý kiến / ngày theo từng người `trinh<N>_<user>_date`) — server gộp
**trước** khi xác định người nhận; không ghi đè được `status`/`form`/`user_creator`/`creator`. Kết quả
`{id, processId}` — FE tải tệp của bước lên `processId`.

## 4. Giới hạn / quyết định có chủ đích

- **Dữ liệu phiếu trình cũ ở Domino không được chuyển sang** (bảng `phieutrinhtd_documents` trên DB dev
  ngày 06/10/2026 = 0 bản ghi). Sau cutover, danh sách chỉ hiện phiếu trình tạo mới trên Java. Nếu cần
  phiếu trình cũ phải có đợt migrate dữ liệu riêng (chưa làm).
- **Tra cứu nhóm 1 cấp** (Domino nhiều cấp): năm tạo / người tạo / người thông qua / người duyệt / mã trạng
  thái; phạm vi = phiếu trình người dùng có tham gia (tương đương quyền đọc Notes), tối đa 5000 bản ghi.
- **Vết hiệu chỉnh** (form `log`) không port — bản Domino vốn ghi nhầm vào `qlvb` (thiếu databasetype).
- Còn gọi Domino (không phải dữ liệu phiếu trình): ảnh chữ ký người dùng (`qlvb/get?databasetype=qlvb`
  `anhkyso`), các ô chọn người / lãnh đạo ở **bảng tìm kiếm nâng cao** (`CollapsePhieuTrinhTD.jsx`).
- Code chết giữ nguyên: `ActionModal#btnActionTrinh_Auto` (chỉ tham chiếu từ JSX đã comment), các
  `*/components/ItemsList.jsx` theo từng thư mục, `PhieuTrinhActionModal.jsx`, `PhieuTrinhButtonAction.jsx`.
- Ngoài phạm vi: vbdi "Trả lại phiếu trình" (`submission_dossier/{id}/reject`, endpoint cũ không thuộc
  `qlvb`); `capnhatVBden("xoahoso")` khi xoá phiếu trình tạo từ văn bản đến vẫn gọi Domino.
- `documentin_obj` của phiếu trình (1 đối tượng — văn bản đến sinh ra phiếu trình) vẫn là field thường.

## 5. Kiểm thử

- Java: `PhieuTrinhTdViewQueryTest`, `PhieuTrinhTdCutoverUseCasesTest`, `GetPhieuTrinhTdChiTietUseCaseTest`,
  `PhieuTrinhTdControllerTest`, `GetSoLuongCanXuLyUseCaseTest`; toàn bộ 4 module 2044 test pass (06/10/2026).
- SQL của `findByCriteria` (7 kiểu khớp người + phối hợp + lọc nâng cao) đã chạy thử trên PostgreSQL dev.
- FE: 31 file sửa đã parse cú pháp; **chưa thử trên giao diện** — cần test tay luồng: tạo (kèm tệp, từ hồ
  sơ, từ văn bản đến) → trình thông qua → thông qua / không thông qua → trình duyệt → duyệt / không duyệt →
  thu hồi → kết thúc / huỷ kết thúc → xoá / phục hồi; người phối hợp cho ý kiến; tra cứu nhóm; tab Phiếu
  trình của hồ sơ.
