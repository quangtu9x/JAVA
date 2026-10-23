package com.td.infrastructure.messaging;

import com.td.application.messaging.MessagePublisher;
import com.td.application.messaging.OutboundMessage;
import com.td.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter RabbitMQ của {@link MessagePublisher} — publish vào {@code app.messaging.exchange}
 * (mặc định {@code worker.exchange}, PHẢI khớp {@code worker.rabbit.exchange} phía td-woker) với
 * routing key {@code app.messaging.routing-prefix + taskType} (sanitize — xem bugfix dưới) — ĐÚNG
 * topology mà {@code com.td.worker.transport.rabbit.RabbitConfig} đã khai báo (queue mặc định
 * {@code worker.tasks} bind {@code worker.*} tự nhận mọi taskType).
 *
 * <p>Active DUY NHẤT khi {@code app.messaging.broker=rabbit} (mặc định) — xem
 * {@link RabbitClientConfig} cho bean {@code RabbitTemplate} (publisher-confirms).</p>
 *
 * <p>Bugfix 05/09/2026 (lần 1): ghi lại {@code (messageId -> tenant hiện tại)} vào
 * {@link RabbitDeliveryTracker} TRƯỚC khi publish — {@code ConfirmCallback}/{@code ReturnsCallback}
 * (chạy bất đồng bộ, sau khi method này đã return, KHÔNG còn {@link TenantContext} nào) cần biết
 * tenant nào sở hữu dòng {@code outbox_events} để chuyển lại {@code PENDING} khi bị NACK/NO_ROUTE.
 * Gắn thêm {@link CorrelationData} (id = messageId) để {@code ConfirmCallback} correlate được (Rabbit
 * KHÔNG tự trả lại {@code messageId} ở đường NACK, chỉ có ở đường "trả lại"/return).</p>
 *
 * <p><b>Bugfix 05/09/2026 (lần 2 — người dùng báo message bị trả lại "NO_ROUTE" dù td-woker đã
 * bật)</b>: {@code td-woker} bind queue mặc định {@code worker.tasks} với pattern
 * {@code worker.*} — topic exchange, dấu {@code *} chỉ khớp ĐÚNG 1 TỪ giữa 2 dấu chấm. Task type
 * tham khảo của {@code td-woker} là {@code "vbdi.delivery"} (CÓ dấu chấm bên trong!) → routing key
 * ghép thẳng {@code "worker." + "vbdi.delivery"} = {@code "worker.vbdi.delivery"} — 3 từ, KHÔNG
 * khớp {@code worker.*} (chỉ chấp nhận 2 từ) → message publish thành công nhưng KHÔNG route được
 * tới queue nào (basic.return NO_ROUTE), dù exchange/queue đều tồn tại đúng.
 *
 * <p>Routing key và {@code taskType} trong envelope JSON là 2 THỨ ĐỘC LẬP — {@code td-woker} chỉ
 * đọc {@code taskType} từ NỘI DUNG message ({@code InboundMessageRouter.route(body)}) để chọn
 * handler, hoàn toàn KHÔNG quan tâm routing key (routing key chỉ là cơ chế định tuyến của AMQP
 * broker, vô hình với code nghiệp vụ sau khi message đã vào queue). Vì vậy sửa AN TOÀN, KHÔNG cần
 * đụng {@code td-woker}: thay mọi dấu chấm trong {@code taskType} bằng {@code _} CHỈ khi build
 * routing key (đảm bảo LUÔN đúng 2 từ sau {@code worker.}, khớp {@code worker.*}, bất kể taskType
 * sau này có bao nhiêu dấu chấm) — envelope JSON ({@link EnvelopeJsonBuilder}) vẫn dùng
 * {@code taskType} GỐC nguyên vẹn, không đổi gì cho việc dispatch handler.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging", name = "broker", havingValue = "rabbit", matchIfMissing = true)
public class RabbitMessagePublisher implements MessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;
    private final EnvelopeJsonBuilder envelopeJsonBuilder;
    private final RabbitDeliveryTracker deliveryTracker;

    @Override
    public void publish(String messageId, OutboundMessage message) {
        // Sanitize CHỈ cho routing key (xem javadoc lớp bugfix lần 2) - envelope JSON build bên
        // dưới dùng message.taskType() GỐC nguyên vẹn, không bị ảnh hưởng.
        String routingKey = props.routingKeyFor(message.taskType());
        String json = envelopeJsonBuilder.build(messageId, message);

        String tenantId = TenantContext.getCurrentTenant();
        deliveryTracker.track(messageId, tenantId);

        rabbitTemplate.convertAndSend(props.getExchange(), routingKey, json, m -> {
            m.getMessageProperties().setMessageId(messageId);
            m.getMessageProperties().setContentType("application/json");
            return m;
        }, new CorrelationData(messageId));
        log.debug("Published RabbitMQ: exchange={}, routingKey={}, messageId={}, tenant={}",
            props.getExchange(), routingKey, messageId, tenantId);
    }
}
