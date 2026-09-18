package com.td.infrastructure.persistence.repository;

import com.td.application.vbdi.VbdiUserActionLogRepository;
import com.td.domain.vbdi.VbdiUserActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 30/08/2026: adapter cho {@code vbdi_user_action_logs} — {@code save} APPEND-ONLY;
 * {@code findByParentId} sắp theo {@code created_on DESC} (mới nhất trước — xem javadoc interface).
 */
@Repository
public class PostgresVbdiUserActionLogRepository implements VbdiUserActionLogRepository {

    private final VbdiUserActionLogJpaRepository jpaRepository;

    public PostgresVbdiUserActionLogRepository(VbdiUserActionLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends VbdiUserActionLog> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Page<VbdiUserActionLog> findByParentId(UUID parentId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdOn"));
        return jpaRepository.findByParentIdAndDeletedOnIsNull(parentId, sorted);
    }
}
