package com.td.infrastructure.messaging;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yêu cầu người dùng 05/09/2026 ("outbox đánh dấu SENT dù bị RabbitMQ trả lại (NO_ROUTE)... sửa
 * ngay cho đúng"): {@code RabbitTemplate.convertAndSend} là API bất đồng bộ về mặt XÁC NHẬN —
 * publisher-confirm (ACK/NACK) và "trả lại" (basic.return, khi không route được) đều đến SAU khi
 * {@code convertAndSend} đã return, trên thread callback RIÊNG của Rabbit client, KHÔNG có
 * {@code TenantContext} nào đang set. Map nhỏ này ghi nhớ tenant nào sở hữu {@code outbox_events}
 * cần cập nhật khi callback đến — key = {@code messageId} (= {@code OutboxEvent.id}, đã gán unique
 * mỗi lần publish).
 *
 * <p>Cả 2 callback ({@code ConfirmCallback} lẫn {@code ReturnsCallback}) đều PHẢI gọi
 * {@link #consume(String)} (tự xoá entry) khi xử lý xong — tránh rò rỉ bộ nhớ nếu message không bao
 * giờ nhận được phản hồi nào (hiếm, nhưng phải tự dọn để không phình vô hạn).</p>
 */
@Component
class RabbitDeliveryTracker {

    private final Map<String, String> tenantByMessageId = new ConcurrentHashMap<>();

    void track(String messageId, String tenantId) {
        if (messageId != null && tenantId != null) {
            tenantByMessageId.put(messageId, tenantId);
        }
    }

    /** Đọc VÀ xoá (chỉ xử lý callback đầu tiên tới cho 1 messageId — ACK/return chỉ cần 1 lần). */
    Optional<String> consume(String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tenantByMessageId.remove(messageId));
    }
}
