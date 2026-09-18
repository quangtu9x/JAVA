package com.td.infrastructure.persistence.repository;

import com.td.domain.documents.DocumentLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentLogJpaRepository extends JpaRepository<DocumentLog, UUID> {

    Page<DocumentLog> findByDocumentId(UUID documentId, Pageable pageable);
}
