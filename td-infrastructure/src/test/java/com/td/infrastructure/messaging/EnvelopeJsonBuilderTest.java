package com.td.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EnvelopeJsonBuilder} PHẢI build đúng format {@code com.td.worker.core.envelope.
 * MessageEnvelope}/{@code MessageUnit} (td-woker) — test đối chiếu ĐÚNG TÊN field JSON (không phải
 * cấu trúc Java) vì đây là hợp đồng dây (wire contract) giữa 2 app độc lập.
 */
class EnvelopeJsonBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void build_shouldProduceEnvelopeMatchingWorkerContract() throws Exception {
        var props = new MessagingProperties();
        props.setSource("td-web");
        var builder = new EnvelopeJsonBuilder(objectMapper, props);
        var unit = new MessageUnitSpec("unit-1", "tenant-a", Map.of("sendLogId", "abc"), Map.of("k", "v"));
        var message = new OutboundMessage("vbdi.delivery", "corr-1", List.of(unit));

        String json = builder.build("msg-1", message);

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.get("messageId").asText()).isEqualTo("msg-1");
        assertThat(root.get("taskType").asText()).isEqualTo("vbdi.delivery");
        assertThat(root.get("correlationId").asText()).isEqualTo("corr-1");
        assertThat(root.get("source").asText()).isEqualTo("td-web");
        assertThat(root.has("createdAt")).isTrue();
        assertThat(root.get("units")).hasSize(1);
        JsonNode unitNode = root.get("units").get(0);
        assertThat(unitNode.get("unitId").asText()).isEqualTo("unit-1");
        assertThat(unitNode.get("tenantId").asText()).isEqualTo("tenant-a");
        assertThat(unitNode.get("payload").get("sendLogId").asText()).isEqualTo("abc");
        assertThat(unitNode.get("target").get("k").asText()).isEqualTo("v");
    }
}
