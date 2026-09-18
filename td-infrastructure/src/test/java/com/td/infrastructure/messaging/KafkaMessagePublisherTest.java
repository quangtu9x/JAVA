package com.td.infrastructure.messaging;

import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KafkaMessagePublisher} PHẢI publish vào topic {@code app.messaging.kafka-topic} (mặc định
 * {@code worker.tasks}, khớp {@code worker.kafka.topics[]} phía td-woker), key = tenantId của unit
 * đầu tiên (affinity theo tenant).
 */
@ExtendWith(MockitoExtension.class)
class KafkaMessagePublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private EnvelopeJsonBuilder envelopeJsonBuilder;

    private MessagingProperties props;
    private KafkaMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        props = new MessagingProperties();
        publisher = new KafkaMessagePublisher(kafkaTemplate, props, envelopeJsonBuilder);
    }

    @Test
    void publish_shouldSendToConfiguredTopicWithTenantKey() {
        var unit = new MessageUnitSpec("u1", "tenant-a", Map.of());
        var message = new OutboundMessage("vbdi.delivery", "corr-1", List.of(unit));
        when(envelopeJsonBuilder.build("msg-1", message)).thenReturn("{\"json\":true}");

        publisher.publish("msg-1", message);

        verify(kafkaTemplate).send("worker.tasks", "tenant-a", "{\"json\":true}");
    }
}
