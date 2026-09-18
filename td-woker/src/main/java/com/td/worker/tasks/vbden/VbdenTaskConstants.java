package com.td.worker.tasks.vbden;

/**
 * Hằng số task VBDEN cross-tenant — port {@code DocumentInProcess.java
 * #copyDocumentProcessToDocumentIn()}/{@code DocumentInProcessUnit.java} (Domino, form mail-in
 * {@code document_process_unit}) — xem javadoc {@link VbdenForwardUnitTaskHandler}.
 *
 * <p>Module RIÊNG {@code com.td.worker.tasks.vbden}, TÁCH KHỎI {@code com.td.worker.tasks.vbdi} dù
 * cùng thao tác trên {@code document_type="vbden"} — 2 cơ chế liên thông cross-tenant KHÁC NHAU về
 * bản chất nghiệp vụ (vbdi: gửi văn bản CHÍNH THỨC giữa 2 cơ quan; vbden.forward_unit: chuyển xử lý
 * NỘI BỘ một văn bản đến đã có sẵn cho 1 đơn vị con chạy tenant riêng), không nên gộp chung 1 gói.
 */
public final class VbdenTaskConstants {

    private VbdenTaskConstants() {
    }

    /** Đơn vị GỬI (nơi có văn bản gốc) → đơn vị con NHẬN xử lý (tenant khác). */
    public static final String TASK_FORWARD_UNIT = "vbden.forward_unit";

    /** Đơn vị con NHẬN → đơn vị gửi (xác nhận đã tạo bản sao, để dọn khỏi hàng đợi gốc). */
    public static final String TASK_FORWARD_UNIT_REPLY = "vbden.forward_unit.reply";

    /**
     * Bổ sung 08/09/2026 (yêu cầu người dùng: "muốn phản hồi trạng thái 'Đã đến' và các trạng thái
     * như chuyển xử lý... cho đơn vị 'Chuyển xử lý' vào quá trình xử lý"): đơn vị ĐANG GIỮ bản sao
     * (có thể không phải đơn vị con NHẬN đầu tiên nếu đã tự chuyển tiếp nội bộ) → đơn vị GỐC, mỗi khi
     * có hành động xử lý mới trên bản sao — xem {@link VbdenForwardUnitStatusTaskHandler}.
     */
    public static final String TASK_FORWARD_UNIT_STATUS = "vbden.forward_unit.status";

    /**
     * Bổ sung 08/09/2026 (giai đoạn 2 — yêu cầu người dùng: "giai đoạn 1 cơ bản đã xong, làm giai
     * đoạn 2 LayLaiVanBanUseCase/ThuHoiVanBanUseCase"): đơn vị GỐC ("Lấy lại VB"/"Thu hồi" nội bộ
     * tác động lên bản sao) → đơn vị con ĐANG GIỮ bản sao — xem {@link VbdenForwardUnitRevokeTaskHandler}.
     */
    public static final String TASK_FORWARD_UNIT_REVOKE = "vbden.forward_unit.revoke";
}
