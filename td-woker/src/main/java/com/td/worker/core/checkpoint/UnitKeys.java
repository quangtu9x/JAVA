package com.td.worker.core.checkpoint;

import java.util.Locale;

/** Utility tạo unique key idempotency persistent: tenantId|messageId|unitId. */
public final class UnitKeys {

    private UnitKeys() {
    }

    /** Khoá duy nhất cho 1 unit trong 1 message của 1 tenant. */
    public static String of(String tenantId, String messageId, String unitId) {
        return tenantId + "|" + messageId + "|" + unitId;
    }

    /** Normalize status string thành enum (bỏ qua case). */
    public static UnitStatus parseStatus(String raw) {
        return UnitStatus.valueOf(raw.toUpperCase(Locale.ROOT));
    }
}