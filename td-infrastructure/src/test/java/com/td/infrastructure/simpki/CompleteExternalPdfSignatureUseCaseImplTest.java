package com.td.infrastructure.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

// 07/10/2026: hoàn tất chữ ký USB Token qua td-web -> TDSimPki (thay viewer gọi thẳng, bị HTTP 500).
// Stub tay thay Mockito: máy build hiện chặn Mockito self-attach (inline MockMaker) — test không phụ thuộc.
class CompleteExternalPdfSignatureUseCaseImplTest {

    private static final class StubClient extends TdSimPkiClient {
        private final Function<Map<String, Object>, JsonNode> behavior;
        Map<String, Object> lastPayload;
        int calls;

        StubClient(Function<Map<String, Object>, JsonNode> behavior) {
            super(new TdSimPkiProperties());
            this.behavior = behavior;
        }

        @Override
        public JsonNode completeExternalSignature(Map<String, Object> payload) {
            calls++;
            lastPayload = payload;
            return behavior.apply(payload);
        }
    }

    @Test
    void execute_forwardsOnlyAllowedFields_andMapsResult() throws Exception {
        JsonNode response = new ObjectMapper().readTree("{\"fileBase64\":\"JVBERi0=\",\"fileName\":\"a-signed.pdf\"}");
        StubClient client = new StubClient(payload -> response);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("preparedPdfBase64", "JVBERi0=");
        request.put("byteRange", List.of(0, 2, 4, 5));
        request.put("signatureValueBase64", "U0lH");
        request.put("certBase64", "Q0VSVA==");
        request.put("pin", "123456");

        var result = new CompleteExternalPdfSignatureUseCaseImpl(client).execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().fileBase64()).isEqualTo("JVBERi0=");
        assertThat(result.getData().fileName()).isEqualTo("a-signed.pdf");
        assertThat(client.lastPayload)
            .containsKeys("preparedPdfBase64", "byteRange", "signatureValueBase64", "certBase64")
            .doesNotContainKey("pin");
    }

    @Test
    void execute_keepsTdSimPkiRejectionMessage() {
        StubClient client = new StubClient(payload -> {
            throw new TdSimPkiException("Chữ ký Token không khớp ByteRange/chứng thư đã gửi");
        });

        var result = new CompleteExternalPdfSignatureUseCaseImpl(client).execute(Map.of("preparedPdfBase64", "JVBERi0="));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("không khớp ByteRange");
    }

    @Test
    void execute_withoutPdf_failsWithoutCallingTdSimPki() {
        StubClient client = new StubClient(payload -> null);

        var result = new CompleteExternalPdfSignatureUseCaseImpl(client).execute(Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(client.calls).isZero();
    }
}
