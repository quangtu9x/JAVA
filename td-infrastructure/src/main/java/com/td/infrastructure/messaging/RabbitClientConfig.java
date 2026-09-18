package com.td.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client RabbitMQ CHỈ để PUBLISH (app chính không consume gì — đó là việc của {@code td-woker}).
 * Kết nối qua {@code spring.rabbitmq.*} (autoconfig Spring Boot) — chỉ khởi tạo khi
 * {@code app.messaging.broker=rabbit} (mặc định), tránh ép app phải có RabbitMQ sẵn sàng khi chọn
 * Kafka.
 *
 * <p>Publisher-confirms/mandatory bật theo đúng khuyến nghị
 * {@code docs/architecture/08-thay-the-domino-router-voi-rabbitmq-minio-postgresql.md §11.3}
 * ("BẮT BUỘC bật để outbox poller biết chắc broker đã nhận") — {@code spring.rabbitmq.publisher-
 * confirm-type=correlated`/`publisher-returns=true} cấu hình ở {@code application.yml}.</p>
 *
 * <p><b>Bugfix 04/09/2026 (lần 1 — app KHÔNG khởi động được khi broker chưa sẵn sàng)</b>: bản đầu
 * tự gọi {@code RabbitAdmin.declareExchange(...)} NGAY lúc bean được tạo (trong lúc Spring context
 * đang refresh) — cần 1 kết nối THẬT tới broker NGAY LÚC ĐÓ, có thể treo/ném lỗi làm cả app không
 * lên được. Đã bỏ bước tự khai báo thủ công này.</p>
 *
 * <p><b>Bugfix 04/09/2026 (lần 2 — publish lỗi liên tục "404 NOT_FOUND - no exchange")</b>: bỏ HẲN
 * việc khai báo (như lần 1) khiến exchange KHÔNG BAO GIỜ được tạo nếu {@code td-woker} không chạy.
 * Sửa: khai báo {@code TopicExchange} như 1 {@code @Bean} THUẦN (constructor object, KHÔNG mở
 * connection) — {@code RabbitAdmin} tự động của Spring Boot tự khai báo bean này LƯỜI (lazy), lần
 * đầu tiên connection thật được mở — không chặn boot, không phụ thuộc {@code td-woker}.</p>
 *
 * <p><b>Bugfix 05/09/2026 (lần 3 — outbox đánh dấu SENT dù bị RabbitMQ "trả lại" NO_ROUTE)</b>:
 * exchange tồn tại nhưng CHƯA có queue nào bind (td-woker chưa consume) → message bị trả lại
 * (basic.return) — {@code ConfirmCallback}/{@code ReturnsCallback} giờ gọi
 * {@link OutboxDeliveryFeedbackHandler#markUndelivered} để chuyển dòng {@code outbox_events} tương
 * ứng NGƯỢC lại {@code PENDING} (tự thử lại ở tick sau) thay vì để nguyên {@code SENT} sai sự
 * thật.</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "rabbit", matchIfMissing = true)
public class RabbitClientConfig {

    private final MessagingProperties props;
    private final RabbitDeliveryTracker deliveryTracker;
    private final OutboxDeliveryFeedbackHandler outboxDeliveryFeedbackHandler;

    /**
     * CHỈ tạo object {@code TopicExchange} (không mở connection nào) — {@code RabbitAdmin} tự động
     * của Spring Boot sẽ tự khai báo bean này lên broker LƯỜI (lazy), lần đầu tiên connection thật
     * được mở (không phải lúc bean này được tạo).
     */
    @Bean
    public TopicExchange messagingExchange() {
        return new TopicExchange(props.getExchange(), true, false);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (ack) {
                // Thanh cong that su - khong can lam gi them (markSent() da dung tu luc publish).
                return;
            }
            String messageId = correlation == null ? null : correlation.getId();
            log.error("RabbitMQ publisher-confirm NACK (messageId={}): {}", messageId, cause);
            deliveryTracker.consume(messageId)
                .ifPresent(tenantId -> outboxDeliveryFeedbackHandler.markUndelivered(tenantId, messageId, "NACK: " + cause));
        });
        template.setReturnsCallback(returned -> {
            String messageId = returned.getMessage().getMessageProperties().getMessageId();
            log.error("RabbitMQ message bi tra lai (khong route duoc): exchange={}, routingKey={}, "
                    + "replyText={}, messageId={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText(), messageId);
            deliveryTracker.consume(messageId)
                .ifPresent(tenantId -> outboxDeliveryFeedbackHandler.markUndelivered(
                    tenantId, messageId, "NO_ROUTE: " + returned.getReplyText()));
        });
        return template;
    }
}
