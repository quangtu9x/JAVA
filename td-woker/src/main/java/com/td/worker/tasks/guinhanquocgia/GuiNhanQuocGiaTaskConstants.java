package com.td.worker.tasks.guinhanquocgia;

/**
 * Hằng số task "Gửi nhận quốc gia" — module MỚI (bổ sung 09/09/2026), TÁCH BIỆT HOÀN TOÀN khỏi
 * {@code com.td.worker.tasks.vbdi}/{@code com.td.worker.tasks.vbden} (không sửa gì 2 module đó).
 * THAM KHẢO (không copy nguyên) kiến trúc/mã nguồn ở {@code docs/td-guinhanquocgia} (project Java/
 * Maven cũ, thuần Java KHÔNG phải Spring Boot, đã dùng thật để tích hợp Trục liên thông văn bản
 * quốc gia VPCP/VXP, chuẩn eDXML QCVN 102) — xem đầy đủ quyết định thiết kế ở
 * {@code docs/16-checklist-tich-hop-gui-nhan-quoc-gia.md}.
 *
 * <p>Convention đặt tên {@code <module>.<action>} đúng như {@code vbdi.delivery}/{@code vbdi.reply}
 * đã có.</p>
 */
public final class GuiNhanQuocGiaTaskConstants {

    private GuiNhanQuocGiaTaskConstants() {
    }

    /**
     * Đơn vị GỬI văn bản điện tử đi qua Trục liên thông văn bản quốc gia (VPCP/VXP) — port tham
     * khảo {@code ServiceTest.java} case 7 ("gui van ban", gọi {@code KnobstickServiceImp.sendEdoc}).
     */
    public static final String TASK_SEND = "guinhanquocgia.send";

    /**
     * CHƯA triển khai ở bản scaffold này (🔴 xem checklist mục "Việc còn lại") — nhận văn bản đến từ
     * Trục liên thông (port tham khảo {@code ServiceTest.java} case 4, {@code getReceivedEdocList}).
     * Bản chất là POLLING (không phải PUSH qua broker như vbdi/vbden), cần thiết kế lịch
     * {@code @Scheduled} riêng trước khi có 1 TaskHandler thật — KHÔNG khai báo hằng số taskType ở
     * đây để tránh nhầm là "đã đăng ký nhưng chưa cài" (đúng nguyên tắc TaskHandlerRegistry chỉ đăng
     * ký bean thật tồn tại).
     */
    // public static final String TASK_RECEIVE = "guinhanquocgia.receive"; // (giữ chú thích, chưa bật)

    // 4 loại văn bản điện tử (BussinessDocType của SDK EdXML102 — xem docs/td-guinhanquocgia/docs/03).
    public static final String DOC_TYPE_NEW = "new";
    public static final String DOC_TYPE_REVOCATION = "revocation";
    public static final String DOC_TYPE_UPDATE = "update";
    public static final String DOC_TYPE_REPLACEMENT = "replacement";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    /** Bảng dedup/audit RIÊNG của Worker — nằm trong database CỦA WORKER (không phải tenant). */
    public static final String SEND_LOG_TABLE = "worker_guinhanquocgia_send_log";
}
