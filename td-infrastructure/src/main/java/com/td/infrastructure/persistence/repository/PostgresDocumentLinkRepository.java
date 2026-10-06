package com.td.infrastructure.persistence.repository;

import com.td.application.documentlinks.DocumentLinkRepository;
import com.td.domain.documentlinks.DocumentLink;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 06/10/2026 - xem docs/architecture/document-links.md. */
@Repository
public class PostgresDocumentLinkRepository implements DocumentLinkRepository {

    private final DocumentLinkJpaRepository jpaRepository;

    public PostgresDocumentLinkRepository(DocumentLinkJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public <S extends DocumentLink> S save(S entity) {
        return jpaRepository.save(entity);
    }

    @Override
    public Optional<DocumentLink> findActive(UUID sourceId, UUID targetId, String linkType) {
        return jpaRepository.findActive(sourceId, targetId, linkType);
    }

    @Override
    public List<DocumentLink> findActiveBySource(UUID sourceId, Collection<String> linkTypes) {
        if (sourceId == null || linkTypes == null || linkTypes.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findActiveBySource(sourceId, linkTypes);
    }

    @Override
    public List<DocumentLink> findActiveByTarget(UUID targetId, Collection<String> linkTypes) {
        if (targetId == null || linkTypes == null || linkTypes.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findActiveByTarget(targetId, linkTypes);
    }
}
