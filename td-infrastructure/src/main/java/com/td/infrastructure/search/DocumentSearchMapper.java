package com.td.infrastructure.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.DocumentDto;
import com.td.domain.documents.BusinessDocument;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class DocumentSearchMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };
    private static final String ATTRIBUTE_KEYS_METADATA_KEY = "__documentAttributeKeys";

    private DocumentSearchMapper() {
    }

    static DocumentSearchDocument toSearchDocument(BusinessDocument document) {
        Map<String, Object> customFields = toMap(document.getAttributesJson());
        Map<String, Object> storedMetadata = toMap(document.getMetadataJson());
        ResponseProjection projection = splitForResponse(customFields, storedMetadata);
        List<String> tags = toStringList(document.getTagsJson());

        return DocumentSearchDocument.builder()
            .id(String.valueOf(document.getId()))
            .title(document.getTitle())
            .documentType(document.getDocumentType())
            .status(document.getStatus())
            .content(document.getContent())
            .tags(tags)
            .attributes(projection.attributes())
            .metadata(projection.metadata())
            .extraFields(projection.topLevelFields())
            .searchText(buildSearchText(document, tags, projection))
            .versionNo(document.getVersionNo())
            .createdOnEpochMs(toEpochMillis(document.getCreatedOn()))
            .lastModifiedOnEpochMs(toEpochMillis(document.getLastModifiedOn()))
            .deleted(document.isDeleted())
            .build();
    }

    static DocumentDto toDto(DocumentSearchDocument searchDocument) {
        var dto = new DocumentDto();
        dto.setId(parseUuid(searchDocument.getId()));
        dto.setTitle(searchDocument.getTitle());
        dto.setDocumentType(searchDocument.getDocumentType());
        dto.setStatus(searchDocument.getStatus());
        dto.setContent(searchDocument.getContent());
        dto.setTags(searchDocument.getTags() == null ? Collections.emptyList() : searchDocument.getTags());
        dto.setAttributes(searchDocument.getAttributes() == null ? Collections.emptyMap() : searchDocument.getAttributes());
        dto.setMetadata(searchDocument.getMetadata() == null ? Collections.emptyMap() : searchDocument.getMetadata());
        dto.setExtraFields(searchDocument.getExtraFields() == null ? Collections.emptyMap() : searchDocument.getExtraFields());
        dto.setVersionNo(searchDocument.getVersionNo());
        dto.setCreatedOn(fromEpochMillis(searchDocument.getCreatedOnEpochMs()));
        dto.setLastModifiedOn(fromEpochMillis(searchDocument.getLastModifiedOnEpochMs()));
        dto.setDeleted(searchDocument.isDeleted());
        return dto;
    }

    private static String buildSearchText(
            BusinessDocument document,
            List<String> tags,
            ResponseProjection projection) {
        StringBuilder builder = new StringBuilder();
        append(builder, document.getTitle());
        append(builder, document.getContent());
        append(builder, document.getDocumentType());
        append(builder, document.getStatus());
        append(builder, tags);
        append(builder, projection.attributes());
        append(builder, projection.topLevelFields());
        append(builder, projection.metadata());
        return builder.toString().trim();
    }

    private static void append(StringBuilder builder, Object value) {
        if (value == null) {
            return;
        }

        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> append(builder, item));
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> append(builder, item));
            return;
        }

        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            builder.append(text).append(' ');
        }
    }

    private static Map<String, Object> toMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Collections.emptyMap();
            }
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private static List<String> toStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private static ResponseProjection splitForResponse(
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

    private static long toEpochMillis(LocalDateTime value) {
        if (value == null) {
            return 0L;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime fromEpochMillis(long epochMillis) {
        if (epochMillis <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (Exception ex) {
            return null;
        }
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
