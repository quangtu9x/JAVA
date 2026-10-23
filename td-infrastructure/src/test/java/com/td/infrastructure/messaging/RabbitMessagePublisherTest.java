package com.td.infrastructure.messaging;

import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import com.td.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * {@link RabbitMessagePublisher} PHẢI publish ĐÚNG exchange {@code app.messaging.exchange} với
 * routing key {@code routingPrefix + taskType} — khớp topology
 * {@code com.td.worker.transport.rabbit.RabbitConfig} (td-woker): exchange {@code worker.exchange},
 * queue mặc định {@code worker.tasks} bind {@code worker.*}.
 */
@ExtendWith(MockitoExtension.class)
class RabbitMessagePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private EnvelopeJsonBuilder envelopeJsonBuilder;

    private MessagingProperties props;
    private RabbitDeliveryTracker deliveryTracker;
    private RabbitMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        props = new MessagingProperties();
        deliveryTracker = new RabbitDeliveryTracker();
        publisher = new RabbitMessagePublisher(rabbitTemplate, props, envelopeJsonBuilder, deliveryTracker);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publish_shouldSendToConfiguredExchangeWithWorkerRoutingKey() {
        var unit = new MessageUnitSpec("u1", "tenant-a", Map.of());
        var message = new OutboundMessage("vbdi.delivery", "corr-1", List.of(unit));
        org.mockito.Mockito.when(envelopeJsonBuilder.build("msg-1", message)).thenReturn("{\"json\":true}");

        publisher.publish("msg-1", message);

        ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(exchangeCaptor.capture(), routingKeyCaptor.capture(),
            bodyCaptor.capture(), org.mockito.ArgumentMatchers.any(MessagePostProcessor.class),
            correlationCaptor.capture());

        assertThat(exchangeCaptor.getValue()).isEqualTo("worker.exchange");
        // Bugfix 05/09/2026 (lan 2): dau cham trong taskType duoc doi thanh "_" CHI o routing key -
        // td-woker bind queue mac dinh pattern "worker.*" (topic exchange, "*" chi khop DUNG 1 tu);
        // giu nguyen "worker.vbdi.delivery" (3 tu) se KHONG khop, message bi tra lai NO_ROUTE (dung
        // loi nguoi dung da gap that khi test).
        assertThat(routingKeyCaptor.getValue()).isEqualTo("worker.vbdi_delivery");
        assertThat(bodyCaptor.getValue()).isEqualTo("{\"json\":true}");
        assertThat(correlationCaptor.getValue().getId()).isEqualTo("msg-1");
    }

    /**
     * 14/10/2026: task của TD Gateway giữ dấu chấm — queue gateway bind {@code worker.guinhanquocgia.send}; dạng "_"
     * lại khớp queue chung {@code worker.*} của td-woker (không có handler) và gateway không nhận được.
     */
    @Test
    void publish_gatewayTask_shouldKeepDottedRoutingKey() {
        var message = new OutboundMessage("guinhanquocgia.send", "corr-1", List.of(new MessageUnitSpec("u1", "t", Map.of())));
        org.mockito.Mockito.when(envelopeJsonBuilder.build("msg-2", message)).thenReturn("{}");

        publisher.publish("msg-2", message);

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(org.mockito.ArgumentMatchers.anyString(), routingKeyCaptor.capture(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(MessagePostProcessor.class),
            org.mockito.ArgumentMatchers.any(CorrelationData.class));
        assertThat(routingKeyCaptor.getValue()).isEqualTo("worker.guinhanquocgia.send");
        assertThat(props.kafkaTopicFor("guinhanquocgia.send")).isEqualTo("tdgateway.guinhanquocgia.send");
        assertThat(props.kafkaTopicFor("vbdi.delivery")).isEqualTo("worker.tasks");
    }

    /**
     * Đảm bảo việc sanitize routing key KHÔNG ảnh hưởng envelope JSON body — {@code td-woker} dispatch
     * handler dựa trên {@code taskType} ĐỌC TỪ BODY, không phải routing key.
     */
    @Test
    void publish_shouldBuildEnvelopeWithOriginalUnsanitizedTaskType() {
        var message = new OutboundMessage("vbdi.delivery", "corr-1", List.of());
        org.mockito.Mockito.when(envelopeJsonBuilder.build("msg-1", message)).thenReturn("{}");

        publisher.publish("msg-1", message);

        ArgumentCaptor<OutboundMessage> messageCaptor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(envelopeJsonBuilder).build(org.mockito.ArgumentMatchers.eq("msg-1"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().taskType()).isEqualTo("vbdi.delivery");
    }

    /**
     * Bugfix 05/09/2026: publish PHẢI ghi lại tenant hiện tại vào {@link RabbitDeliveryTracker} —
     * để {@code ConfirmCallback}/{@code ReturnsCallback} (chạy bất đồng bộ, không còn
     * {@link TenantContext}) biết chuyển đúng tenant DB khi cần revert outbox về PENDING.
     */
    @Test
    void publish_shouldTrackCurrentTenantForAsyncDeliveryFeedback() {
        TenantContext.setCurrentTenant("H05_01");
        var message = new OutboundMessage("vbdi.delivery", "corr-1", List.of());
        org.mockito.Mockito.when(envelopeJsonBuilder.build("msg-1", message)).thenReturn("{}");

        publisher.publish("msg-1", message);

        assertThat(deliveryTracker.consume("msg-1")).contains("H05_01");
    }
}
