package com.td.infrastructure.persistence.repository;

import com.td.application.recordsstorage.RecordsStorageCatalogRepository;
import com.td.application.recordsstorage.SearchRecordsStorageCatalogsRequest;
import com.td.domain.recordsstorage.RecordsStorageCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresRecordsStorageCatalogRepository implements RecordsStorageCatalogRepository {

    private final RecordsStorageCatalogJpaRepository jpa;

    @Override
    public Optional<RecordsStorageCatalog> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<RecordsStorageCatalog, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<RecordsStorageCatalog> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public <S extends RecordsStorageCatalog> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(RecordsStorageCatalog entity) {
        jpa.delete(entity);
    }

    @Override
    public Page<RecordsStorageCatalog> search(SearchRecordsStorageCatalogsRequest request, Pageable pageable) {
        Specification<RecordsStorageCatalog> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(identifierFilter(request))
            .and(selectedNodeFilter(request))
            .and(yearFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<RecordsStorageCatalog> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<RecordsStorageCatalog> keywordFilter(SearchRecordsStorageCatalogsRequest req) {
        if (req.getKeyword() == null || req.getKeyword().isBlank()) return null;
        String kw = "%" + req.getKeyword().trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
            cb.like(cb.lower(root.get("category")), kw),
            cb.like(cb.lower(root.get("code")), kw),
            cb.like(cb.lower(root.get("firstTopic")), kw),
            cb.like(cb.lower(root.get("secondTopic")), kw),
            cb.like(cb.lower(root.get("storagePeriod")), kw),
            cb.like(cb.lower(root.get("note")), kw)
        );
    }

    private static Specification<RecordsStorageCatalog> identifierFilter(SearchRecordsStorageCatalogsRequest req) {
        if (req.getIdentifier() == null || req.getIdentifier().isBlank()) return null;
        return (root, q, cb) -> cb.equal(root.get("identifier"), req.getIdentifier());
    }

    private static Specification<RecordsStorageCatalog> selectedNodeFilter(SearchRecordsStorageCatalogsRequest req) {
        if (req.getSelectedNodeId() == null) return null;
        return (root, q, cb) -> cb.or(
            cb.equal(root.get("phongId"), req.getSelectedNodeId()),
            cb.equal(root.get("firstTopicId"), req.getSelectedNodeId()),
            cb.equal(root.get("secondTopicId"), req.getSelectedNodeId())
        );
    }

    private static Specification<RecordsStorageCatalog> yearFilter(SearchRecordsStorageCatalogsRequest req) {
        if (req.getYearInception() == null) return null;
        return (root, q, cb) -> cb.equal(root.get("yearInception"), req.getYearInception());
    }
}
