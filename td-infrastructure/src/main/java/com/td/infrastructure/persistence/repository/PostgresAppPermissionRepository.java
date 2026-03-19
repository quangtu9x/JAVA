package com.td.infrastructure.persistence.repository;

import com.td.application.sharedcore.AppPermissionRepository;
import com.td.application.sharedcore.SearchAppPermissionsRequest;
import com.td.domain.sharedcore.AppPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresAppPermissionRepository implements AppPermissionRepository {

    private final AppPermissionJpaRepository jpa;

    @Override
    public Optional<AppPermission> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<AppPermission, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<AppPermission> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public <S extends AppPermission> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(AppPermission entity) {
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
    public Page<AppPermission> search(SearchAppPermissionsRequest request, Pageable pageable) {
        Specification<AppPermission> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(moduleFilter(request))
            .and(activeFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<AppPermission> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<AppPermission> keywordFilter(SearchAppPermissionsRequest req) {
        if (req.getKeyword() == null || req.getKeyword().isBlank()) {
            return null;
        }
        String kw = "%" + req.getKeyword().trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
            cb.like(cb.lower(root.get("name")), kw),
            cb.like(cb.lower(root.get("code")), kw),
            cb.like(cb.lower(root.get("description")), kw)
        );
    }

    private static Specification<AppPermission> moduleFilter(SearchAppPermissionsRequest req) {
        if (req.getModuleKey() == null || req.getModuleKey().isBlank()) {
            return null;
        }
        String module = req.getModuleKey().trim().toLowerCase();
        return (root, q, cb) -> cb.equal(cb.lower(root.get("moduleKey")), module);
    }

    private static Specification<AppPermission> activeFilter(SearchAppPermissionsRequest req) {
        if (req.getIsActive() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("isActive"), req.getIsActive());
    }
}
