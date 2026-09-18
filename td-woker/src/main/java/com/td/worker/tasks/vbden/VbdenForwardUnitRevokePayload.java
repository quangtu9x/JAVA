package com.td.worker.tasks.vbden;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Payload của envelope {@code vbden.forward_unit.revoke} — PHẢI khớp field-by-field
 * {@code com.td.application.vbden.VbdenForwardUnitRevokeMessagePayload} (module {@code td-application},
 * đọc trực tiếp mã nguồn để lấy đúng tên field — 2 module độc lập, KHÔNG share class/jar). Xem javadoc
 * {@link VbdenForwardUnitRevokeTaskHandler}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VbdenForwardUnitRevokePayload {

    private String targetDocumentId;
    private String originDocumentId;
    private String actingUsername;
    private String opinion;
    private String reason;
}
