package com.td.application.documents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.dynamicfields.DynamicFieldValueConverter;
import com.td.application.common.dynamicfields.FieldType;
import com.td.application.common.dynamicfields.FieldTypeInferrer;
import com.td.application.common.dynamicfields.ItemsFilter;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * BL-101 (Dynamic Data Engine): xây dựng/đọc cột {@code documents.dynamic_fields} (JSONB),
 * tương đương {@code ShowItems()}/{@code CreateOrUpdateDocument()} phía Domino.
 */
@Service
public class DynamicDocumentFieldsService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    public String buildDynamicFieldsJson(
            Map<String, String> rawFields, String dateFields, String numberFields, String richtextFields) {
        Set<String> dateFieldSet = ItemsFilter.splitSemicolonList(dateFields);
        Set<String> numberFieldSet = ItemsFilter.splitSemicolonList(numberFields);
        Set<String> richTextFieldSet = ItemsFilter.splitSemicolonList(richtextFields);

        Map<String, Object> dynamicFields = new LinkedHashMap<>();
        if (rawFields != null) {
            for (Map.Entry<String, String> entry : rawFields.entrySet()) {
                String fieldName = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase();
                if (fieldName.isEmpty()) {
                    continue;
                }
                FieldType type = FieldTypeInferrer.infer(fieldName, dateFieldSet, numberFieldSet, richTextFieldSet);
                dynamicFields.put(fieldName, DynamicFieldValueConverter.convert(type, entry.getValue()));
            }
        }
        return toJson(dynamicFields);
    }

    public Map<String, Object> parseDynamicFields(String dynamicFieldsJson) {
        return toMap(dynamicFieldsJson);
    }

    public Map<String, Object> filterItems(Map<String, Object> allFields, String items) {
        return ItemsFilter.filter(allFields, items);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> toMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Collections.emptyMap();
            }
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }
}
