package com.td.infrastructure.persistence.repository;

import com.td.domain.vbdi.VbdiEditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Yêu cầu người dùng 28/08/2026: JPQL filter vết hiệu chỉnh theo văn bản đi cha + chưa bị xoá mềm,
 * kết hợp Sort từ Pageable (created_on ASC do adapter truyền vào) — mirror
 * {@code DuThaoEditHistoryDocumentJpaRepository}.
 */
public interface VbdiEditHistoryDocumentJpaRepository
        extends org.springframework.data.jpa.repository.JpaRepository<VbdiEditHistoryDocument, UUID> {

    @Query("SELECT e FROM VbdiEditHistoryDocument e WHERE e.parentId = :parentId AND e.deletedOn IS NULL")
    Page<VbdiEditHistoryDocument> findByParentIdAndDeletedOnIsNull(@Param("parentId") UUID parentId, Pageable pageable);
}
