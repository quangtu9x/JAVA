package com.td.infrastructure.persistence.repository;

import com.td.domain.documents.FileAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * 09/09/2026: Spring Data JPA thuần cho phần ĐỌC — {@code module}/{@code documentId} là cột TYPED
 * (không phải field động JSONB) nên derived query đủ dùng, không cần native SQL (đúng tiền lệ
 * {@code VbdiUserActionLogJpaRepository}). Phần GHI (UPSERT) đi qua {@code EntityManager} native
 * query riêng ở {@code PostgresFileAccessLogRepository#recordAccess} — KHÔNG dùng {@code save()} của
 * interface này (xem javadoc {@link FileAccessLog}).
 */
public interface FileAccessLogJpaRepository extends JpaRepository<FileAccessLog, UUID> {

    Page<FileAccessLog> findByModuleAndDocumentIdOrderByLastAccessedOnDesc(String module, UUID documentId, Pageable pageable);
}
