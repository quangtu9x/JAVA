package com.td.application.messaging;

/**
 * Port (cổng) publish message ra broker — {@code td-application} KHÔNG biết đang publish qua
 * RabbitMQ hay Kafka (giữ đúng nguyên tắc "contract là lớp trừu tượng hoá" — {@code docs/
 * architecture/08-thay-the-domino-router-voi-rabbitmq-minio-postgresql.md §8.6}). Adapter thật ở
 * {@code td-infrastructure} ({@code RabbitMessagePublisher}/{@code KafkaMessagePublisher}), chọn 1
 * trong 2 lúc chạy qua {@code app.messaging.broker} — luôn CHỈ 1 bean active, business code không
 * cần biết/branch theo broker nào.
 *
 * <p>KHÔNG gọi trực tiếp từ use case nghiệp vụ — luôn đi qua {@link OutboxPublisherService} (outbox
 * pattern, tránh publish "ảo" khi transaction rollback). Chỉ {@code OutboxDispatcherScheduler} gọi
 * interface này.</p>
 */
public interface MessagePublisher {

    /**
     * @param messageId  id ỔN ĐỊNH xuyên các lần publish lại (retry) — dùng làm {@code messageId}
     *                   envelope để Worker dedup đúng (xem javadoc {@code OutboxEvent#id}).
     * @param message    nội dung cần publish.
     */
    void publish(String messageId, OutboundMessage message);
}
