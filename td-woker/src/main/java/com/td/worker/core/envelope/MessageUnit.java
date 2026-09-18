package com.td.worker.core.envelope;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

/**
 * Một unit (đơn vị xử lý độc lập) bên trong message. Mỗi unit có thể thuộc 1 tenant —
 * database/đơn vị/resource khác nhau (database-per-tenant).
 *
 * @param unitId        định danh unit trong message
 * @param tenantId      tenant đích (quyết định database/vùng dữ liệu xử lý)
 * @param payload       nội dung nghiệp vụ (JSON, do handler tự deserialize)
 * @param target        metadata định tuyến tùy chọn (vd routingKey, databaseName,...)
 * @param maxAttempts   override số retry tối đa cho unit (null = dùng cấu hình mặc định)
 * @param deadline      deadline cứng của unit (null = dùng unitTimeout từ cấu hình)
 */
public record MessageUnit(
        String unitId,
        String tenantId,
        JsonNode payload,
        Map<String, String> target,
        Integer maxAttempts,
        Instant deadline) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String unitId;
        private String tenantId;
        private JsonNode payload;
        private Map<String, String> target = Map.of();
        private Integer maxAttempts;
        private Instant deadline;

        public Builder unitId(String v) {
            this.unitId = v;
            return this;
        }

        public Builder tenantId(String v) {
            this.tenantId = v;
            return this;
        }

        public Builder payload(JsonNode v) {
            this.payload = v;
            return this;
        }

        public Builder target(Map<String, String> v) {
            this.target = v;
            return this;
        }

        public Builder maxAttempts(Integer v) {
            this.maxAttempts = v;
            return this;
        }

        public Builder deadline(Instant v) {
            this.deadline = v;
            return this;
        }

        public MessageUnit build() {
            return new MessageUnit(unitId, tenantId, payload, target, maxAttempts, deadline);
        }
    }

    /** Trả về bản sao với maxAttempts được gán (dùng normalization trước khi vào store). */
    public MessageUnit withMaxAttempts(int value) {
        return new MessageUnit(unitId, tenantId, payload, target, value, deadline);
    }
}