package com.td.infrastructure.persistence.repository;

import com.td.domain.processdiagram.ProcessDiagram;

import java.util.Optional;
import java.util.UUID;

public interface ProcessDiagramJpaRepository extends BaseRepository<ProcessDiagram> {

    Optional<ProcessDiagram> findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(UUID documentId, String documentType);
}
