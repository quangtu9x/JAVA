package com.td.infrastructure.persistence.repository;

import com.td.domain.vbden.VbdenUpdateHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Yêu cầu người dùng 24/09/2026: lịch sử "Cập nhật VB" theo văn bản đến đích + chưa xoá mềm, Sort từ
 * Pageable (adapter truyền created_on DESC) — mirror {@link VbdenEditHistoryDocumentJpaRepository}.
 */
public interface VbdenUpdateHistoryDocumentJpaRepository
        extends org.springframework.data.jpa.repository.JpaRepository<VbdenUpdateHistoryDocument, UUID> {

    @Query("SELECT e FROM VbdenUpdateHistoryDocument e WHERE e.parentId = :parentId AND e.deletedOn IS NULL")
    Page<VbdenUpdateHistoryDocument> findByParentIdAndDeletedOnIsNull(@Param("parentId") UUID parentId, Pageable pageable);
}
