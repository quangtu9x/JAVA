package com.td.application.documents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

final class DocumentJsonMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    private DocumentJsonMapper() {
    }

    static String toJsonObject(Map<String, Object> value) {
        try {
            if (value == null || value.isEmpty()) {
                return "{}";
            }
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    static String toJsonArray(List<String> value) {
        try {
            if (value == null || value.isEmpty()) {
                return "[]";
            }
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    static Map<String, Object> toMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Collections.emptyMap();
            }
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    static List<String> toStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
