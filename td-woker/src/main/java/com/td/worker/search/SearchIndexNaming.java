package com.td.worker.search;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tên index Elasticsearch: alias {@code {prefix}-{tenant}-{entity}} (đọc/ghi luôn qua alias), index vật
 * lý {@code {alias}-v{mappingVersion}}. ES chỉ nhận chữ thường, không dấu cách và một số ký tự đặc biệt —
 * chuẩn hoá về [a-z0-9_-].
 */
public final class SearchIndexNaming {

    private static final Pattern INVALID = Pattern.compile("[^a-z0-9_-]+");
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private SearchIndexNaming() {
    }

    public static String alias(String prefix, String tenantId, String entityType) {
        return normalize(prefix) + "-" + normalize(tenantId) + "-" + normalize(entityType);
    }

    public static String physical(String alias, int mappingVersion) {
        return alias + "-v" + mappingVersion;
    }

    static String normalize(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String cleaned = INVALID.matcher(lower).replaceAll("_");
        // ES cấm tên bắt đầu bằng "-", "_" hoặc "+".
        while (cleaned.startsWith("-") || cleaned.startsWith("_")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Tên không hợp lệ cho index Elasticsearch: '" + value + "'");
        }
        return cleaned;
    }

    /** Tên bảng/cột ghép vào SQL — chỉ chấp nhận định danh đơn giản (chặn SQL injection qua cấu hình). */
    public static String requireSqlIdentifier(String value) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Tên bảng không hợp lệ: '" + value + "'");
        }
        return value;
    }
}
