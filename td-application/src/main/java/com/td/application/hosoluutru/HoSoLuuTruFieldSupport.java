package com.td.application.hosoluutru;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Helper tĩnh dùng chung nội bộ package {@code hosoluutru} cho các use case phiếu yêu cầu mượn/trả
 * (thao tác field dạng danh sách {@code ;}-joined trên {@code dynamic_fields}: {@code user_quyenxem}/
 * {@code user_dangkymuon}) — KHÔNG phải Spring bean (thuần hàm tĩnh, không cần DI), tránh trùng lặp
 * ~20 dòng ở 5 use case khác nhau.
 */
final class HoSoLuuTruFieldSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HoSoLuuTruFieldSupport() {
    }

    static List<String> toSemicolonList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString());
                }
            }
        } else if (value instanceof String str && !str.isBlank()) {
            for (String part : str.split(";")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }

    static void addToSemicolonField(Map<String, Object> fields, String fieldName, String value) {
        List<String> values = toSemicolonList(fields.get(fieldName));
        if (!values.contains(value)) {
            values.add(value);
        }
        fields.put(fieldName, String.join(";", values));
    }

    static void removeFromSemicolonField(Map<String, Object> fields, String fieldName, String value) {
        List<String> values = toSemicolonList(fields.get(fieldName));
        values.remove(value);
        fields.put(fieldName, String.join(";", values));
    }

    static String stringField(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? "" : value.toString();
    }

    static String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
