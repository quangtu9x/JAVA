package com.td.infrastructure.security;

/**
 * SEC-01 (QLVB Permission Model): tên quyền nghiệp vụ thật của hệ thống QLVB cũ (Domino), lấy từ
 * field {@code roles} trên hồ sơ người dùng (catalog DB), truyền nguyên văn vào claim {@code roles}
 * của Tdtoken/JWT — xem {@code TDAuthentication.buildUserToken()} và
 * {@code TDDatabaseConnection.getUserInformationAndPermissions()} phía Domino thật. Đây không phải
 * role ACL của Notes database — chỉ là chuỗi quyền logic ở tầng ứng dụng, được các service
 * Domino thật kiểm tra bằng {@code roles.contains(...)}/{@code TDRoles.checkRole(...)} (vd.
 * {@code xsp/services/vbden/ChuyenXuLy.java}, {@code xsp/services/danhmuc/CapNhatNhomACL.java}).
 *
 * <p>{@link TdTokenAuthenticationFilter}/{@code KeycloakJwtConverter} luôn build
 * {@link org.springframework.security.core.authority.SimpleGrantedAuthority} theo quy tắc
 * {@code "ROLE_" + role.toUpperCase()} — vì vậy các hằng số dưới đây giữ nguyên chữ HOA để dùng
 * trực tiếp trong {@code @PreAuthorize("hasAnyRole(...)")} mà không lệch case.
 *
 * <p>Phạm vi SEC-01 (05/08/2026): mới áp dụng cho {@code VbdenController} (BL-210) — nơi đã có bằng
 * chứng cụ thể (quyền "cập nhật/hiệu chỉnh văn bản đến", xem
 * {@code docs/frontend/05-vbden-vbdi.md} mục "Quyền"). Các quyền còn lại (VBDi, DanhMuc, PhanCong)
 * để sẵn cho các backlog module tương ứng (BL-220, ...) tái sử dụng khi có bằng chứng nghiệp vụ
 * riêng, tránh suy đoán phạm vi ngoài những gì đã xác minh.
 */
public final class QlvbRoles {

    private QlvbRoles() {
    }

    /** Quản trị viên hệ thống — tương đương {@code CVQuanTri} (vd. {@code CayCoCauToChuc.java}, cho phép {@code keys:"all"}). */
    public static final String QUAN_TRI = "CVQUANTRI";

    /** Quyền cập nhật/hiệu chỉnh văn bản đến — toàn cơ quan. */
    public static final String CAP_NHAT_VBDEN = "QCAPNHATVBDEN";

    /** Quyền cập nhật văn bản đến — phạm vi đơn vị. */
    public static final String CAP_NHAT_VBDEN_DV = "QCAPNHATVBDENDV";

    /** Quyền cập nhật văn bản đến — phạm vi phòng ban. */
    public static final String CAP_NHAT_VBDEN_PB = "QCAPNHATVBDENPB";

    /** Quyền xem văn bản đến (chưa dùng ở SEC-01 — đọc VBDen hiện chỉ yêu cầu đã đăng nhập, xem Javadoc {@code VbdenController}). */
    public static final String XEM_VBDEN = "QXEMVBDEN";

    /** Quyền cập nhật văn bản đi — toàn cơ quan (dự phòng cho BL-220). */
    public static final String CAP_NHAT_VBDI = "QCAPNHATVBDI";

    /** Quyền cập nhật văn bản đi — phạm vi đơn vị (dự phòng cho BL-220). */
    public static final String CAP_NHAT_VBDI_DV = "QCAPNHATVBDIDV";

    /** Quyền cập nhật văn bản đi — phạm vi phòng ban (dự phòng cho BL-220). */
    public static final String CAP_NHAT_VBDI_PB = "QCAPNHATVBDIPB";

    /** Quyền phân công xử lý — toàn cơ quan (dự phòng cho BL-211). */
    public static final String PHAN_CONG = "QPHANCONG";

    /** Quyền phân phối văn bản — toàn cơ quan (dự phòng cho BL-211). */
    public static final String PHAN_PHOI = "QPHANPHOI";

    /** Quyền phân công xử lý văn bản đến — phạm vi đơn vị (catalog app_permissions, code {@code QPhanCongDV}). */
    public static final String PHAN_CONG_DV = "QPHANCONGDV";

    /** Quyền phân phối văn bản đến — phạm vi đơn vị (catalog app_permissions, code {@code QPhanPhoiDV}). */
    public static final String PHAN_PHOI_DV = "QPHANPHOIDV";

    /** Quyền phân công xử lý văn bản đến — phạm vi phòng ban (catalog app_permissions, code {@code QPhanCongPhong}). */
    public static final String PHAN_CONG_PHONG = "QPHANCONGPHONG";

    /**
     * Bổ sung 09/09/2026 (port số liệu CẤP CƠ QUAN của {@code SoLuongCanXuLy} — xem
     * {@code SoLuongCanXuLy.java} thật, {@code handleUserRoles()}): 1 trong 5 role kích hoạt số liệu
     * cấp cơ quan. Ý nghĩa đầy đủ của viết tắt "KTTTTH" CHƯA xác nhận được (không suy đoán) — giữ
     * nguyên chuỗi Domino gốc.
     */
    public static final String KTTTTH = "QKTTTTH";
}
