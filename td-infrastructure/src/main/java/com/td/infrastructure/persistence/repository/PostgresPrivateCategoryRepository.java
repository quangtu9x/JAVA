package com.td.infrastructure.persistence.repository;

import com.td.application.privatecatalog.PrivateCategoryRepository;
import com.td.application.privatecatalog.SearchPrivateCategoriesRequest;
import com.td.domain.privatecatalog.PrivateCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresPrivateCategoryRepository implements PrivateCategoryRepository {

    private final PrivateCategoryJpaRepository jpa;

    @Override
    public Optional<PrivateCategory> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<PrivateCategory, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<PrivateCategory> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public <S extends PrivateCategory> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(PrivateCategory entity) {
        jpa.delete(entity);
    }

    @Override
    public boolean existsByCodeAndDeletedOnIsNull(String code) {
        return jpa.existsByCodeAndDeletedOnIsNull(code);
    }

    @Override
    public boolean existsByCodeAndIdNotAndDeletedOnIsNull(String code, UUID id) {
        return jpa.existsByCodeAndIdNotAndDeletedOnIsNull(code, id);
    }

    @Override
    public List<PrivateCategory> findByFormAndDonViSuDungIdentifier(String form, String identifier) {
        return jpa.findByFormAndDonViSuDungIdentifier(form, identifier);
    }

    @Override
    public List<PrivateCategory> findByFormAndTypeAndIdentifier(String form, String type, String identifier) {
        return jpa.findByFormAndTypeAndIdentifier(form, type, identifier);
    }

    @Override
    public Page<PrivateCategory> search(SearchPrivateCategoriesRequest request, Pageable pageable) {
        Specification<PrivateCategory> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(parentFilter(request))
            .and(levelFilter(request))
            .and(activeFilter(request))
            .and(formsFilter(request))
            .and(identifierFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<PrivateCategory> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<PrivateCategory> keywordFilter(SearchPrivateCategoriesRequest req) {
        if (req.getKeyword() == null || req.getKeyword().isBlank()) return null;
        String kw = SearchTextSql.containsLike(req.getKeyword()); // 20/10/2026: không phân biệt dấu (td_search_norm)
        return (root, q, cb) -> cb.or(
            cb.like(SearchTextSql.norm(cb, root.get("name")),        kw),
            cb.like(SearchTextSql.norm(cb, root.get("code")),        kw),
            cb.like(SearchTextSql.norm(cb, root.get("description")), kw),
            cb.like(SearchTextSql.norm(cb, root.get("fullPath")),    kw),
            cb.like(SearchTextSql.norm(cb, root.get("form")),        kw)
        );
    }

    private static Specification<PrivateCategory> parentFilter(SearchPrivateCategoriesRequest req) {
        if (req.getParentId() == null) return null;
        return (root, q, cb) -> cb.equal(root.get("parentId"), req.getParentId());
    }

    private static Specification<PrivateCategory> levelFilter(SearchPrivateCategoriesRequest req) {
        if (req.getLevel() == null) return null;
        return (root, q, cb) -> cb.equal(root.get("level"), req.getLevel());
    }

    private static Specification<PrivateCategory> activeFilter(SearchPrivateCategoriesRequest req) {
        if (req.getIsActive() == null) return null;
        return (root, q, cb) -> cb.equal(root.get("isActive"), req.getIsActive());
    }

    private static Specification<PrivateCategory> formsFilter(SearchPrivateCategoriesRequest req) {
        if (req.getForms() == null || req.getForms().isEmpty()) return null;
        return (root, q, cb) -> root.get("form").in(req.getForms());
    }

    private static Specification<PrivateCategory> identifierFilter(SearchPrivateCategoriesRequest req) {
        if (req.getIdentifier() == null || req.getIdentifier().isBlank()) return null;
        return (root, q, cb) -> cb.equal(root.get("identifier"), req.getIdentifier());
    }
}
