package com.td.application.messaging;

import java.util.List;

/**
 * Message trung lập (không biết Rabbit/Kafka) mà business code build lên rồi giao cho
 * {@link OutboxPublisherService#enqueue}. Xem javadoc {@link MessageUnitSpec} cho ý nghĩa
 * {@code units}.
 *
 * @param taskType      vd {@code "vbdi.delivery"} — PHẢI khớp {@code taskType} mà {@code td-woker}
 *                      (hoặc consumer tương lai) mong đợi.
 * @param correlationId mã truy vết xuyên hệ thống (log/metrics) — dùng lại cho mọi retry.
 * @param units         1+ unit độc lập, có thể khác tenant đích nhau trong CÙNG 1 message.
 */
public record OutboundMessage(String taskType, String correlationId, List<MessageUnitSpec> units) {
}
