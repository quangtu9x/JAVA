package com.td.infrastructure.persistence.identity;

import com.td.application.sharedcore.RoleGroupRoleRepository;
import com.td.domain.sharedcore.RoleGroupRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresRoleGroupRoleRepository implements RoleGroupRoleRepository {

    private final RoleGroupRoleJpaRepository jpa;

    @Override
    public Optional<RoleGroupRole> findById(UUID id) {
        return ((org.springframework.data.jpa.repository.JpaRepository<RoleGroupRole, UUID>) jpa).findById(id);
    }

    @Override
    public <S extends RoleGroupRole> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public void delete(RoleGroupRole entity) {
        jpa.delete(entity);
    }

    @Override
    public List<RoleGroupRole> findByRoleGroupIdAndDeletedOnIsNull(UUID roleGroupId) {
        return jpa.findByRoleGroupIdAndDeletedOnIsNull(roleGroupId);
    }

    @Override
    public List<RoleGroupRole> findByRoleGroupIdInAndDeletedOnIsNull(List<UUID> roleGroupIds) {
        return jpa.findByRoleGroupIdInAndDeletedOnIsNull(roleGroupIds);
    }
}
