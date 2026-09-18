package com.td.infrastructure.messaging;

import com.td.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.td.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bugfix 05/09/2026 ("outbox đánh dấu SENT dù bị RabbitMQ trả lại — sửa ngay cho đúng"):
 * {@link OutboxDeliveryFeedbackHandler} phải chuyển ĐÚNG tenant DB (qua {@link TenantContext})
 * trước khi cập nhật {@code outbox_events} — callback Rabbit chạy trên thread không có tenant nào
 * đang set sẵn.
 */
@ExtendWith(MockitoExtension.class)
class OutboxDeliveryFeedbackHandlerTest {

    @Mock
    private OutboxEventJpaRepository outboxEventJpaRepository;

    private OutboxDeliveryFeedbackHandler handler;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void markUndelivered_shouldSwitchToGivenTenantBeforeUpdating() {
        handler = new OutboxDeliveryFeedbackHandler(outboxEventJpaRepository);
        UUID outboxId = UUID.randomUUID();
        when(outboxEventJpaRepository.markUndeliveredForRetry(eq(outboxId), anyString(), any(LocalDateTime.class)))
            .thenAnswer(inv -> {
                assertThat(TenantContext.getCurrentTenant()).isEqualTo("H05_01");
                return 1;
            });

        handler.markUndelivered("H05_01", outboxId.toString(), "NO_ROUTE: no queue bound");

        verify(outboxEventJpaRepository).markUndeliveredForRetry(eq(outboxId), anyString(), any(LocalDateTime.class));
        // TenantContext phai duoc clear lai sau khi xong (khong ro ri sang callback ke tiep).
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void markUndelivered_shouldNotThrow_whenTenantIdMissing() {
        handler = new OutboxDeliveryFeedbackHandler(outboxEventJpaRepository);

        handler.markUndelivered(null, UUID.randomUUID().toString(), "NACK");

        org.mockito.Mockito.verifyNoInteractions(outboxEventJpaRepository);
    }

    @Test
    void markUndelivered_shouldNotThrow_whenRepositoryFails() {
        handler = new OutboxDeliveryFeedbackHandler(outboxEventJpaRepository);
        when(outboxEventJpaRepository.markUndeliveredForRetry(any(), anyString(), any()))
            .thenThrow(new RuntimeException("db down"));

        handler.markUndelivered("H05_01", UUID.randomUUID().toString(), "NACK");
        // Khong throw ra ngoai - callback Rabbit khong duoc phep bi loi lam hong gi them.
    }
}
