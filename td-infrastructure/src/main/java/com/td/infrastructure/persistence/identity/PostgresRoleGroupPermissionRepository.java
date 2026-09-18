package com.td.infrastructure.persistence.identity;

import com.td.application.sharedcore.RoleGroupPermissionRepository;
import com.td.domain.sharedcore.RoleGroupPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresRoleGroupPermissionRepository implements RoleGroupPermissionRepository {

    private final RoleGroupPermissionJpaRepository jpa;

    @Override
    public Optional<RoleGroupPermission> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<RoleGroupPermission, UUID>) jpa).findById(id);
    }

    @Override
    public <S extends RoleGroupPermission> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(RoleGroupPermission entity) {
        jpa.delete(entity);
    }

    @Override
    public List<RoleGroupPermission> findByRoleGroupIdAndDeletedOnIsNull(UUID roleGroupId) {
        return jpa.findByRoleGroupIdAndDeletedOnIsNull(roleGroupId);
    }

    @Override
    public List<RoleGroupPermission> findByRoleGroupIdInAndDeletedOnIsNull(List<UUID> roleGroupIds) {
        return jpa.findByRoleGroupIdInAndDeletedOnIsNull(roleGroupIds);
    }
}
