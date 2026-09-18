package com.td.application.common.dynamicfields;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Chuyển giá trị string thô (form-data) sang Object theo {@link FieldType}, tương đương
 * {@code ProcessDocument()} phía Domino ghi giá trị vào NotesDocument theo kiểu field.
 */
public final class DynamicFieldValueConverter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DynamicFieldValueConverter() {
    }

    public static Object convert(FieldType type, String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (type) {
            case NUMBER -> parseNumber(rawValue);
            case JSON -> parseJson(rawValue);
            case TEXT_LIST -> parseTextList(rawValue);
            case DATETIME, TEXT -> rawValue;
        };
    }

    private static Object parseNumber(String rawValue) {
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException ex) {
            return rawValue;
        }
    }

    private static Object parseJson(String rawValue) {
        try {
            return OBJECT_MAPPER.readValue(rawValue, Object.class);
        } catch (Exception ex) {
            return rawValue;
        }
    }

    private static List<String> parseTextList(String rawValue) {
        List<String> values = new ArrayList<>();
        for (String part : rawValue.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
