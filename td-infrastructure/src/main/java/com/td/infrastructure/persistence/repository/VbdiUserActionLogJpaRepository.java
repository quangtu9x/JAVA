package com.td.infrastructure.persistence.repository;

import com.td.domain.vbdi.VbdiUserActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * 30/08/2026: JPQL filter vết thao tác theo văn bản đi cha + chưa bị xoá mềm — {@code parent_id} là
 * cột TYPED trên entity (không phải field động JSONB) nên dùng Spring Data thẳng, không cần native
 * SQL (đúng tiền lệ {@code VbdiSendLogDocumentJpaRepository}).
 */
public interface VbdiUserActionLogJpaRepository extends JpaRepository<VbdiUserActionLog, UUID> {

    @Query("SELECT e FROM VbdiUserActionLog e WHERE e.parentId = :parentId AND e.deletedOn IS NULL")
    Page<VbdiUserActionLog> findByParentIdAndDeletedOnIsNull(@Param("parentId") UUID parentId, Pageable pageable);
}
