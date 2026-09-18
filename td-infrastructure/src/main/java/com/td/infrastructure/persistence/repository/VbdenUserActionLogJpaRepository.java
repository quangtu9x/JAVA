package com.td.infrastructure.persistence.repository;

import com.td.domain.vbden.VbdenUserActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * 18/09/2026: JPQL filter vết thao tác theo văn bản đến cha + chưa bị xoá mềm — {@code parent_id}
 * là cột TYPED trên entity (không phải field động JSONB) nên dùng Spring Data thẳng, không cần
 * native SQL — mirror {@code VbdiUserActionLogJpaRepository}.
 */
public interface VbdenUserActionLogJpaRepository extends JpaRepository<VbdenUserActionLog, UUID> {

    @Query("SELECT e FROM VbdenUserActionLog e WHERE e.parentId = :parentId AND e.deletedOn IS NULL")
    Page<VbdenUserActionLog> findByParentIdAndDeletedOnIsNull(@Param("parentId") UUID parentId, Pageable pageable);
}
