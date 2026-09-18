package com.td.application.privatecatalog;

/**
 * Yêu cầu người dùng 28/08/2026 ("port luôn nhóm phát hành nhanh") — 3 giá trị {@code type} hợp lệ
 * cho {@link com.td.domain.privatecatalog.PrivateCategory} Form {@link
 * GetGroupUnitForIdentifierUseCase#GROUP_UNIT_FORM}, khớp ĐÚNG 3/4 lựa chọn Select "Loại nhóm" ở
 * {@code ThanhHoaDEV/src/app/pages/qlvb/nhomdonvi/components/ChiTietModal.jsx} (lựa chọn thứ 4,
 * "Chuyển xử lý", KHÔNG thuộc "nhóm phát hành nhanh" — dùng cho ngữ cảnh khác, ngoài phạm vi).
 */
public final class GroupUnitType {

    private GroupUnitType() {
    }

    /** Dùng cho cây "Cơ quan ngoài {CAP}". */
    public static final String EXTERNAL_UNIT = "Phát hành cơ quan ngoài";

    /** Dùng cho cây "Cơ quan, đơn vị thuộc {CAP}". */
    public static final String CONTACT_UNIT = "Phát hành cơ quan trong tỉnh";

    /** Dùng cho cây "Đơn vị nội bộ". */
    public static final String INTERNAL_UNIT = "Phát hành đơn vị nội bộ";
}
