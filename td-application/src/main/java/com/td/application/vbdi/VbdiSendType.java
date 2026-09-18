package com.td.application.vbdi;

/**
 * Yêu cầu người dùng 28/08/2026 ("Nhật ký gửi nhận"): 4 giá trị {@code action}/{@code actionname}
 * Domino thật cho hành động gửi văn bản đi (xem {@code docs/analysis/07-phan-tich-port-senddocument-
 * vbdi-qlvb.md} §2 mở đầu — {@code POST qlvb/vbdi?...&action=senddocument|resenddocument&actionname=
 * guilaitoanbo}).
 */
public final class VbdiSendType {

    private VbdiSendType() {
    }

    /** Gửi lần đầu ({@code action=senddocument}) — mặc định nếu request không truyền. */
    public static final String SEND_DOCUMENT = "senddocument";

    /** Gửi lại (đã gửi trước đó, gửi lại — {@code action=resenddocument}). */
    public static final String RESEND_DOCUMENT = "resenddocument";

    /** Gửi bổ sung nơi nhận mới (không gửi lại nơi đã gửi). */
    public static final String GUI_BO_SUNG = "guibosung";

    /** Gửi lại TOÀN BỘ nơi nhận đã có, kể cả nơi đã gửi ({@code actionname=guilaitoanbo}). */
    public static final String GUI_LAI_TOAN_BO = "guilaitoanbo";

    /**
     * 29/08/2026 (yêu cầu người dùng "Thu hồi toàn bộ/thu hồi theo đơn vị phân tích và thực hiện
     * port luôn"): thu hồi 1 PHẦN nơi nhận đã CHỌN — port {@code RevokeDocumentOut.java} (Domino,
     * action {@code revokedocumentout}, đọc {@code actionname} từ body để chọn nhãn — 2 giá trị
     * {@code thuhoivanban}/{@code thuhoivanbanall} CÙNG NHÃN "Thu hồi ngay", chỉ giữ 1 hằng số).
     * Domino gốc: đổi {@code status} của phần tử log KHỚP {@code id} thành "Thu hồi" (element-level,
     * ghi ĐÈ) + thêm 1 log con nhãn "Thu hồi ngay" — bảng typed của ta APPEND-ONLY (không update),
     * nên port thành: ghi THÊM 1 dòng mới trạng thái "Thu hồi ngay" cho mỗi nơi nhận được chọn
     * (đúng tinh thần "1 sự kiện = 1 dòng" đã dùng cho "đã xem", xem
     * {@code docs/analysis/07-phan-tich-port-senddocument-vbdi-qlvb.md} addendum 28/08/2026).
     * KHÔNG validate nơi nhận đã từng có vết gửi trước đó (cùng mức permissive như
     * {@link #RESEND_DOCUMENT}/{@link #GUI_BO_SUNG} — không tự chế thêm ràng buộc Domino gốc không
     * yêu cầu tường minh ở tầng ghi log).
     */
    public static final String THU_HOI_VAN_BAN = "thuhoivanban";

    /**
     * 29/08/2026 (cùng đợt port trên): CÙNG action Domino {@code revokedocumentout} với
     * {@link #THU_HOI_VAN_BAN} (Domino xử lý 100% GIỐNG NHAU, chỉ khác nhãn hiển thị "Lấy lại ngay"
     * thay vì "Thu hồi ngay" — xem {@code actionname_temp.equalsIgnoreCase("laylaivanban")}). Tách
     * hằng số riêng (không dùng chung {@link #THU_HOI_VAN_BAN}) để giữ ĐÚNG nhãn hiển thị khác nhau
     * trên UI/log lịch sử, đúng hành vi Domino.
     */
    public static final String LAY_LAI_VAN_BAN = "laylaivanban";

    /**
     * 29/08/2026 (cùng đợt port trên): thu hồi/lấy lại TOÀN BỘ nơi nhận — port
     * {@code RevokeAllDocumentOut.java} (Domino, action RIÊNG {@code Allrevokedocumentout}, KHÔNG
     * nhận tham số chọn nơi nhận, áp dụng cho MỌI nơi nhận hiện có, KHÔNG đọc {@code actionname} nên
     * CHỈ 1 nhãn cố định "Lấy lại" cho mọi trường hợp — khác {@link #THU_HOI_VAN_BAN}/
     * {@link #LAY_LAI_VAN_BAN} vốn có 2 nhãn tuỳ actionname). FE tự đọc lại toàn bộ nơi nhận hiện
     * tại qua {@code SendLog} rồi gửi kèm (cùng cách đã làm cho {@link #GUI_LAI_TOAN_BO}) — use case
     * này KHÔNG tự suy ra "toàn bộ", giữ đúng nguyên tắc {@code SendVbdiDocumentUseCase} không tự
     * đọc lại trạng thái hiện có.
     */
    public static final String THU_HOI_TOAN_BO = "thuhoitoanbo";

    /**
     * 29/08/2026 (yêu cầu người dùng "gửi văn bản thay thế ... phân tích và thực hiện port luôn",
     * tham khảo {@code docs/analysis/07-...} addendum 29/08/2026 (4)): gửi THÔNG BÁO "thay thế/thu
     * hồi" tới đơn vị/cá nhân đã chọn khi soạn 1 văn bản B đánh dấu thay thế/thu hồi văn bản A (xem
     * {@link GanVbdiDocumentUseCase} — liên kết B→A, TÁCH RIÊNG khỏi việc gửi này) — port
     * {@code SendDocument.java}, {@code actionname="UnitRevoke"} + {@code bosung="guibosung"} (Domino
     * đòi hỏi CẢ 2 điều kiện đồng thời mới set {@code unit_replace_revoke} — ở Java gộp lại thành 1
     * sendType duy nhất, vì FE luôn gửi kèm cả 2 khi actionname này được kích hoạt, không có ca nào
     * chỉ 1 trong 2 xảy ra độc lập với hành động "gửi thông báo thay thế"). Giữ NGUYÊN case Domino
     * ("UnitRevoke", không lowercase) vì FE ({@code YkienModal.jsx}/{@code DocumentInfo.jsx}) đã
     * dùng chuỗi literal này làm {@code actionname} ở nhiều nơi (vd {@code openModalChoYKien(
     * "UnitRevoke")}) — tái dùng ĐÚNG hằng số, tránh lệch chuỗi giữa FE/BE.
     */
    public static final String UNIT_REVOKE = "UnitRevoke";
}
