package com.td.infrastructure.persistence.repository;

import com.td.application.vbden.VbdenUserActionLogRepository;
import com.td.domain.vbden.VbdenUserActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 18/09/2026: adapter cho {@code vbden_user_action_logs} — {@code save} APPEND-ONLY;
 * {@code findByParentId} sắp theo {@code created_on DESC} (mới nhất trước) — mirror
 * {@code PostgresVbdiUserActionLogRepository}.
 */
@Repository
public class PostgresVbdenUserActionLogRepository implements VbdenUserActionLogRepository {

    private final VbdenUserActionLogJpaRepository jpaRepository;

    public PostgresVbdenUserActionLogRepository(VbdenUserActionLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends VbdenUserActionLog> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Page<VbdenUserActionLog> findByParentId(UUID parentId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdOn"));
        return jpaRepository.findByParentIdAndDeletedOnIsNull(parentId, sorted);
    }
}
