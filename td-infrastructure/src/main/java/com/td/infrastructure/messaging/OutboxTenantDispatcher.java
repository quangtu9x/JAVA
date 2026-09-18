package com.td.infrastructure.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.messaging.MessagePublisher;
import com.td.application.messaging.MessageUnitSpec;
import com.td.application.messaging.OutboundMessage;
import com.td.domain.messaging.OutboxEvent;
import com.td.infrastructure.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Xử lý 1 batch outbox CHO TENANT ĐANG ĐƯỢC ĐẶT ({@link com.td.infrastructure.tenant.TenantContext})
 * — tách riêng khỏi {@link OutboxDispatcherScheduler} để {@code @Transactional} có hiệu lực (Spring
 * AOP proxy không áp dụng cho self-invocation trong cùng 1 bean). Mỗi tenant 1 transaction
 * {@code REQUIRES_NEW} riêng — lỗi/rollback ở tenant này không ảnh hưởng tenant khác trong cùng
 * 1 tick của scheduler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxTenantDispatcher {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final MessagingProperties props;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchTenant(MessagePublisher publisher) {
        List<OutboxEvent> batch = outboxEventJpaRepository.lockNextBatch(
            LocalDateTime.now(), props.getOutbox().getBatchSize());
        for (OutboxEvent event : batch) {
            try {
                OutboundMessage message = toOutboundMessage(event);
                publisher.publish(event.getId().toString(), message);
                event.markSent();
            } catch (Exception ex) {
                long backoffMs = Math.min(
                    props.getOutbox().getRetryInitialBackoffMs() * (1L << Math.min(event.getAttempts(), 10)),
                    props.getOutbox().getRetryMaxBackoffMs());
                event.markRetry(ex.getMessage(), LocalDateTime.now().plusNanos(backoffMs * 1_000_000L),
                    props.getOutbox().getMaxAttempts());
                log.error("Publish outbox that bai: outboxId={}, taskType={}, attempts={}",
                    event.getId(), event.getTaskType(), event.getAttempts(), ex);
            }
            outboxEventJpaRepository.save(event);
        }
    }

    private OutboundMessage toOutboundMessage(OutboxEvent event) throws Exception {
        List<MessageUnitSpec> units = objectMapper.readValue(
            event.getPayloadJson(), new TypeReference<List<MessageUnitSpec>>() { });
        return new OutboundMessage(event.getTaskType(), event.getCorrelationId(), units);
    }
}
