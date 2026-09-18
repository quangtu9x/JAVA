package com.td.worker.tasks.vbdi;

/**
 * Hằng số task VBDI — reference implementation cho luồng "Gửi văn bản đi & phản hồi trạng thái"
 * (docs/architecture/08 + 11 của project tham khảo). Worker Core hoàn toàn KHÔNG biết các
 * hằng số này — chúng chỉ nằm trong module task VBDI.
 */
public final class VbdiTaskConstants {

    private VbdiTaskConstants() {
    }

    /** Đơn vị GỬI → đơn vị NHẬN (port SendDocument.java). */
    public static final String TASK_DELIVERY = "vbdi.delivery";

    /** Đơn vị NHẬN → đơn vị GỬI (port ReplyDocumentOut.java / ResponseStatus). */
    public static final String TASK_REPLY = "vbdi.reply";

    /**
     * Bổ sung 08/09/2026 (người dùng báo lỗi thật: "td-worker ghi log DONE nhưng văn bản đến của
     * đơn vị vẫn còn ở danh sách" khi bấm "Lấy lại VB" — sendType {@code laylaivanban}/
     * {@code thuhoivanban}/{@code thuhoitoanbo}): TRƯỚC bản vá này, {@code AddVbdiSendLogUseCase}
     * (td-application) publish CẢ 3 sendType "thu hồi" này qua CÙNG taskType {@code vbdi.delivery}
     * như 1 lượt GỬI MỚI — {@link VbdiDeliveryTaskHandler} xử lý y hệt: dedup theo {@code sendLogId}
     * (LUÔN mới với hành động thu hồi, không trùng sendLogId của lượt gửi gốc) rồi TẠO THÊM 1 văn
     * bản đến "chờ vào sổ" MỚI + phản hồi "Đã đến" — hoàn toàn SAI nghiệp vụ (Domino
     * {@code RevokeDocumentOut.java}/{@code RevokeAllDocumentOut.java} không gửi mail form
     * {@code documentin} như {@code SendDocument.java}, mà gửi form {@code feedback_DocumentOut} —
     * xử lý ở {@code StatusProcess.ExecuteVBDIBussiness() -> ActionRevokeDocument()}, đọc lại
     * {@code docs/agent_domino/io/BeforeMailin.ja/StatusProcess.java} — HOÀN TOÀN KHÁC luồng
     * "delivery": tìm văn bản đến ĐÃ CÓ (theo {@code id_DocumentOut}) và đánh dấu Đã lấy lại/xoá
     * khỏi danh sách hoạt động, KHÔNG tạo văn bản mới). Task type RIÊNG này route sang
     * {@link VbdiRevokeTaskHandler} — port ĐÚNG {@code ActionRevokeDocument}.
     */
    public static final String TASK_REVOKE = "vbdi.revoke";

    // Bo sung 13/09/2026 (bao loi nguoi dung: "gui lai van ban chua gan dung trang thai cho don vi
    // nhan, lan dau gui la van ban moi, gui lai phai la van ban cap nhat") - dung VbdiSendType (td-
    // application) qua gia tri STRING (khong import - 2 module doc lap), doi chieu SendDocument.java
    // that (dong ~1776-1787): "guibosung" (gui bo sung noi nhan MOI) LUON ep ve "0" (coi nhu van ban
    // MOI, bat ke document_replace_retrieve cua chinh van ban la gi); "resenddocument"/"guilaitoanbo"
    // (gui lai - Domino goc actionname="guilaivanban") LUON ep ve "2" ("VB cap nhat") - xem javadoc
    // VbdenIncomingDocumentWriter#resolveEffectiveBussinessDocType.
    public static final String SEND_TYPE_GUI_BO_SUNG = "guibosung";
    public static final String SEND_TYPE_RESEND_DOCUMENT = "resenddocument";
    public static final String SEND_TYPE_GUI_LAI_TOAN_BO = "guilaitoanbo";
    /** = giá trị {@code bussiness_doc_type}/{@code document_replace_retrieve} "VB cập nhật" (gửi lại). */
    public static final String BUSSINESS_DOC_TYPE_CAP_NHAT = "2";

    // 4 nhóm nơi nhận (đúng VbdiSendRecipientGroup của td-web).
    public static final String GROUP_COORDINATOR = "coordinator";
    public static final String GROUP_INTERNAL_UNIT = "internal_unit";
    public static final String GROUP_CONTACT_UNIT = "contact_unit";
    public static final String GROUP_EXTERNAL_UNIT = "external_unit";

    // 6 mã trạng thái của StatusCode.java (Domino) — giữ nguyên làm mã máy đọc.
    public static final String STATUS_01_DA_DEN = "01";
    public static final String STATUS_02_TU_CHOI = "02";
    public static final String STATUS_03_DA_TIEP_NHAN = "03";
    public static final String STATUS_04_PHAN_CONG = "04";
    public static final String STATUS_05_DANG_XU_LY = "05";
    public static final String STATUS_06_HOAN_THANH = "06";
    // Bo sung 07/09/2026 (3, yeu cau nguoi dung: "Tu choi/Tra lai" phai phan hoi trang thai giong
    // 01/03 - doi chieu StatusProcess.java that): "tralai" LA MA PHI CHUAN cua chinh Domino goc
    // (khong thuoc StatusCode.java enum 01-06) nhung VAN di qua CHINH co che broker/response_status
    // nhu moi ma khac - giu nguyen literal string nay (khong ep ve so) de khop dung Domino.
    public static final String STATUS_TRA_LAI = "tralai";
    public static final String LABEL_TRA_LAI = "Trả lại";
    // Bo sung 08/09/2026 (xem javadoc TASK_REVOKE): "Da lay lai" - StatusProcess.SendReplyToSender
    // (goi tu ActionRevokeDocument) that su dung status_code="101" (CUNG PHI CHUAN nhu "tralai",
    // khong thuoc StatusCode.java 01-06) - giu nguyen literal nay de khop dung Domino.
    public static final String STATUS_101_DA_LAY_LAI = "101";
    public static final String LABEL_DA_LAY_LAI = "Đã lấy lại";

    public static final String LABEL_DA_DEN = "Đã đến";
    public static final String LABEL_GUI_VAN_BAN = "Gửi văn bản";
    // Bo sung 07/09/2026 (yeu cau nguoi dung: hoan thien broker phan hoi trang thai "vao so"/"phan
    // xu ly" - xem docs/architecture/11 addendum 07/09/2026 (4)).
    public static final String LABEL_DA_TIEP_NHAN = "Đã tiếp nhận";
    public static final String LABEL_PHAN_CONG_XU_LY = "Phân công xử lý";
    // Bo sung 07/09/2026 (2, yeu cau nguoi dung "lam not" STATUS_05/06 - xem javadoc
    // VbdiReplyTaskHandler#buildContent, ChuyenXuLyVbdenUseCase, KetThucXuLyUseCase).
    public static final String LABEL_DANG_XU_LY = "Đang xử lý";
    public static final String LABEL_HOAN_THANH = "Hoàn thành";
}