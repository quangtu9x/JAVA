package com.td.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.messaging.MessagePublisher;
import com.td.domain.messaging.OutboxEvent;
import com.td.infrastructure.persistence.repository.OutboxEventJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutboxTenantDispatcher} xử lý 1 batch outbox CHO 1 TENANT: publish thành công → SENT;
 * publish lỗi → giữ PENDING + tăng attempts/backoff (retry ở tick sau) — KHÔNG được để 1 event lỗi
 * chặn các event còn lại trong CÙNG batch.
 */
@ExtendWith(MockitoExtension.class)
class OutboxTenantDispatcherTest {

    @Mock
    private OutboxEventJpaRepository outboxEventJpaRepository;
    @Mock
    private MessagePublisher messagePublisher;

    private MessagingProperties props;
    private OutboxTenantDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        props = new MessagingProperties();
        dispatcher = new OutboxTenantDispatcher(outboxEventJpaRepository, props, new ObjectMapper());
    }

    private OutboxEvent event() {
        return new OutboxEvent("vbdi", UUID.randomUUID(), "vbdi.delivery", "corr-1", "td-web", "[]");
    }

    @Test
    void dispatchTenant_whenPublishSucceeds_shouldMarkSent() {
        var event = event();
        when(outboxEventJpaRepository.lockNextBatch(any(), anyInt())).thenReturn(List.of(event));

        dispatcher.dispatchTenant(messagePublisher);

        verify(messagePublisher).publish(event.getId().toString(),
            new com.td.application.messaging.OutboundMessage("vbdi.delivery", "corr-1", List.of()));
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.STATUS_SENT);
        verify(outboxEventJpaRepository).save(event);
    }

    @Test
    void dispatchTenant_whenPublishFails_shouldKeepPendingAndScheduleRetry() {
        var event = event();
        when(outboxEventJpaRepository.lockNextBatch(any(), anyInt())).thenReturn(List.of(event));
        doThrow(new RuntimeException("broker down")).when(messagePublisher).publish(any(), any());

        dispatcher.dispatchTenant(messagePublisher);

        assertThat(event.isPending()).isTrue();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextTryAt()).isAfter(LocalDateTime.now());
        assertThat(event.getLastError()).contains("broker down");
        verify(outboxEventJpaRepository).save(event);
    }

    @Test
    void dispatchTenant_whenAttemptsExceedMax_shouldMarkFailed() {
        var event = event();
        for (int i = 0; i < props.getOutbox().getMaxAttempts() - 1; i++) {
            event.markRetry("err", LocalDateTime.now(), props.getOutbox().getMaxAttempts());
        }
        when(outboxEventJpaRepository.lockNextBatch(any(), anyInt())).thenReturn(List.of(event));
        doThrow(new RuntimeException("broker down")).when(messagePublisher).publish(any(), any());

        dispatcher.dispatchTenant(messagePublisher);

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.STATUS_FAILED);
    }
}
