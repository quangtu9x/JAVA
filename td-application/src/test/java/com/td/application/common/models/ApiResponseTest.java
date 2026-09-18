package com.td.application.common.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Xác nhận ApiResponse serialize đúng format TDMessage cũ:
 * {status, code, message, data, properties, total} — không thừa, không thiếu field.
 */
class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void success_shouldSerializeExactTdMessageFields() throws Exception {
        ApiResponse<String> response = ApiResponse.success("hello");

        ObjectNode json = (ObjectNode) objectMapper.valueToTree(response);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
            "status", "code", "message", "data", "properties", "total");
        assertThat(json.get("status").asBoolean()).isTrue();
        assertThat(json.get("code").asInt()).isEqualTo(200);
        assertThat(json.get("message").asText()).isEmpty();
        assertThat(json.get("data").asText()).isEqualTo("hello");
        assertThat(json.get("properties").isNull()).isTrue();
        assertThat(json.get("total").isNull()).isTrue();
    }

    @Test
    void successWithTotal_shouldSetTotalField() {
        ApiResponse<String> response = ApiResponse.success("hello", 42);

        assertThat(response.isStatus()).isTrue();
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getTotal()).isEqualTo(42);
    }

    @Test
    void successWithProperties_shouldCarryArbitraryMetadata() {
        Map<String, Object> properties = Map.of("cache", true, "cacheKey", "abc");

        ApiResponse<String> response = ApiResponse.success("hello", properties);

        assertThat(response.getProperties()).containsEntry("cache", true).containsEntry("cacheKey", "abc");
    }

    @Test
    void created_shouldUseHttp201Code() {
        ApiResponse<String> response = ApiResponse.created("new-id");

        assertThat(response.isStatus()).isTrue();
        assertThat(response.getCode()).isEqualTo(201);
        assertThat(response.getData()).isEqualTo("new-id");
    }

    @Test
    void error_shouldSetStatusFalseAndDefaultCode400() {
        ApiResponse<Object> response = ApiResponse.error("Du lieu khong hop le");

        assertThat(response.isStatus()).isFalse();
        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("Du lieu khong hop le");
        assertThat(response.getData()).isNull();
    }

    @Test
    void error_withExplicitCode_shouldOverrideDefault() {
        ApiResponse<Object> response = ApiResponse.error(500, "Co loi xay ra");

        assertThat(response.isStatus()).isFalse();
        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).isEqualTo("Co loi xay ra");
    }

    private Set<String> fieldNames(ObjectNode json) {
        Set<String> names = new java.util.HashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
