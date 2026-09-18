package com.td.application.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.domain.messaging.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 04/09/2026 ("viết class chung... tái dùng"): {@link OutboxPublisherService}
 * là API DUY NHẤT business code cần gọi để publish message — test đảm bảo nó chỉ GHI 1 dòng
 * outbox {@code PENDING} (KHÔNG tự publish gì) và không văng exception ra ngoài khi serialize lỗi.
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxPublisherService service;

    @BeforeEach
    void setUp() {
        service = new OutboxPublisherService(outboxEventRepository, new ObjectMapper());
    }

    @Test
    void enqueue_shouldSavePendingOutboxEventWithSerializedUnits() {
        UUID aggregateId = UUID.randomUUID();
        var unit = new MessageUnitSpec("u1", "tenant-a", Map.of("k", "v"));
        var message = new OutboundMessage("vbdi.delivery", "corr-1", List.of(unit));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID outboxId = service.enqueue("vbdi", aggregateId, message);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        org.mockito.Mockito.verify(outboxEventRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(outboxId).isEqualTo(saved.getId());
        assertThat(saved.getAggregateType()).isEqualTo("vbdi");
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(saved.getTaskType()).isEqualTo("vbdi.delivery");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-1");
        assertThat(saved.isPending()).isTrue();
        assertThat(saved.getPayloadJson()).contains("tenant-a").contains("\"k\":\"v\"");
    }
}
