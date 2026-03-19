package com.td.infrastructure.persistence.repository;

import com.td.application.sharedcore.AppRoleRepository;
import com.td.application.sharedcore.SearchAppRolesRequest;
import com.td.domain.sharedcore.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresAppRoleRepository implements AppRoleRepository {

    private final AppRoleJpaRepository jpa;

    @Override
    public Optional<AppRole> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<AppRole, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<AppRole> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public <S extends AppRole> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(AppRole entity) {
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
    public Page<AppRole> search(SearchAppRolesRequest request, Pageable pageable) {
        Specification<AppRole> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(systemRoleFilter(request))
            .and(activeFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<AppRole> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<AppRole> keywordFilter(SearchAppRolesRequest req) {
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

    private static Specification<AppRole> systemRoleFilter(SearchAppRolesRequest req) {
        if (req.getIsSystemRole() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("isSystemRole"), req.getIsSystemRole());
    }

    private static Specification<AppRole> activeFilter(SearchAppRolesRequest req) {
        if (req.getIsActive() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("isActive"), req.getIsActive());
    }
}
