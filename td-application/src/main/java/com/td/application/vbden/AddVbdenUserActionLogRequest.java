package com.td.application.vbden;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 18/09/2026 — request cho {@code POST /api/v1/vbden/LogUserAction}: ghi 1 dòng "Lịch sử thao tác
 * văn bản" của văn bản đến — mirror {@code com.td.application.vbdi.AddVbdiUserActionLogRequest}.
 */
@Data
public class AddVbdenUserActionLogRequest {

    @NotNull(message = "documentId khong duoc de trong")
    private UUID documentId;

    @NotBlank(message = "actionType khong duoc de trong")
    private String actionType;

    /** Field trình bày — chấp nhận từ client, xem javadoc {@link com.td.domain.vbden.VbdenUserActionLog}. */
    private String fullname;
    private String unit;
    private String identifier;
}
