package com.td.infrastructure.persistence.repository;

import com.td.application.edithistory.EditHistoryDocumentRepository;
import com.td.domain.edithistory.EditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** 30/10/2026: adapter cho {@code edit_history_documents} — save append-only, đọc mới nhất trước. */
@Repository
public class PostgresEditHistoryDocumentRepository implements EditHistoryDocumentRepository {

    private final EditHistoryDocumentJpaRepository jpaRepository;

    public PostgresEditHistoryDocumentRepository(EditHistoryDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends EditHistoryDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Page<EditHistoryDocument> findByParentIdNewestFirst(String parentId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdOn"));
        return jpaRepository.findByParentIdAndDeletedOnIsNull(parentId, sorted);
    }
}
