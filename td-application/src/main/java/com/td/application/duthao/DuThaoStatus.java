package com.td.application.duthao;

/**
 * BL-230 (DuThao Core): trạng thái vòng đời của dự thảo.
 *
 * <p><b>Cập nhật 24/08/2026 (DT-001, tài liệu phân tích 04 mục 12)</b>: TRƯỚC ĐÂY class này chỉ có
 * {@link #DRAFTING}/{@link #PUBLISHED} (đủ cho DoD BL-230 gốc: tạo dự thảo → xin ý kiến phối hợp →
 * chuyển thành văn bản đi → thu hồi). Khi port {@code xulyduthao} ({@code DraftProcesses.
 * DraftProcessing()}) — luồng trình duyệt/ký nháy/ký duyệt/trả lại/chuyển người khác/kết thúc — cần
 * đủ các mã trạng thái Domino còn lại, dùng THẲNG chuỗi số Domino gốc (không đặt tên tiếng Anh
 * riêng) để đối chiếu trực tiếp với {@code DraftProcesses.java}/{@code view_duthao.dxl} khi audit
 * sau này.</p>
 *
 * <p><b>Cập nhật thứ 2, cùng ngày (theo yêu cầu người dùng — đang ở giai đoạn test, không cần lo
 * tương thích ngược với dữ liệu cũ)</b>: đổi {@link #DRAFTING}/{@link #PUBLISHED} từ 2 chuỗi Java
 * tự đặt ({@code "DRAFTING"}/{@code "PUBLISHED"}) sang ĐÚNG mã số Domino ({@code "0"}/{@code "4"})
 * — nhất quán với toàn bộ các hằng số còn lại trong class này (đều là chuỗi số Domino gốc) và với
 * {@code VanBanDuThaoTrangThai()} (FE, {@code utils/global.jsx}) vốn switch theo mã số Domino để
 * hiển thị nhãn trạng thái ("0"→"Đang soạn thảo", "4"→"Chuyển văn thư").</p>
 */
public final class DuThaoStatus {

    private DuThaoStatus() {
    }

    /** Đang soạn thảo — vừa tạo, hoặc quay lại soạn thảo sau ký nháy. Domino: {@code status="0"}/rỗng. */
    public static final String DRAFTING = "0";

    /** Chờ ký duyệt (đã trình lãnh đạo ký). Domino: {@code status="1"} — {@code action=chuyenkyduyet}. */
    public static final String PENDING_SIGNATURE = "1";

    /** Bị trả lại để soạn thảo lại. Domino: {@code status="2"} — {@code action=tralai}. */
    public static final String RETURNED = "2";

    /** Đã ký duyệt, chờ chuyển văn thư. Domino: {@code status="3"} — {@code action=kyduyet}. */
    public static final String SIGNED_WAITING_SUBMIT = "3";

    /** Đã chuyển thành văn bản đi. Domino: {@code status="4"} ({@code CopyDraftToDocumentOut()}). */
    public static final String PUBLISHED = "4";

    /** Chuyển duyệt (trình người xử lý trung gian). Domino: {@code status="5"} — {@code action=chuyenduyet}. */
    public static final String FORWARDED_FOR_APPROVAL = "5";

    /** Chuyển ký nháy. Domino: {@code status="6"} — {@code action=chuyenkynhay}. */
    public static final String PRE_SIGN_SUBMIT = "6";

    /** Kết thúc, không xử lý tiếp. Domino: {@code status="7"} — {@code action=ketthuc}. */
    public static final String ENDED = "7";

    /** Chuyển xử lý cho người khác. Domino: {@code status="9"} — {@code action=chuyennguoikhac}. */
    public static final String OTHER_HANDLER_SUBMIT = "9";

    /**
     * Yêu cầu người dùng 28/08/2026 ("port lại XuLyDuThao.java, class này có các chức năng tương
     * ứng ở button của văn bản đi"): đã hoàn tất xử lý ở khâu văn bản đi — HOẶC đã ban hành CHÍNH
     * THỨC (field động {@code documentout_id}/{@code documentout_code} có giá trị — xem
     * {@code DuThaoDaBanHanhUseCase}), HOẶC bị văn thư từ chối không ban hành (field động
     * {@code xoavanbandi} có giá trị — xem {@code DuThaoVanThuKhongBanHanhUseCase}). Domino:
     * {@code status="8"}, cả 2 nhánh CÙNG set giá trị này (đối chiếu trực tiếp
     * {@code vn.com.tandan.xsp.services.vbdi.XuLyDuThao.java} — 2 hàm
     * {@code ChuyenTrangThaiDuThaoDaBanHanh()}/{@code ChuyenTrangThaiDuThaoVanThuXoa()} cùng ghi
     * {@code status="8"}, phân biệt kết quả bằng 2 field động nói trên, không phải bằng status).
     */
    public static final String DOCUMENT_OUT_FINALIZED = "8";
}
