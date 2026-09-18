package com.td.application.vbden;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Yêu cầu người dùng 15/09/2026 ("ký số lưu vết tương tự như BTE, dùng chung cho toàn bộ các module
 * khác sử dụng ký số"): request cho {@code POST /api/v1/vbden/GanKemTep} — cùng shape
 * {@link com.td.application.duthao.GanKemTepDuThaoRequest} (duthao), phạm vi vbden. Ghi 1 vết
 * {@code vbden_res} (KHÔNG đổi trạng thái văn bản đến cha — xem javadoc
 * {@link GanKemTepVbdenUseCase}) mang ý kiến, hiện ở tab "Quá trình xử lý" của văn bản đến.
 */
@Data
public class GanKemTepVbdenRequest {

    /** id văn bản đến cha (đã tồn tại). */
    @NotNull(message = "id khong duoc de trong")
    private UUID id;

    /** Ý kiến/ghi chú (tuỳ chọn) — vd "Đã ký số tệp X (giữ nguyên bản gốc), thay bằng Y". */
    private String opinion;
}
