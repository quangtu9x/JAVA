package com.td.application.sharedcore;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// BL-118 (19/08/2026): hồ sơ tổ chức THẬT của 1 AppUser — thay cho các field hardcode/rỗng trước
// đây ở QLVBCompatAuthController (position/coquan/donvi/caplanhdao). Tên field theo đúng Domino gốc
// (agency/unit/department/agency_level — xem BE_THANHHOA ImportNguoiDung.java/LuuDanhMuc.java,
// đối chiếu legacyAdminProfile.json) — GetUserOrganizationProfileUseCase là nơi suy ra các giá trị
// này bằng cách đi lên Organization.parentId từ AppUser.organizationId, khớp đúng cơ chế
// "form"-matching (department/unit/agency/agency_level) mà Domino thật dùng khi denormalize tên
// hiển thị lên hồ sơ người dùng lúc tạo/sửa (KHÔNG suy luận lại mỗi lần đọc như trước đây nhầm
// tưởng — nhưng ở đây ta suy luận runtime từ organizationId vì AppUser modern KHÔNG lưu trùng dữ
// liệu tổ chức, xem javadoc AppUser.java).
@Data
public class UserOrganizationProfileDto {

    /** null nếu không tìm thấy AppUser tương ứng (vd tài khoản "admin" bootstrap không có hồ sơ thật). */
    private String username;
    private String fullName;
    // BL-118 (tiếp, 22/09/2026): UUID PK THẬT của AppUser - KHÁC HẲN "id" giả lập (hash username,
    // xem QLVBCompatAuthController#stableId) mà qlvb/profile trả cho FE bấy lâu nay. Bổ sung vì phát
    // hiện bug thật: FE gọi các endpoint shared-core cần UUID thật (vd GET .../users/{id}/signature-
    // images) bằng currentUser.id (số giả lập) luôn thất bại âm thầm (parse UUID lỗi -> catch rỗng),
    // dù dữ liệu đã cấu hình đúng. "" nếu không tìm thấy AppUser tương ứng (cùng điều kiện username null).
    private String appUserId = "";
    // 06/10/2026: AppUser.isActive - false = tài khoản đang bị Khoá (QLVBCompatAuthController chặn
    // đăng nhập). Mặc định true cho hồ sơ rỗng (không tìm thấy AppUser - đã có luật "chưa khai báo").
    private boolean active = true;
    // Bổ sung 22/09/2026 (bugfix): AppUser.phoneNumber (cột "phone_number" có sẵn trong DB) TỪNG
    // KHÔNG BAO GIỜ được đưa vào qlvb/profile - FE (TdPdfViewerSignModal.jsx và tdsimsign/tdsimpki.jsx
    // cũ) điền sẵn SĐT ký SIM-PKI từ `currentUser.phonenumber` nhưng field này luôn undefined với MỌI
    // tài khoản dùng backend Java (không phải chỉ 1 tài khoản cụ thể) - phát hiện khi test skhcn.
    private String phoneNumber = "";

    private String position = "";
    private String capLanhDao = "";
    private String identifier = "";
    // AppUser.level (độ sâu trong cây tổ chức, đếm từ GỐC = 1) dùng kèm "identifiers" để tính
    // identifier hiệu lực theo cấp (xem getIdentifier_by_level() ở global.jsx / TDUlties.java
    // get_Identifier_user_by_level — 2 bản giống hệt nhau, Java Domino + FE).
    private String level = "";
    // BL-118 (tiếp, 19/08/2026): "identifiers" (số nhiều) — chuỗi định danh TỪNG tổ tiên trong cây
    // tổ chức của user, nối bằng ";", THỨ TỰ LÁ → GỐC (org trực tiếp của user ở đầu, root/tỉnh ở
    // cuối) — đã xác nhận chắc chắn bằng cách đọc trực tiếp BE_THANHHOA
    // (ImportNguoiDung.java/LuuDanhMuc.java hàm capnhat_nguoidung, dòng ~264-311/505-580) ĐỐI CHIẾU
    // dữ liệu thật `legacyAdminProfile.json` ("identifiers":"G10.01.01;G10.06;;G10;" khớp
    // "parents":"Phòng...;Văn phòng Bộ;Cơ quan chuyên môn tỉnh;Tỉnh A;" — lá trước, gốc sau). Trước
    // đây (19/08/2026, lần đầu) cố tình để trống vì chưa đủ bằng chứng thứ tự — nay đã giải quyết,
    // xem docs/field-mapping/03-user-field-mapping.md #32. Dùng cho FE getIdentifier_by_level() —
    // trực tiếp phục vụ BL-2xx (Nhập mới VBDen: chọn "sổ văn bản" mặc định theo đơn vị).
    private String identifiers = "";
    // Bổ sung 19/08/2026 (2): "parentids" (số nhiều, KHÁC "identifier"/"identifiers" — Domino gốc là
    // chuỗi UNID Notes của từng tổ tiên, CÙNG thứ tự/CÙNG lượt đi lên với "identifiers" — chỉ khác
    // giá trị lấy từ mỗi node (UNID thay vì mã định danh mnemonic). Tương đương Java: UNID Domino ≈
    // UUID (Organization.id) — không có UNID Domino thật để tái tạo, dùng UUID của chính node đó vì
    // đây là "khoá định danh duy nhất của node trong cây tổ chức", đúng VAI TRÒ mà UNID đảm nhiệm
    // (không phải giá trị bit-for-bit giống Domino cũ, đã đổi hệ id-serving hoàn toàn khi migrate).
    // FE dùng qua get_ParentId_by_level() (utils/global.jsx) cho 1 số dropdown phụ (vd "Lãnh đạo
    // phân công" ở SubFormPhanVanBan.jsx) — trước đây trả rỗng do field này chưa từng được set.
    private String parentIds = "";
    // Bổ sung 25/09/2026 (báo lỗi người dùng: "Chi tiết văn bản đi -> Nhập giống VB trước" crash
    // "Cannot read properties of undefined (reading 'endsWith')" ở getTenDonvi_by_level): "parents" —
    // chuỗi TÊN từng tổ tiên, CÙNG thứ tự/lượt đi lên với "parentids" (lá → gốc, nối ";"), đúng payload
    // Domino gốc ("parents":"Phòng...;Văn phòng Bộ;Cơ quan chuyên môn tỉnh;Tỉnh A;" — xem
    // legacyAdminProfile.json). Trước đây CHƯA từng được set (cùng loại gap với parentids 19/08/2026)
    // — ~80 chỗ FE đọc currentUser.parents qua getTenDonvi_by_level (tên đơn vị theo cấp).
    private String parents = "";

    private String agencyLevelName = "";
    private String agencyLevelId = "";
    private String agencyName = "";
    private String agencyId = "";
    private String unitName = "";
    private String unitId = "";
    private String departmentName = "";
    private String departmentId = "";

    // "Kiêm nhiệm" (BL-112, AppUser.parentUserId) — danh sách chức vụ kiêm nhiệm của người dùng
    // này, mỗi mục có ĐẦY ĐỦ hồ sơ tổ chức riêng (username/fullName/position/agency/unit/...),
    // khớp đúng cơ chế Domino thật (TDDatabaseConnection.getJackSonUserInformationAndPermissions
    // "get kiem nhiem" -> "user_other_obj").
    private List<UserOrganizationProfileDto> kiemNhiem = new ArrayList<>();
}
