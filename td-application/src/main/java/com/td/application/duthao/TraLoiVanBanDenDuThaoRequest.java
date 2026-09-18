package com.td.application.duthao;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 16/09/2026 (yêu cầu người dùng: modal "Trả lời cho văn bản đến" ở chi tiết dự thảo — chưa cutover
 * FE, vẫn gọi Domino {@code qlvb/add?id={draftId}&databasetype=duthao} + {@code qlvb/vbden/
 * CapNhatVanBanDen?action=ganduthaotraloi}): tương đương {@code DuThaoActionModal.jsx
 * #btnActionTraLoiVanBanDen} — cùng khuôn request với
 * {@link com.td.application.vbdi.TraLoiVanBanDenRequest} (đã port cho văn bản đi, xem javadoc lớp
 * đó về cấu trúc {@link #documentIn}), chỉ khác văn bản CHA là dự thảo ({@link #draftId}) thay vì
 * văn bản đi.
 */
@Data
public class TraLoiVanBanDenDuThaoRequest {

    @NotNull(message = "draftId khong duoc de trong")
    private UUID draftId;

    @NotEmpty(message = "Chua chon van ban den can tra loi")
    private List<Map<String, Object>> documentIn;

    /** Tên hiển thị người thực hiện — tuỳ chọn, display-only. */
    private String creatorName;

    /** Chức vụ hiển thị — tuỳ chọn, đi kèm {@link #creatorName}. */
    private String position;
}
