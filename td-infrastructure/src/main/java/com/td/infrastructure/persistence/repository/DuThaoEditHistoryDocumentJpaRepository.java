package com.td.infrastructure.persistence.repository;

import com.td.domain.duthao.DuThaoEditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Yêu cầu người dùng 27/08/2026: JPQL filter vết hiệu chỉnh theo dự thảo cha + chưa bị xoá mềm,
 * kết hợp Sort từ Pageable (created_on ASC do adapter truyền vào).
 */
public interface DuThaoEditHistoryDocumentJpaRepository
        extends org.springframework.data.jpa.repository.JpaRepository<DuThaoEditHistoryDocument, UUID> {

    @Query("SELECT e FROM DuThaoEditHistoryDocument e WHERE e.parentId = :parentId AND e.deletedOn IS NULL")
    Page<DuThaoEditHistoryDocument> findByParentIdAndDeletedOnIsNull(@Param("parentId") UUID parentId, Pageable pageable);
}
