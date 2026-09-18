package com.td.worker.transport.rabbit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher RabbitMQ cho OUTBOUND từ handler (vd VBDI Worker nhận-send publish envelope
 * phản hồi theo {@code senderReplyRoutingKey}). Dùng {@code RabbitTemplate} (CachingConnectionFactory
 * của Spring Boot quản lý connection/channel pool — docs/promt.txt "RabbitMQ connection/channel pool").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitPublisher {

    private final RabbitTemplate rabbitTemplate;

    /** @param routingKey routing key, vd "worker.vbdi.reply" hoặc routing theo identifier. */
    public void publish(String exchange, String routingKey, String jsonBody) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, jsonBody);
            log.debug("Publish RabbitMQ: exchange={}, routingKey={}, size={}",
                exchange, routingKey, jsonBody == null ? 0 : jsonBody.length());
        } catch (RuntimeException e) {
            log.error("Publish RabbitMQ thất bại exchange={}, routingKey={}: {}",
                exchange, routingKey, e.getMessage());
            throw e;
        }
    }
}