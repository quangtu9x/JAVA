package com.td.application.tonghop;

/**
 * Mã dùng chung giữa bộ đồng bộ ({@code ProvinceIndexSyncService}) và truy vấn tổng hợp toàn tỉnh.
 */
public final class ProvinceDocumentStates {

    private ProvinceDocumentStates() {
    }

    public static final String KIND_VBDEN = "VBDEN";
    public static final String KIND_VBDI = "VBDI";

    // vbden — suy từ documents.status (VbdenStatusService) + đã vào sổ hay chưa.
    public static final String CHO_VAO_SO = "CHO_VAO_SO";
    public static final String CHUA_XU_LY = "CHUA_XU_LY";
    public static final String DANG_XU_LY = "DANG_XU_LY";
    public static final String DA_XU_LY = "DA_XU_LY";
    public static final String TU_CHOI = "TU_CHOI";

    // vbdi — đã ban hành, đã/chưa gửi qua mạng.
    public static final String CHUA_GUI = "CHUA_GUI";
    public static final String DA_GUI = "DA_GUI";

    // Kênh.
    public static final String CHANNEL_NOI_BO = "NOI_BO";
    public static final String CHANNEL_LIEN_THONG = "LIEN_THONG";
    public static final String CHANNEL_TRUC_QG = "TRUC_QG";
    public static final String CHANNEL_GIAY = "GIAY";

    // Trạng thái 1 lượt gửi tới 1 nơi nhận (province_vbdi_dispatches.state), theo mã StatusCode Domino.
    public static final String DISPATCH_SENT = "SENT";             // đã gửi, chưa có phản hồi "Đã đến"
    public static final String DISPATCH_ARRIVED = "ARRIVED";       // 01
    public static final String DISPATCH_ACCEPTED = "ACCEPTED";     // 03
    public static final String DISPATCH_ASSIGNED = "ASSIGNED";     // 04
    public static final String DISPATCH_PROCESSING = "PROCESSING"; // 05
    public static final String DISPATCH_DONE = "DONE";             // 06
    public static final String DISPATCH_REFUSED = "REFUSED";       // 02 / tralai
    public static final String DISPATCH_REVOKED = "REVOKED";       // 101 / thu hồi / lấy lại
}
