package com.td.application.vbdi;

import java.util.Map;

/**
 * Bổ sung 28/08/2026 ("port luôn" nhánh "Hiệu chỉnh"): tách RA 1 nơi DUY NHẤT map trạng thái hợp lệ
 * Domino thô ("0"=DRAFT, "2"=ISSUED — CHỈ 2 mã có bằng chứng dxl trực tiếp, xem javadoc gốc
 * {@link SoanThaoVbdiUseCase}) &lt;-&gt; chữ có nghĩa nội bộ {@link VbdiStatus} — dùng CHUNG cho cả
 * chiều lưu ({@link SoanThaoVbdiUseCase}/{@link HieuChinhVbdiUseCase}) lẫn chiều đọc
 * ({@link GetVbdiDetailUseCase}) — trước đây 2 map riêng biệt (thuận/nghịch) định nghĩa ở 2 file
 * khác nhau, rủi ro lệch nhau khi 1 bên sửa mà quên bên kia.
 */
public final class VbdiLegacyStatusCodes {

    private static final Map<String, String> LEGACY_TO_INTERNAL = Map.of(
        "0", VbdiStatus.DRAFT,
        "2", VbdiStatus.ISSUED,
        // 28/08/2026 - xac nhan truc tiep XuLyDuThao.java (xem javadoc VbdiStatus#RETURNED_BY_CLERK).
        "3", VbdiStatus.RETURNED_BY_CLERK);

    private static final Map<String, String> INTERNAL_TO_LEGACY = Map.of(
        VbdiStatus.DRAFT, "0",
        VbdiStatus.ISSUED, "2",
        VbdiStatus.RETURNED_BY_CLERK, "3");

    private VbdiLegacyStatusCodes() {
    }

    /**
     * Dịch mã Domino thô ("0"/"2") sang chữ nội bộ ({@link VbdiStatus}); giá trị khác 2 mã đã biết
     * (kể cả chữ nội bộ gửi thẳng, hoặc giá trị lạ) được PASSTHROUGH nguyên văn — KHÔNG đoán thêm mã
     * nào khác (cùng nguyên tắc thận trọng đã ghi ở {@code SoanThaoVbdiUseCase}, không đoán mã "1").
     *
     * @return {@code null} nếu {@code rawStatus} null/rỗng — gọi nơi cần TỰ QUYẾT ĐỊNH default (tạo
     *         mới mặc định DRAFT; hiệu chỉnh mặc định GIỮ NGUYÊN, không đổi field không được gửi).
     */
    public static String toInternalOrNull(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        String trimmed = rawStatus.trim();
        return LEGACY_TO_INTERNAL.getOrDefault(trimmed, trimmed);
    }

    /** Dịch ngược chữ nội bộ ({@link VbdiStatus}) sang mã Domino thô — passthrough nếu chưa biết. */
    public static String toLegacyCode(String internalStatus) {
        return INTERNAL_TO_LEGACY.getOrDefault(internalStatus, internalStatus);
    }
}
