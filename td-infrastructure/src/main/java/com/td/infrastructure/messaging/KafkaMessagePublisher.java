package com.td.infrastructure.messaging;

import com.td.application.messaging.MessagePublisher;
import com.td.application.messaging.OutboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter Kafka của {@link MessagePublisher} — publish vào {@code app.messaging.kafka-topic}
 * (mặc định {@code worker.tasks}, PHẢI khớp 1 topic trong {@code worker.kafka.topics[]} phía
 * td-woker) — router của Worker đọc {@code taskType} TỪ BODY envelope, không phải tên topic, nên
 * KHÔNG cần topic riêng theo từng taskType.
 *
 * <p>Key Kafka = {@code tenantId} của unit ĐẦU TIÊN (affinity theo tenant — cùng tenant vào cùng
 * partition, giữ thứ tự xử lý trong 1 tenant) — active DUY NHẤT khi
 * {@code app.messaging.broker=kafka}, xem {@link KafkaClientConfig}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "kafka")
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MessagingProperties props;
    private final EnvelopeJsonBuilder envelopeJsonBuilder;

    @Override
    public void publish(String messageId, OutboundMessage message) {
        String topic = props.getKafkaTopic();
        String json = envelopeJsonBuilder.build(messageId, message);
        String key = message.units().isEmpty() ? messageId : message.units().get(0).tenantId();
        kafkaTemplate.send(topic, key, json);
        log.debug("Published Kafka: topic={}, key={}, messageId={}", topic, key, messageId);
    }
}
