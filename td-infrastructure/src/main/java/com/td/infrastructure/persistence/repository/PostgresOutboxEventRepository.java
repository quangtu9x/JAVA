package com.td.infrastructure.persistence.repository;

import com.td.application.messaging.OutboxEventRepository;
import com.td.domain.messaging.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Di chuyển sang {@code com.td.infrastructure.persistence.repository} 04/09/2026 (cùng lý do
 * {@link OutboxEventJpaRepository}) — đúng quy ước đặt CẢ adapter lẫn Jpa repository interface
 * trong 1 package cho mọi bảng "primary" (tenant-routed), giống {@code PostgresVbdiSendLogDocumentRepository}.
 */
@Repository
@RequiredArgsConstructor
public class PostgresOutboxEventRepository implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public <S extends OutboxEvent> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public List<OutboxEvent> lockNextBatch(LocalDateTime now, int batchSize) {
        return jpaRepository.lockNextBatch(now, batchSize);
    }
}
