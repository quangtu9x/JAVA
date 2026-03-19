package com.td.infrastructure.persistence.repository;

import com.td.application.sharedcore.SearchUserDataScopesRequest;
import com.td.application.sharedcore.UserDataScopeRepository;
import com.td.domain.sharedcore.UserDataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresUserDataScopeRepository implements UserDataScopeRepository {

    private final UserDataScopeJpaRepository jpa;

    @Override
    public Optional<UserDataScope> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<UserDataScope, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<UserDataScope> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public <S extends UserDataScope> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(UserDataScope entity) {
        jpa.delete(entity);
    }

    @Override
    public Page<UserDataScope> search(SearchUserDataScopesRequest request, Pageable pageable) {
        Specification<UserDataScope> spec = Specification
            .where(notDeleted())
            .and(userFilter(request))
            .and(moduleFilter(request))
            .and(typeFilter(request))
            .and(orgFilter(request))
            .and(activeFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<UserDataScope> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<UserDataScope> userFilter(SearchUserDataScopesRequest req) {
        if (req.getUserId() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("userId"), req.getUserId());
    }

    private static Specification<UserDataScope> moduleFilter(SearchUserDataScopesRequest req) {
        if (req.getScopeModule() == null || req.getScopeModule().isBlank()) {
            return null;
        }
        String module = req.getScopeModule().trim().toLowerCase();
        return (root, q, cb) -> cb.equal(cb.lower(root.get("scopeModule")), module);
    }

    private static Specification<UserDataScope> typeFilter(SearchUserDataScopesRequest req) {
        if (req.getScopeType() == null || req.getScopeType().isBlank()) {
            return null;
        }
        String type = req.getScopeType().trim().toLowerCase();
        return (root, q, cb) -> cb.equal(cb.lower(root.get("scopeType")), type);
    }

    private static Specification<UserDataScope> orgFilter(SearchUserDataScopesRequest req) {
        if (req.getScopeOrgId() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("scopeOrgId"), req.getScopeOrgId());
    }

    private static Specification<UserDataScope> activeFilter(SearchUserDataScopesRequest req) {
        if (req.getIsActive() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("isActive"), req.getIsActive());
    }
}
