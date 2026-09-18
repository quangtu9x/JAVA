package com.td.infrastructure.persistence.repository;

import com.td.application.documents.DocumentLogRepository;
import com.td.domain.documents.DocumentLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresDocumentLogRepository implements DocumentLogRepository {

    private final DocumentLogJpaRepository jpa;

    @Override
    public DocumentLog save(DocumentLog documentLog) {
        return jpa.save(documentLog);
    }

    @Override
    public Page<DocumentLog> findByDocumentId(UUID documentId, Pageable pageable) {
        return jpa.findByDocumentId(documentId, pageable);
    }
}
