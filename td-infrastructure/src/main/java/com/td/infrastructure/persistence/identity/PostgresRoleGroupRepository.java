package com.td.infrastructure.persistence.identity;

import com.td.application.sharedcore.RoleGroupRepository;
import com.td.application.sharedcore.SearchRoleGroupsRequest;
import com.td.domain.sharedcore.RoleGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresRoleGroupRepository implements RoleGroupRepository {

    private final RoleGroupJpaRepository jpa;

    @Override
    public Optional<RoleGroup> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<RoleGroup, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<RoleGroup> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public java.util.List<RoleGroup> findAllByIdInAndDeletedOnIsNull(java.util.Collection<UUID> ids) {
        return (ids == null || ids.isEmpty()) ? java.util.List.of() : jpa.findAllByIdInAndDeletedOnIsNull(ids);
    }

    @Override
    public <S extends RoleGroup> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(RoleGroup entity) {
        jpa.delete(entity);
    }

    @Override
    public boolean existsByNameAndDeletedOnIsNull(String name) {
        return jpa.existsByNameAndDeletedOnIsNull(name);
    }

    @Override
    public boolean existsByNameAndIdNotAndDeletedOnIsNull(String name, UUID id) {
        return jpa.existsByNameAndIdNotAndDeletedOnIsNull(name, id);
    }

    @Override
    public Page<RoleGroup> search(SearchRoleGroupsRequest request, Pageable pageable) {
        Specification<RoleGroup> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(activeFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<RoleGroup> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<RoleGroup> keywordFilter(SearchRoleGroupsRequest req) {
        if (req.getKeyword() == null || req.getKeyword().isBlank()) {
            return null;
        }
        String kw = "%" + req.getKeyword().trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("name")), kw);
    }

    private static Specification<RoleGroup> activeFilter(SearchRoleGroupsRequest req) {
        if (req.getIsActive() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("isActive"), req.getIsActive());
    }
}
