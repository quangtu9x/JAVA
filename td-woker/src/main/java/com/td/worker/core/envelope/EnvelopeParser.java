package com.td.worker.core.envelope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parse và validate JSON body thành {@link MessageEnvelope}.
 *
 * <p>Đây là biên giới DUY NHẤT giữa transport (Rabbit/Kafka) và Worker Core — mọi broker
 * đưa body về đây trước khi gọi {@code MessageFlowRunner}.</p>
 */
@Component
@RequiredArgsConstructor
public class EnvelopeParser {

    private final ObjectMapper objectMapper;

    /** @param rawBody JSON string từ broker. */
    public MessageEnvelope parse(String rawBody) throws EnvelopeValidationException {
        if (rawBody == null || rawBody.isBlank()) {
            throw new EnvelopeValidationException("Message body rỗng");
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new EnvelopeValidationException("Message body không phải JSON hợp lệ: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new EnvelopeValidationException("Message body phải là 1 JSON object");
        }

        String messageId = text(root, "messageId");
        String taskType = text(root, "taskType");
        String correlationId = textOrDefault(root, "correlationId", "");
        String source = textOrDefault(root, "source", "unknown");
        Instant createdAt = instant(root, "createdAt");
        Map<String, String> headers = stringMap(root.get("headers"));

        if (messageId == null) {
            throw new EnvelopeValidationException("Thiếu trường bắt buộc 'messageId'");
        }
        if (taskType == null) {
            throw new EnvelopeValidationException("Thiếu trường bắt buộc 'taskType'");
        }

        JsonNode unitsNode = root.get("units");
        if (unitsNode == null || !unitsNode.isArray() || unitsNode.isEmpty()) {
            throw new EnvelopeValidationException("Envelope phải có ít nhất 1 unit (mảng 'units' không được rỗng)");
        }

        List<MessageUnit> units = new ArrayList<>();
        Set<String> seenUnitIds = new HashSet<>();
        for (JsonNode unitNode : unitsNode) {
            if (unitNode == null || !unitNode.isObject()) {
                throw new EnvelopeValidationException("Mỗi phần tử trong 'units' phải là 1 object");
            }
            String unitId = text(unitNode, "unitId");
            String tenantId = text(unitNode, "tenantId");
            if (unitId == null) {
                throw new EnvelopeValidationException("Thiếu trường bắt buộc 'units[].unitId'");
            }
            if (tenantId == null) {
                throw new EnvelopeValidationException("Thiếu trường bắt buộc 'units[].tenantId' (tenant đích)");
            }
            if (!seenUnitIds.add(unitId)) {
                throw new EnvelopeValidationException("Trùng 'unitId' trong cùng 1 message: " + unitId);
            }
            Integer maxAttempts = intOrNull(unitNode, "maxAttempts");
            units.add(new MessageUnit(
                unitId,
                tenantId,
                unitNode.get("payload"),
                stringMap(unitNode.get("target")),
                maxAttempts,
                instant(unitNode, "deadline")));
        }

        return new MessageEnvelope(messageId, taskType, correlationId, source, createdAt, headers, List.copyOf(units));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText("");
        return s.isBlank() ? null : s.trim();
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        String s = text(node, field);
        return s == null ? defaultValue : s;
    }

    private static Instant instant(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText("").isBlank()) {
            return null;
        }
        try {
            return Instant.parse(v.asText());
        } catch (Exception e) {
            throw new EnvelopeValidationException("Giá trị '" + field + "' không phải ISO-8601: " + v.asText());
        }
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asInt(0) > 0 ? v.asInt(0) : null;
    }

    private static Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            map.put(e.getKey(), e.getValue() == null ? null : e.getValue().asText());
        }
        return map;
    }
}