package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.RoleGroupPermission;

import java.util.List;
import java.util.UUID;

// Bảng nối N:N RoleGroup ↔ AppPermission — cùng vai trò với RoleGroupRoleRepository, chỉ khác
// bảng đích.
public interface RoleGroupPermissionRepository extends IRepository<RoleGroupPermission> {

    List<RoleGroupPermission> findByRoleGroupIdAndDeletedOnIsNull(UUID roleGroupId);

    List<RoleGroupPermission> findByRoleGroupIdInAndDeletedOnIsNull(List<UUID> roleGroupIds);
}
