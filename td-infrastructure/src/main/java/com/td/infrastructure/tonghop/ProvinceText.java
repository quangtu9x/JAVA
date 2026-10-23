package com.td.infrastructure.tonghop;

import com.fasterxml.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Tiện ích đọc field động + chuẩn hoá chuỗi tìm kiếm cho kho tổng hợp toàn tỉnh. */
final class ProvinceText {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private ProvinceText() {
    }

    /**
     * Giá trị chữ của 1 field động: mảng → phần tử khác rỗng đầu tiên; số → bỏ ".0" (bẫy
     * {@code document_number} bị lưu thành Double); rỗng/"[]" → null.
     */
    static String text(JsonNode fields, String key) {
        if (fields == null) {
            return null;
        }
        return text(fields.get(key));
    }

    static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                String value = text(element);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        if (node.isObject()) {
            return null;
        }
        if (node.isNumber()) {
            return node.isIntegralNumber() || node.asDouble() != Math.rint(node.asDouble())
                ? node.asText()
                : String.valueOf((long) node.asDouble());
        }
        String value = node.asText().trim();
        if (value.isEmpty() || "[]".equals(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            // Mảng bị lưu dạng chuỗi JSON: lấy phần tử đầu.
            String inner = value.substring(1, value.length() - 1).trim();
            int comma = inner.indexOf(',');
            String first = (comma >= 0 ? inner.substring(0, comma) : inner).replace("\"", "").trim();
            return first.isEmpty() ? null : first;
        }
        return value;
    }

    static boolean isFlag(JsonNode fields, String key) {
        String value = text(fields, key);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    /** Chữ thường, bỏ dấu tiếng Việt (đ → d), gộp khoảng trắng. */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String stripped = DIACRITICS.matcher(Normalizer.normalize(lowered, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(stripped).replaceAll(" ").trim();
    }

    static String joinNormalized(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(part);
            }
        }
        return normalize(sb.toString());
    }

    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
