package com.td.application.vbdi;

/**
 * Yêu cầu người dùng 28/08/2026 ("Nhật ký gửi nhận"): 4 nhóm nơi nhận CỨNG của Domino thật
 * ({@code SendDocument.java}, xem {@code docs/analysis/07-phan-tich-port-senddocument-vbdi-qlvb.md}
 * §2 điểm 1) — KHÔNG phải field quyền đơn lẻ mà là 4 nhóm nghiệp vụ riêng biệt, mỗi nhóm 1 field
 * Domino {@code *_obj}/{@code *_obj_log_rtext} tương ứng.
 */
public final class VbdiSendRecipientGroup {

    private VbdiSendRecipientGroup() {
    }

    /** Cá nhân/phê duyệt — Domino {@code coordinator_obj}/{@code coordinator_obj_log_rtext}. */
    public static final String COORDINATOR = "coordinator";

    /** Đơn vị nội bộ — Domino {@code internal_unit_obj}/{@code internal_unit_obj_log_rtext}. */
    public static final String INTERNAL_UNIT = "internal_unit";

    /** Cơ quan liên hệ — Domino {@code contact_unit_obj}/{@code contact_unit_obj_log_rtext}. */
    public static final String CONTACT_UNIT = "contact_unit";

    /** Cơ quan ngoài/liên thông quốc gia — Domino {@code external_unit_obj}/{@code external_unit_obj_log_rtext}. */
    public static final String EXTERNAL_UNIT = "external_unit";
}
