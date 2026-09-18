package com.td.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Helper DÙNG CHUNG bởi {@link RabbitMessagePublisher}/{@link KafkaMessagePublisher} — tránh lặp
 * code build envelope JSON giữa 2 adapter (yêu cầu người dùng 04/09/2026: "viết class chung...
 * tránh trùng lặp code").
 *
 * <p>Format PHẢI khớp {@code com.td.worker.core.envelope.MessageEnvelope}/{@code MessageUnit}
 * ({@code td-woker}, đọc trực tiếp mã nguồn — KHÔNG suy đoán từ tài liệu thiết kế cũ):
 * {@code {messageId, taskType, correlationId, source, createdAt(ISO-8601), headers{}, units[
 * {unitId, tenantId, payload, target}]}}.</p>
 */
@Component
@RequiredArgsConstructor
class EnvelopeJsonBuilder {

    private final ObjectMapper objectMapper;
    private final MessagingProperties props;

    String build(String messageId, OutboundMessage message) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("messageId", messageId);
        root.put("taskType", message.taskType());
        root.put("correlationId", message.correlationId() == null ? "" : message.correlationId());
        root.put("source", props.getSource());
        root.put("createdAt", Instant.now().toString());
        root.set("headers", objectMapper.createObjectNode());
        root.set("units", buildUnits(message.units()));
        return root.toString();
    }

    private ArrayNode buildUnits(List<MessageUnitSpec> units) {
        ArrayNode array = objectMapper.createArrayNode();
        for (MessageUnitSpec unit : units) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("unitId", unit.unitId());
            node.put("tenantId", unit.tenantId());
            node.set("payload", objectMapper.valueToTree(unit.payload()));
            node.set("target", objectMapper.valueToTree(unit.target() == null ? java.util.Map.of() : unit.target()));
            array.add(node);
        }
        return array;
    }
}
