package com.td.infrastructure.simpki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 08/10/2026: chuyển tiếp "chuẩn bị 1 chữ ký USB có hình" sang TDSimPki — chỉ field cho phép, trả nguyên kết quả.
class PrepareExternalPdfSignatureUseCaseImplTest {

    private static final class StubClient extends TdSimPkiClient {
        Map<String, Object> lastPayload;

        StubClient() {
            super(new TdSimPkiProperties());
        }

        @Override
        public JsonNode prepareExternalSignature(Map<String, Object> payload) {
            lastPayload = payload;
            try {
                return new ObjectMapper().readTree("{\"preparedPdfBase64\":\"JVBERi0=\",\"byteRange\":[0,2,4,5],"
                    + "\"contentsOffset\":3,\"contentsLength\":1,\"hashAlgorithm\":\"SHA-256\",\"hashBase64\":\"SA==\"}");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @Test
    void forwardsAllowedFieldsAndReturnsPreparation() {
        StubClient client = new StubClient();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("pdfBase64", "JVBERi0=");
        request.put("placement", Map.of("page", 1, "x", 10, "y", 10, "width", 100, "height", 40));
        request.put("certBase64", "Q0VSVA==");
        request.put("pin", "1234");

        var result = new PrepareExternalPdfSignatureUseCaseImpl(client).execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("hashBase64", "SA==").containsEntry("contentsOffset", 3);
        assertThat(client.lastPayload).containsKeys("pdfBase64", "placement", "certBase64").doesNotContainKey("pin");
    }

    @Test
    void missingPlacementFailsWithoutCallingTdSimPki() {
        StubClient client = new StubClient();
        var result = new PrepareExternalPdfSignatureUseCaseImpl(client).execute(Map.of("pdfBase64", "JVBERi0="));
        assertThat(result.isSuccess()).isFalse();
        assertThat(client.lastPayload).isNull();
    }
}
