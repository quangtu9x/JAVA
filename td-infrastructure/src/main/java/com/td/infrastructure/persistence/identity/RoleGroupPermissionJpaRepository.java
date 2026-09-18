package com.td.infrastructure.persistence.identity;

import com.td.infrastructure.persistence.repository.BaseRepository;

import com.td.domain.sharedcore.RoleGroupPermission;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RoleGroupPermissionJpaRepository extends BaseRepository<RoleGroupPermission> {

    @Query("SELECT l FROM RoleGroupPermission l WHERE l.roleGroupId = :roleGroupId AND l.deletedOn IS NULL")
    List<RoleGroupPermission> findByRoleGroupIdAndDeletedOnIsNull(@Param("roleGroupId") UUID roleGroupId);

    @Query("SELECT l FROM RoleGroupPermission l WHERE l.roleGroupId IN :roleGroupIds AND l.deletedOn IS NULL")
    List<RoleGroupPermission> findByRoleGroupIdInAndDeletedOnIsNull(@Param("roleGroupIds") List<UUID> roleGroupIds);
}
