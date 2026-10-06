package com.td.infrastructure.persistence.repository;

import com.td.domain.documentlinks.DocumentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 06/10/2026 - bảng document_links, xem docs/architecture/document-links.md. */
public interface DocumentLinkJpaRepository extends JpaRepository<DocumentLink, UUID> {

    @Query("SELECT l FROM DocumentLink l WHERE l.sourceId = :sourceId AND l.targetId = :targetId"
        + " AND l.linkType = :linkType AND l.deletedOn IS NULL")
    Optional<DocumentLink> findActive(
        @Param("sourceId") UUID sourceId, @Param("targetId") UUID targetId, @Param("linkType") String linkType);

    @Query("SELECT l FROM DocumentLink l WHERE l.sourceId = :sourceId AND l.linkType IN :linkTypes"
        + " AND l.deletedOn IS NULL ORDER BY l.createdOn DESC")
    List<DocumentLink> findActiveBySource(
        @Param("sourceId") UUID sourceId, @Param("linkTypes") Collection<String> linkTypes);

    @Query("SELECT l FROM DocumentLink l WHERE l.targetId = :targetId AND l.linkType IN :linkTypes"
        + " AND l.deletedOn IS NULL ORDER BY l.createdOn DESC")
    List<DocumentLink> findActiveByTarget(
        @Param("targetId") UUID targetId, @Param("linkTypes") Collection<String> linkTypes);
}
