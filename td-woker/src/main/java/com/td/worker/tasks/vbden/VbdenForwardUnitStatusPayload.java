package com.td.worker.tasks.vbden;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Payload của envelope {@code vbden.forward_unit.status} — PHẢI khớp field-by-field
 * {@code com.td.application.vbden.VbdenForwardUnitStatusMessagePayload} (module {@code td-application},
 * đọc trực tiếp mã nguồn để lấy đúng tên field — 2 module độc lập, KHÔNG share class/jar). Xem javadoc
 * {@link VbdenForwardUnitStatusTaskHandler}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VbdenForwardUnitStatusPayload {

    private String originDocumentId;

    private String action;
    private String actionLabel;

    private String reportingIdentifier;
    private String reportingName;

    private String actingUsername;
    private String opinion;

    private String newAssigneesLabel;
}
