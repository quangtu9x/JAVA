package com.td.infrastructure.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 08/10/2026 — stub tay (không Mockito, máy build có lúc lỗi self-attach). */
class LocateTextSlotsUseCaseImplTest {

    private static final class StubClient extends TdSimPkiClient {
        JsonNode lastSlots;
        JsonNode response;
        TdSimPkiException error;

        StubClient() {
            super(new TdSimPkiProperties());
        }

        @Override
        public JsonNode locateTextSlots(byte[] pdfBytes, String fileName, JsonNode slots) {
            lastSlots = slots;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void forwardsSlotsAndReturnsTdSimPkiBody() throws Exception {
        StubClient client = new StubClient();
        client.response = mapper.readTree("{\"pages\":[{\"page\":1,\"width\":595,\"height\":842}],"
            + "\"slots\":[{\"id\":\"so\",\"found\":true,\"page\":1,\"x\":120,\"y\":690}]}");
        var useCase = new LocateTextSlotsUseCaseImpl(client);

        Result<Map<String, Object>> result = useCase.execute(new byte[] {1}, "a.pdf",
            "[{\"id\":\"so\",\"regex\":\"Số\\\\s*:( ?)/\"}]");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsKeys("pages", "slots");
        assertThat(client.lastSlots.get(0).path("regex").asText()).isEqualTo("Số\\s*:( ?)/");
    }

    @Test
    void rejectsMissingOrInvalidInputWithoutCallingTdSimPki() {
        StubClient client = new StubClient();
        var useCase = new LocateTextSlotsUseCaseImpl(client);

        assertThat(useCase.execute(new byte[0], "a.pdf", "[{\"id\":\"x\",\"regex\":\"a( )b\"}]").isSuccess()).isFalse();
        assertThat(useCase.execute(new byte[] {1}, "a.pdf", "khong-phai-json").isSuccess()).isFalse();
        assertThat(useCase.execute(new byte[] {1}, "a.pdf", "[]").isSuccess()).isFalse();
        assertThat(client.lastSlots).isNull();
    }

    @Test
    void mapsTdSimPkiErrorToFailure() {
        StubClient client = new StubClient();
        client.error = new TdSimPkiException("TDSimPki loi");
        var useCase = new LocateTextSlotsUseCaseImpl(client);

        Result<Map<String, Object>> result = useCase.execute(new byte[] {1}, "a.pdf", "[{\"id\":\"x\",\"regex\":\"a( )b\"}]");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("TDSimPki loi");
    }
}
