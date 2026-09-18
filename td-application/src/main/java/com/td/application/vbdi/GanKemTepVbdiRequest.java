package com.td.application.vbdi;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 15/09/2026 ("ký số lưu vết tương tự như BTE, dùng chung cho toàn bộ các module
 * khác sử dụng ký số"): request cho {@code POST /api/v1/vbdi/GanKemTep} — CÙNG SHAPE
 * {@link com.td.application.duthao.GanKemTepDuThaoRequest} (duthao), chỉ đổi phạm vi sang vbdi. Ghi
 * 1 vết {@code vbdi_res} (document con, KHÔNG đổi trạng thái văn bản đi cha — xem javadoc
 * {@link GanKemTepVbdiUseCase}) mang ý kiến, hiện ở tab "Quá trình xử lý" của văn bản đi.
 */
@Data
public class GanKemTepVbdiRequest {

    /** id văn bản đi cha (đã tồn tại). */
    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Ý kiến/ghi chú (tuỳ chọn) — vd "Đã ký số tệp X (giữ nguyên bản gốc), thay bằng Y". */
    private String opinion;
}
