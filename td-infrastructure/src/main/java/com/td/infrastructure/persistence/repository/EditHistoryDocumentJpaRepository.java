package com.td.infrastructure.persistence.repository;

import com.td.domain.edithistory.EditHistoryDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** 30/10/2026: JPQL vết hiệu chỉnh dùng chung theo bản ghi cha, chưa xoá mềm (Sort do adapter truyền). */
public interface EditHistoryDocumentJpaRepository extends JpaRepository<EditHistoryDocument, UUID> {

    @Query("SELECT e FROM EditHistoryDocument e WHERE e.parentId = :parentId AND e.deletedOn IS NULL")
    Page<EditHistoryDocument> findByParentIdAndDeletedOnIsNull(@Param("parentId") String parentId, Pageable pageable);
}
