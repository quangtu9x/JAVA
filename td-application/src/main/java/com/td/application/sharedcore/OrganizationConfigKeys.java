package com.td.application.sharedcore;

/**
 * 01/10/2026 (yêu cầu người dùng "nhánh riêng của HĐND tỉnh → làm cấu hình"): khoá cấu hình riêng của
 * TỪNG tổ chức, lưu trong {@code Organization.dynamicFieldsJson} (cùng cơ chế {@code cfg_*} đã có — sửa
 * ở màn "Cơ cấu tổ chức"). Thay cho phần FE gắn cứng trước đây (danh sách mã {@code K56.*}, so tên cấp
 * ban hành bằng chuỗi, UNID Domino {@code 0C67B4CA...}, view {@code vwTruongPhongHDNDTinh}).
 *
 * <p>Đặt TRÊN TỔ CHỨC LÀ "CẤP BAN HÀNH" (vd "Hội đồng nhân dân thành phố"):</p>
 * <ul>
 *   <li>{@link #KY_THUA_LENH_SOURCE} — nguồn người ký khi Hình thức ký = "Ký thừa lệnh": rỗng = thuật
 *   toán chuẩn ({@link GetKyThuaLenhSignersUseCase}); {@link #KY_THUA_LENH_SOURCE_LEADERS_OF_ORG} = lãnh
 *   đạo TRỰC TIẾP của 1 tổ chức ({@link #KY_THUA_LENH_SOURCE_ORG_ID}, rỗng = chính tổ chức này);
 *   {@link #KY_THUA_LENH_SOURCE_POSITION_IN_ORGS} = người giữ chức vụ {@link #KY_THUA_LENH_POSITION}
 *   trong các tổ chức {@link #KY_THUA_LENH_SOURCE_ORG_IDS} (danh sách id nối ";").</li>
 *   <li>{@link #HIDE_FROM_CAP_BAN_HANH} = "1" — không hiện tổ chức này trong dropdown "Cấp ban hành".</li>
 * </ul>
 *
 * <p>Đặt TRÊN CƠ QUAN ({@code agency}) của người dùng: {@link #CAP_BAN_HANH_INCLUDE_CHILDREN} = "1" — bổ
 * sung các đơn vị con TRỰC TIẾP của cơ quan vào dropdown "Cấp ban hành" (HĐND: các Ban/Hội đồng/Đoàn).</p>
 */
public final class OrganizationConfigKeys {

    public static final String KY_THUA_LENH_SOURCE = "cfg_kythualenh_nguon";
    public static final String KY_THUA_LENH_SOURCE_LEADERS_OF_ORG = "lanhdao_to_chuc";
    public static final String KY_THUA_LENH_SOURCE_POSITION_IN_ORGS = "chucvu_cac_to_chuc";
    public static final String KY_THUA_LENH_SOURCE_ORG_ID = "cfg_kythualenh_to_chuc_id";
    public static final String KY_THUA_LENH_SOURCE_ORG_IDS = "cfg_kythualenh_to_chuc_ids";
    public static final String KY_THUA_LENH_POSITION = "cfg_kythualenh_chuc_vu";
    public static final String HIDE_FROM_CAP_BAN_HANH = "cfg_an_khoi_capbanhanh";
    public static final String CAP_BAN_HANH_INCLUDE_CHILDREN = "cfg_capbanhanh_them_don_vi_con";

    private OrganizationConfigKeys() {
    }
}
