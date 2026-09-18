package com.td.infrastructure.persistence.repository;

import com.td.application.vbdi.VbdiSendLogDocumentRepository;
import com.td.domain.vbdi.VbdiSendLogDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026 ("tách lưu Nhật ký gửi nhận ra bảng vật lý khác"): adapter cho
 * {@code vbdi_send_log_documents} — {@code save} APPEND-ONLY; {@code findByParentId} sắp theo
 * {@code created_on DESC} (mới nhất trước — xem javadoc interface).
 */
@Repository
public class PostgresVbdiSendLogDocumentRepository implements VbdiSendLogDocumentRepository {

    private final VbdiSendLogDocumentJpaRepository jpaRepository;

    public PostgresVbdiSendLogDocumentRepository(VbdiSendLogDocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends VbdiSendLogDocument> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Page<VbdiSendLogDocument> findByParentId(UUID parentId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdOn"));
        return jpaRepository.findByParentIdAndDeletedOnIsNull(parentId, sorted);
    }

    @Override
    public boolean existsByParentIdAndRecipientGroupAndRecipientIdentifier(
            UUID parentId, String recipientGroup, String recipientIdentifier) {
        return jpaRepository.existsByParentIdAndRecipientGroupAndRecipientIdentifierAndDeletedOnIsNull(
            parentId, recipientGroup, recipientIdentifier);
    }
}
