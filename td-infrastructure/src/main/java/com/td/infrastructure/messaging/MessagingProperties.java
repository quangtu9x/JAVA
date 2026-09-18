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
