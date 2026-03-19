package com.td.infrastructure.persistence.repository;

import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.SearchAppUsersRequest;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresAppUserRepository implements AppUserRepository {

    private final AppUserJpaRepository jpa;

    @Override
    public Optional<AppUser> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<AppUser, UUID>) jpa).findById(id);
    }

    @Override
    public Optional<AppUser> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public Optional<AppUser> findByKeycloakSubjectAndDeletedOnIsNull(String keycloakSubject) {
        return jpa.findByKeycloakSubjectAndDeletedOnIsNull(keycloakSubject);
    }

    @Override
    public <S extends AppUser> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(AppUser entity) {
        jpa.delete(entity);
    }

    @Override
    public boolean existsByUsernameAndDeletedOnIsNull(String username) {
        return jpa.existsByUsernameAndDeletedOnIsNull(username);
    }

    @Override
    public boolean existsByUsernameAndIdNotAndDeletedOnIsNull(String username, UUID id) {
        return jpa.existsByUsernameAndIdNotAndDeletedOnIsNull(username, id);
    }

    @Override
    public boolean existsByKeycloakSubjectAndDeletedOnIsNull(String keycloakSubject) {
        return jpa.existsByKeycloakSubjectAndDeletedOnIsNull(keycloakSubject);
    }

    @Override
    public Page<AppUser> search(SearchAppUsersRequest request, Pageable pageable) {
        Specification<AppUser> spec = Specification
            .where(notDeleted())
            .and(keywordFilter(request))
            .and(organizationFilter(request))
            .and(activeFilter(request));
        return jpa.findAll(spec, pageable);
    }

    private static Specification<AppUser> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedOn"));
    }

    private static Specification<AppUser> keywordFilter(SearchAppUsersRequest req) {
        if (req.getKeyword() == null || req.getKeyword().isBlank()) {
            return null;
        }
        String kw = "%" + req.getKeyword().trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
            cb.like(cb.lower(root.get("username")), kw),
            cb.like(cb.lower(root.get("fullName")), kw),
            cb.like(cb.lower(root.get("email")), kw)
        );
    }

    private static Specification<AppUser> organizationFilter(SearchAppUsersRequest req) {
        if (req.getOrganizationId() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("organizationId"), req.getOrganizationId());
    }

    private static Specification<AppUser> activeFilter(SearchAppUsersRequest req) {
        if (req.getIsActive() == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("isActive"), req.getIsActive());
    }
}
