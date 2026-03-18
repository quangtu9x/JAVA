package com.td.application.documents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DocumentFieldLayout {

    static final String ATTRIBUTE_KEYS_METADATA_KEY = "__documentAttributeKeys";

    private DocumentFieldLayout() {
    }

    static Map<String, Object> mergeCustomFields(
            Map<String, Object> attributes,
            Map<String, Object> dynamicFields) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (attributes != null && !attributes.isEmpty()) {
            merged.putAll(attributes);
        }
        if (dynamicFields != null && !dynamicFields.isEmpty()) {
            merged.putAll(dynamicFields);
        }
        return merged;
    }

    static Map<String, Object> metadataForPersistence(
            Map<String, Object> metadata,
            Map<String, Object> attributes) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (metadata != null && !metadata.isEmpty()) {
            merged.putAll(metadata);
        }

        List<String> attributeKeys = attributes == null
            ? List.of()
            : new ArrayList<>(attributes.keySet());

        merged.put(ATTRIBUTE_KEYS_METADATA_KEY, attributeKeys);
        return merged;
    }

    static ResponseProjection splitForResponse(
            Map<String, Object> storedCustomFields,
            Map<String, Object> storedMetadata) {
        Map<String, Object> customFields = new LinkedHashMap<>();
        if (storedCustomFields != null && !storedCustomFields.isEmpty()) {
            customFields.putAll(storedCustomFields);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (storedMetadata != null && !storedMetadata.isEmpty()) {
            metadata.putAll(storedMetadata);
        }

        boolean hasAttributeMarker = metadata.containsKey(ATTRIBUTE_KEYS_METADATA_KEY);
        List<String> attributeKeys = toStringList(metadata.remove(ATTRIBUTE_KEYS_METADATA_KEY));

        Map<String, Object> attributes = new LinkedHashMap<>();
        Map<String, Object> topLevelFields = new LinkedHashMap<>();

        if (!hasAttributeMarker) {
            attributes.putAll(customFields);
            return new ResponseProjection(attributes, topLevelFields, metadata);
        }

        customFields.forEach((key, value) -> {
            if (attributeKeys.contains(key)) {
                attributes.put(key, value);
            } else {
                topLevelFields.put(key, value);
            }
        });

        return new ResponseProjection(attributes, topLevelFields, metadata);
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    record ResponseProjection(
            Map<String, Object> attributes,
            Map<String, Object> topLevelFields,
            Map<String, Object> metadata) {
    }
}