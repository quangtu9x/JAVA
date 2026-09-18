package com.td.application.vbdi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 30/08/2026 — request cho {@code POST /api/v1/vbdi/LogUserAction}: ghi 1 dòng "Lịch sử thao tác
 * văn bản" — port Domino {@code action=loguseraction} ({@code LogUserAction.java#setLogUserAction}).
 * Tương thích Ý NGHĨA (không byte-for-byte) với payload Domino cũ ({@code ControllModal.jsx#logUserAction}
 * gửi kèm {@code Form/timestamp/ip_address/browser_info} — 3 field đó Domino gốc CŨNG không đọc/lưu,
 * xem javadoc {@link AddVbdiUserActionLogUseCase}, nên không cần khai báo lại ở đây).
 */
@Data
public class AddVbdiUserActionLogRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    @NotBlank(message = "actionType khong duoc de trong")
    private String actionType;

    /** Field trình bày — chấp nhận từ client, xem javadoc {@link com.td.domain.vbdi.VbdiUserActionLog}. */
    private String fullname;
    private String unit;
    private String identifier;
}
