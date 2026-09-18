package com.td.infrastructure.persistence.repository;

import com.td.domain.vbdi.VbdiSendLogDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026: JPQL filter vết gửi/nhận theo văn bản đi cha + chưa bị xoá mềm —
 * {@code parent_id}/{@code recipient_group}/{@code recipient_identifier} là cột TYPED trên entity
 * (không phải field động JSONB) nên dùng Spring Data thẳng, không cần native SQL (đúng tiền lệ
 * {@code DuThaoEditHistoryDocumentJpaRepository}, khác {@code VbdiDocumentJpaRepository} phải native
 * SQL vì containment JSONB).
 */
public interface VbdiSendLogDocumentJpaRepository extends JpaRepository<VbdiSendLogDocument, UUID> {

    @Query("SELECT e FROM VbdiSendLogDocument e WHERE e.parentId = :parentId AND e.deletedOn IS NULL")
    Page<VbdiSendLogDocument> findByParentIdAndDeletedOnIsNull(@Param("parentId") UUID parentId, Pageable pageable);

    boolean existsByParentIdAndRecipientGroupAndRecipientIdentifierAndDeletedOnIsNull(
        UUID parentId, String recipientGroup, String recipientIdentifier);
}
