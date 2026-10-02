package com.td.infrastructure.persistence.repository;

import com.td.application.vbden.VbdenUpdateHistoryDocumentRepository;
import com.td.domain.vbden.VbdenUpdateHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Yêu cầu người dùng 24/09/2026: adapter cho {@code vbden_update_history_documents} (lịch sử "Cập nhật
 * VB") — save APPEND-ONLY, đọc MỚI NHẤT TRƯỚC — mirror {@link PostgresVbdenEditHistoryDocumentRepository}.
 */
@Repository
public class PostgresVbdenUpdateHistoryDocumentRepository implements VbdenUpdateHistoryDocumentRepository {

    private final VbdenUpdateHistoryDocumentJpaRepository jpaRepository;

    public PostgresVbdenUpdateHistoryDocumentRepository(VbdenUpdateHistoryDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends VbdenUpdateHistoryDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Page<VbdenUpdateHistoryDocument> findByParentIdDesc(java.util.UUID parentId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdOn"));
        return jpaRepository.findByParentIdAndDeletedOnIsNull(parentId, sorted);
    }
}
