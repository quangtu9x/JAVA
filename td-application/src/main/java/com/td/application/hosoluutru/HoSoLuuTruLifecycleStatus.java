package com.td.application.hosoluutru;

/**
 * Hằng số vòng đời module "Hồ sơ lưu trữ" (hosoluutru) —
 * {@code docs/analysis/16-phan-tich-port-hosoluutru-domino-sang-java.md} mục 2.2/6.
 */
public final class HoSoLuuTruLifecycleStatus {

    /** Cột {@code status} của {@code HoSoLuuTruDocument} — lưu trực tiếp giá trị {@code form} Domino rút gọn. */
    public static final String FORM_CHO_TIEP_NHAN = "hoso_chuaduyet";
    public static final String FORM_DA_TIEP_NHAN = "hoso";

    /** {@code chuyenluutru}: "0"=chưa chuyển lưu trữ chính thức nội bộ, "1"=đã chuyển. */
    public static final String CHUYENLUUTRU_CHUA = "0";
    public static final String CHUYENLUUTRU_DA = "1";

    /**
     * {@code chuyenluutrutinh}: rỗng/"0"=chưa/thu hồi LTLS, "2"=đang chờ duyệt LTLS. "1"(đã duyệt)/
     * "3"(bị từ chối) KHÔNG port (không có bằng chứng FE/BE — xem tài liệu 16 mục 7 #2).
     */
    public static final String CHUYENLUUTRUTINH_CHUA = "";
    public static final String CHUYENLUUTRUTINH_THU_HOI = "0";
    public static final String CHUYENLUUTRUTINH_CHO_DUYET = "2";

    /** {@code status} của phiếu yêu cầu mượn/trả. */
    public static final String PHIEU_CHO_GIAI_QUYET = "0";
    public static final String PHIEU_CHAP_NHAN = "1";
    public static final String PHIEU_NGUOI_MUON_THU_HOI = "2";
    public static final String PHIEU_KHONG_CHAP_NHAN = "3";

    /** {@code status1} của phiếu yêu cầu (chỉ có ý nghĩa khi {@code status=PHIEU_CHAP_NHAN}). */
    public static final String PHIEU_DANG_MUON = "";
    public static final String PHIEU_DA_TRA = "1";
    public static final String PHIEU_KHO_THU_HOI = "2";

    public static final String FIELD_DOCUMENT_PROFILES_ID = "document_profiles_id";
    public static final String FIELD_HOSO_ID = "hoso_id";
    public static final String FIELD_IDENTIFIER = "identifier";
    public static final String FIELD_CATEGORY = "category";
    public static final String FIELD_CATEGORY_ID = "category_id";
    public static final String FIELD_CHUYENLUUTRU = "chuyenluutru";
    public static final String FIELD_CHUYENLUUTRU_DATE = "chuyenluutru_date";
    public static final String FIELD_CHUYENLUUTRUTINH = "chuyenluutrutinh";
    public static final String FIELD_USER_QUYENXEM = "user_quyenxem";
    public static final String FIELD_USER_DANGKYMUON = "user_dangkymuon";
    public static final String FIELD_STATUS1 = "status1";
    public static final String FIELD_RETURN_DATE = "return_date";
    public static final String FIELD_SUBJECT = "subject";
    public static final String FIELD_USER_CREATOR = "user_creator";

    private HoSoLuuTruLifecycleStatus() {
    }
}
