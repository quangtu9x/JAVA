package com.td.application.vbdi;

/**
 * BL-220 (VBDi Core): 3 trạng thái vòng đời tối thiểu của văn bản đi theo DoD (soạn thảo → trình ký
 * → ban hành). Domino thật mô hình hoá chi tiết hơn nhiều (mã {@code status} số 1-8 + bảng
 * {@code duthao} riêng — xem {@code XuLyDuThao.java}: {@code status="1"} chờ ký, {@code "2"} trả
 * chuyên viên, {@code "3"} văn thư trả lại, {@code "8"} đã ban hành/không ban hành...); ở đây dùng 3
 * giá trị chuỗi rõ nghĩa tối thiểu để thoả DoD BL-220, không replicate toàn bộ state machine
 * {@code duthao} (thuộc phạm vi BL-230, chưa xây).
 */
public final class VbdiStatus {

    private VbdiStatus() {
    }

    /** Soạn thảo — vừa tạo, chưa trình ký. */
    public static final String DRAFT = "DRAFT";

    /** Đã trình lãnh đạo ký duyệt — chờ ban hành. */
    public static final String PENDING_SIGNATURE = "PENDING_SIGNATURE";

    /** Đã ban hành — đã sinh số đi chính thức. */
    public static final String ISSUED = "ISSUED";

    /**
     * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương
     * ứng ở button của văn bản đi"): bị văn thư trả lại (cho lãnh đạo ký duyệt lại, hoặc cho người
     * soạn thảo sửa lại) — xác nhận TRỰC TIẾP tại {@code vn.com.tandan.xsp.services.vbdi.
     * XuLyDuThao#TraLaiDuThaoChoLanhDaoKyDuyet()}/{@code TraLaiDuThaoChoNguoiSoanThao()} (dòng
     * 252/366: {@code docDocument.replaceItemValue("status", "3")}, {@code docDocument} = CHÍNH
     * văn bản đi đang xử lý, KHÔNG phải dự thảo — khác {@code status="3"} trên duthao
     * ({@link com.td.application.duthao.DuThaoStatus#SIGNED_WAITING_SUBMIT}, field/entity khác
     * hẳn). Domino: {@code status="3"}.
     *
     * <p><b>Đính chính</b>: javadoc lớp này (viết trước khi đọc trực tiếp {@code XuLyDuThao.java})
     * suy đoán "2"="trả chuyên viên" — SAI, nhầm lẫn field {@code status} của vbdi với của duthao
     * (2 entity khác nhau, cùng bị 1 file Domino {@code services.vbdi.XuLyDuThao} thao tác). "2"
     * ĐÃ được xác nhận ĐÚNG là {@link #ISSUED} bằng bằng chứng dxl mạnh hơn (SELECT trực tiếp trên
     * view "đã ban hành" — xem javadoc {@code SoanThaoVbdiUseCase}) — KHÔNG đổi lại.</p>
     */
    public static final String RETURNED_BY_CLERK = "3";
}
