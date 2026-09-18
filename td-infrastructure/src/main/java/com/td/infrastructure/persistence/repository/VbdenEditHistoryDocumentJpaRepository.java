package com.td.infrastructure.persistence.repository;

import com.td.domain.vbden.VbdenEditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Yêu cầu người dùng 12/09/2026: JPQL filter vết hiệu chỉnh theo văn bản đến cha + chưa bị xoá mềm,
 * kết hợp Sort từ Pageable (created_on ASC do adapter truyền vào) — mirror
 * {@code VbdiEditHistoryDocumentJpaRepository}.
 */
public interface VbdenEditHistoryDocumentJpaRepository
        extends org.springframework.data.jpa.repository.JpaRepository<VbdenEditHistoryDocument, UUID> {

    @Query("SELECT e FROM VbdenEditHistoryDocument e WHERE e.parentId = :parentId AND e.deletedOn IS NULL")
    Page<VbdenEditHistoryDocument> findByParentIdAndDeletedOnIsNull(@Param("parentId") UUID parentId, Pageable pageable);
}
