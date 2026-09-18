package com.td.infrastructure.onlyoffice;

import java.util.UUID;

/** Claims đã verify của 1 token nội bộ phạm vi hẹp — xem {@link OnlyOfficeAccessTokenService}. */
public record OnlyOfficeAccessClaims(
        String purpose,
        String tenantId,
        UUID documentId,
        UUID fileId,
        String username) {
}
