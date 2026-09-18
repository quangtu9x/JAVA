package com.td.worker.core.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeParserTest {

    private final EnvelopeParser parser = new EnvelopeParser(new ObjectMapper().findAndRegisterModules());

    @Test
    void parsesValidMessageWithMultipleUnits() throws Exception {
        String body = """
            {
              "messageId": "msg-1",
              "taskType": "vbdi.delivery",
              "correlationId": "corr-1",
              "source": "td-web",
              "units": [
                {
                  "unitId": "u1",
                  "tenantId": "H05",
                  "payload": {"sendLogId": "sl-1"},
                  "target": {"routingKey": "vbdi.H05.delivered"}
                },
                {
                  "unitId": "u2",
                  "tenantId": "H06",
                  "payload": {"sendLogId": "sl-2"}
                }
              ]
            }
            """;
        MessageEnvelope envelope = parser.parse(body);
        assertThat(envelope.messageId()).isEqualTo("msg-1");
        assertThat(envelope.taskType()).isEqualTo("vbdi.delivery");
        assertThat(envelope.units()).hasSize(2);
        assertThat(envelope.units().get(0).tenantId()).isEqualTo("H05");
        assertThat(envelope.units().get(1).payload().path("sendLogId").asText()).isEqualTo("sl-2");
    }

    @Test
    void rejectsMissingMessageId() {
        assertThatThrownBy(() -> parser.parse(
            "{\"taskType\":\"x\",\"units\":[{\"unitId\":\"u1\",\"tenantId\":\"t1\"}]}"))
            .isInstanceOf(EnvelopeValidationException.class)
            .hasMessageContaining("messageId");
    }

    @Test
    void rejectsMissingTenantOnUnit() {
        assertThatThrownBy(() -> parser.parse(
            "{\"messageId\":\"m1\",\"taskType\":\"x\",\"units\":[{\"unitId\":\"u1\"}]}"))
            .isInstanceOf(EnvelopeValidationException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void rejectsDuplicateUnitId() {
        assertThatThrownBy(() -> parser.parse("""
            {"messageId":"m1","taskType":"x","units":[
              {"unitId":"u","tenantId":"t1"},
              {"unitId":"u","tenantId":"t1"}
            ]}
            """))
            .isInstanceOf(EnvelopeValidationException.class)
            .hasMessageContaining("unitId");
    }

    @Test
    void rejectsEmptyUnits() {
        assertThatThrownBy(() -> parser.parse(
            "{\"messageId\":\"m1\",\"taskType\":\"x\",\"units\":[]}"))
            .isInstanceOf(EnvelopeValidationException.class)
            .hasMessageContaining("unit");
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> parser.parse("not-json"))
            .isInstanceOf(EnvelopeValidationException.class);
    }
}