package com.td.infrastructure.persistence.identity;

import com.td.application.sharedcore.AppUserRoleGroupRepository;
import com.td.domain.sharedcore.AppUserRoleGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresAppUserRoleGroupRepository implements AppUserRoleGroupRepository {

    private final AppUserRoleGroupJpaRepository jpa;

    @Override
    public Optional<AppUserRoleGroup> findById(UUID id) {
        return ((JpaRepository<AppUserRoleGroup, UUID>) jpa).findById(id);
    }

    @Override
    public <S extends AppUserRoleGroup> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(AppUserRoleGroup entity) {
        jpa.delete(entity);
    }

    @Override
    public List<AppUserRoleGroup> findByAppUserIdAndDeletedOnIsNull(UUID appUserId) {
        return jpa.findByAppUserIdAndDeletedOnIsNull(appUserId);
    }

    @Override
    public List<AppUserRoleGroup> findByAppUserIdInAndDeletedOnIsNull(List<UUID> appUserIds) {
        return jpa.findByAppUserIdInAndDeletedOnIsNull(appUserIds);
    }
}
