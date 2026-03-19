package com.td.infrastructure.persistence.repository;

import com.td.application.categories.CategoryRepository;
import com.td.application.categories.SearchCategoriesRequest;
import com.td.domain.categories.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresCategoryRepository implements CategoryRepository {

    private final CategoryJpaRepository jpa;

    @Override
    public Optional<Category> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<Category, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<Category> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public <S extends Category> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(Category entity) {
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
    public Page<Category> search(SearchCategoriesRequest request, Pageable pageable) {
        Specification<Category> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(parentFilter(request))
            .and(levelFilter(request))
            .and(activeFilter(request));
        return jpa.findAll(spec, pageable);
    }

    // ── Specifications ────────────────────────────────────────────

    private static Specification<Category> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<Category> keywordFilter(SearchCategoriesRequest req) {
        if (req.getKeyword() == null || req.getKeyword().isBlank()) return null;
        String kw = "%" + req.getKeyword().trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
            cb.like(cb.lower(root.get("name")),        kw),
            cb.like(cb.lower(root.get("code")),        kw),
            cb.like(cb.lower(root.get("description")), kw),
            cb.like(cb.lower(root.get("fullPath")),    kw)
        );
    }

    private static Specification<Category> parentFilter(SearchCategoriesRequest req) {
        if (req.getParentId() == null) return null;
        return (root, q, cb) -> cb.equal(root.get("parentId"), req.getParentId());
    }

    private static Specification<Category> levelFilter(SearchCategoriesRequest req) {
        if (req.getLevel() == null) return null;
        return (root, q, cb) -> cb.equal(root.get("level"), req.getLevel());
    }

    private static Specification<Category> activeFilter(SearchCategoriesRequest req) {
        if (req.getIsActive() == null) return null;
        return (root, q, cb) -> cb.equal(root.get("isActive"), req.getIsActive());
    }
}
