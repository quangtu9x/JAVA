package com.td.infrastructure.persistence.repository;

import com.td.application.sharedcore.OrganizationRepository;
import com.td.application.sharedcore.SearchOrganizationsRequest;
import com.td.domain.sharedcore.Organization;
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
public class PostgresOrganizationRepository implements OrganizationRepository {

    private final OrganizationJpaRepository jpa;

    @Override
    public Optional<Organization> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<Organization, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<Organization> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public List<Organization> findByParentIdAndDeletedOnIsNull(UUID parentId) {
        return jpa.findByParentIdAndDeletedOnIsNull(parentId);
    }

    @Override
    public <S extends Organization> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(Organization entity) {
        jpa.delete(entity);
    }

    @Override
    public boolean existsByIdentifierAndDeletedOnIsNull(String identifier) {
        return jpa.existsByIdentifierAndDeletedOnIsNull(identifier);
    }

    @Override
    public boolean existsByIdentifierAndIdNotAndDeletedOnIsNull(String identifier, UUID id) {
        return jpa.existsByIdentifierAndIdNotAndDeletedOnIsNull(identifier, id);
    }

    @Override
    public Page<Organization> search(SearchOrganizationsRequest request, Pageable pageable) {
        Specification<Organization> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(parentFilter(request))
            .and(levelFilter(request))
            .and(activeFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<Organization> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<Organization> keywordFilter(SearchOrganizationsRequest req) {
        if (req.getKeyword() == null || req.getKeyword().isBlank()) {
            return null;
        }
        String kw = "%" + req.getKeyword().trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
            cb.like(cb.lower(root.get("name")), kw),
            cb.like(cb.lower(root.get("identifier")), kw),
            cb.like(cb.lower(root.get("description")), kw),
            cb.like(cb.lower(root.get("fullPath")), kw)
        );
    }

    private static Specification<Organization> parentFilter(SearchOrganizationsRequest req) {
        if (req.getParentId() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("parentId"), req.getParentId());
    }

    private static Specification<Organization> levelFilter(SearchOrganizationsRequest req) {
        if (req.getLevel() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("level"), req.getLevel());
    }

    private static Specification<Organization> activeFilter(SearchOrganizationsRequest req) {
        if (req.getIsActive() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("isActive"), req.getIsActive());
    }
}
