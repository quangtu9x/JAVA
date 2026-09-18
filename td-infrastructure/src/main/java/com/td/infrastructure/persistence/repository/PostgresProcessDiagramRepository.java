package com.td.infrastructure.persistence.repository;

import com.td.application.processdiagram.ProcessDiagramRepository;
import com.td.domain.processdiagram.ProcessDiagram;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresProcessDiagramRepository implements ProcessDiagramRepository {

    private final ProcessDiagramJpaRepository jpa;

    @Override
    public Optional<ProcessDiagram> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<ProcessDiagram, UUID>) jpa).findById(id);
    }

    @Override
    public <S extends ProcessDiagram> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(ProcessDiagram entity) {
        jpa.delete(entity);
    }

    @Override
    public Optional<ProcessDiagram> findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(UUID documentId, String documentType) {
        return jpa.findByDocumentIdAndDocumentTypeAndDeletedOnIsNull(documentId, documentType);
    }
}
