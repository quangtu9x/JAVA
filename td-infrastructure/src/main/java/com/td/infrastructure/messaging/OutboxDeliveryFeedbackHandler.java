package com.td.infrastructure.messaging;

import com.td.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.td.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Yêu cầu người dùng 05/09/2026 ("outbox đánh dấu SENT dù bị RabbitMQ trả lại... sửa ngay cho
 * đúng"): xử lý phản hồi bất đồng bộ từ RabbitMQ (publisher-confirm NACK / basic.return NO_ROUTE) —
 * chuyển {@code OutboxEvent} tương ứng NGƯỢC lại {@code PENDING} thay vì để nguyên {@code SENT}
 * "ảo" (dòng đã được {@link OutboxTenantDispatcher} đánh dấu SENT LẠC QUAN ngay sau
 * {@code convertAndSend} — trước khi biết chắc broker có route được hay không).
 *
 * <p>Callback của Rabbit client chạy trên thread RIÊNG, KHÔNG có {@link TenantContext} nào đang
 * set — {@code tenantId} PHẢI được truyền vào tường minh (do {@link RabbitMessagePublisher} ghi lại
 * qua {@link RabbitDeliveryTracker} lúc publish, vì {@code outbox_events} là bảng tenant-routed).</p>
 *
 * <p>KHÔNG cần tự thêm {@code @Transactional} ở đây — {@code markUndeliveredForRetry} là method
 * trên bean REPOSITORY (Spring Data JPA), bản thân nó đã tự bọc transaction (mọi method của
 * repository, kể cả {@code @Modifying @Query} tự khai báo, đều được Spring Data proxy transactional
 * hoá mặc định).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class OutboxDeliveryFeedbackHandler {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    void markUndelivered(String tenantId, String outboxEventId, String reason) {
        if (tenantId == null || outboxEventId == null) {
            log.warn("Khong the chuyen outbox ve PENDING (thieu tenantId/outboxEventId) - reason={}", reason);
            return;
        }
        try {
            TenantContext.runAs(tenantId, () -> {
                int updated = outboxEventJpaRepository.markUndeliveredForRetry(
                    UUID.fromString(outboxEventId), reason, LocalDateTime.now().plusSeconds(5));
                if (updated > 0) {
                    log.warn("Outbox {} bi RabbitMQ tu choi ({}) - da chuyen ve PENDING de thu lai",
                        outboxEventId, reason);
                }
            });
        } catch (Exception ex) {
            log.error("Loi khi chuyen outbox {} (tenant {}) ve PENDING sau khi bi RabbitMQ tu choi",
                outboxEventId, tenantId, ex);
        }
    }
}
