package com.td.infrastructure.messaging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình messaging GENERIC của app chính (yêu cầu người dùng 04/09/2026: "đồng nhất cấu hình...
 * tránh ràng buộc, sau này cài đặt độc lập"). Prefix {@code app.messaging} — CỐ Ý KHÁC
 * {@code worker.*} (prefix của {@code td-woker}) dù VALUE mặc định của {@link #exchange}/
 * {@link #routingPrefix}/{@link #kafkaTopic} khớp ĐÚNG quy ước {@code worker.rabbit.exchange}/
 * {@code worker.<taskType>}/{@code worker.kafka.topics[].name} bên đó — 2 app đồng bộ qua QUY ƯỚC
 * WIRE FORMAT (JSON envelope + tên exchange/topic), KHÔNG qua code/jar dùng chung (xem
 * {@code td-woker/docs/01-architecture.md}: "không có dependency nào khiến Worker phải chạy cùng
 * td-web").
 */
@Data
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

    /** {@code rabbit} hoặc {@code kafka} — chỉ 1 {@code MessagePublisher} bean active theo giá trị này. */
    private String broker = "rabbit";

    /** Exchange topic RabbitMQ — PHẢI khớp {@code worker.rabbit.exchange} phía td-woker. */
    private String exchange = "worker.exchange";

    /**
     * Routing key = routingPrefix + taskType (dấu chấm trong taskType tự đổi thành {@code _} —
     * vd {@code "vbdi.delivery"} → {@code worker.vbdi_delivery}, xem bugfix 05/09/2026 lần 2 ở
     * {@link RabbitMessagePublisher}: {@code td-woker} bind queue mặc định pattern {@code worker.*}
     * — topic exchange, {@code *} chỉ khớp ĐÚNG 1 từ, taskType có dấu chấm sẽ phá vỡ match nếu giữ
     * nguyên).
     */
    private String routingPrefix = "worker.";

    /** Topic Kafka — PHẢI khớp 1 topic trong {@code worker.kafka.topics[]} phía td-woker. */
    private String kafkaTopic = "worker.tasks";

    /** Envelope {@code source} — nhận diện app phát sinh message trong log/metrics phía Worker. */
    private String source = "td-web";

    /**
     * Task thuộc dịch vụ RIÊNG (TD Gateway — {@code tdgateway}) GIỮ dấu "." trong routing key: queue gateway bind
     * {@code worker.guinhanquocgia.send}; dạng thay "_" ({@code worker.guinhanquocgia_send}) lại khớp queue chung
     * {@code worker.*} của td-woker (không có handler → treo WAITING_DEPENDENCY), gateway không bao giờ nhận được.
     */
    private java.util.List<String> dottedRoutingTaskPrefixes = new java.util.ArrayList<>(java.util.List.of("guinhanquocgia."));

    /** Topic Kafka riêng theo taskType (mặc định mọi task vào {@link #kafkaTopic}) — khớp topic TD Gateway. */
    private java.util.Map<String, String> kafkaTopicOverrides = new java.util.LinkedHashMap<>(java.util.Map.of(
        "guinhanquocgia.send", "tdgateway.guinhanquocgia.send",
        "guinhanquocgia.status.send", "tdgateway.guinhanquocgia.status.send"));

    /** Routing key RabbitMQ cho 1 taskType theo quy ước trên. */
    public String routingKeyFor(String taskType) {
        boolean dotted = dottedRoutingTaskPrefixes != null
            && dottedRoutingTaskPrefixes.stream().anyMatch(p -> p != null && !p.isBlank() && taskType.startsWith(p));
        return routingPrefix + (dotted ? taskType : taskType.replace('.', '_'));
    }

    public String kafkaTopicFor(String taskType) {
        String override = kafkaTopicOverrides == null ? null : kafkaTopicOverrides.get(taskType);
        return override == null || override.isBlank() ? kafkaTopic : override;
    }

    private Outbox outbox = new Outbox();

    @Data
    public static class Outbox {
        /** Chu kỳ scheduler đọc PENDING (ms). */
        private long pollIntervalMs = 5000;
        private int batchSize = 50;
        private int maxAttempts = 8;
        private long retryInitialBackoffMs = 5000;
        private long retryMaxBackoffMs = 300000;
    }
}
