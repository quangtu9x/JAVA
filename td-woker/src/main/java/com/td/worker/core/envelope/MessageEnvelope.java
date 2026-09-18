package com.td.worker.core.envelope;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Envelope CHUẨN của Worker — 1 message có thể chứa NHIỀU unit độc lập (multi-unit).
 *
 * <p>Contract trừu tượng hoá giữa broker (RabbitMQ/Kafka) và nghiệp vụ: code nghiệp vụ
 * (handler) CHỈ nhìn envelope này, không biết transport. Đúng tinh thần
 * docs/architecture/08 §8.6 của project tham khảo.</p>
 *
 * @param messageId     định danh message (idempotency cấp message)
 * @param taskType      loại task, vd {@code vbdi.delivery} — quyết định handler nào xử lý
 * @param correlationId mã truy vết xuyên hệ thống (log/metrics)
 * @param source        nơi phát sinh message (vd "td-web", "external-gateway")
 * @param createdAt     thời điểm message được tạo
 * @param headers       map header bổ sung (tùy chọn)
 * @param units         danh sách unit độc lập — MỖI UNIT CÓ THỂ THUỘC tenant/db/resource khác
 */
public record MessageEnvelope(
        String messageId,
        String taskType,
        String correlationId,
        String source,
        Instant createdAt,
        Map<String, String> headers,
        List<MessageUnit> units) {

    public static Builder builder() {
        return new Builder();
    }

    /** Builder tiện dùng (parser + unit test). */
    public static final class Builder {
        private String messageId;
        private String taskType;
        private String correlationId;
        private String source;
        private Instant createdAt = Instant.now();
        private Map<String, String> headers = Map.of();
        private List<MessageUnit> units = List.of();

        public Builder messageId(String v) {
            this.messageId = v;
            return this;
        }

        public Builder taskType(String v) {
            this.taskType = v;
            return this;
        }

        public Builder correlationId(String v) {
            this.correlationId = v;
            return this;
        }

        public Builder source(String v) {
            this.source = v;
            return this;
        }

        public Builder createdAt(Instant v) {
            this.createdAt = v;
            return this;
        }

        public Builder headers(Map<String, String> v) {
            this.headers = v;
            return this;
        }

        public Builder units(List<MessageUnit> v) {
            this.units = v;
            return this;
        }

        public MessageEnvelope build() {
            return new MessageEnvelope(messageId, taskType, correlationId, source, createdAt, headers, units);
        }
    }
}