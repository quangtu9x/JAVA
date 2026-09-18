package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.AppUserRoleGroup;

import java.util.List;
import java.util.UUID;

// Bảng nối N:N AppUser <-> RoleGroup — cùng vai trò với RoleGroupRoleRepository/
// RoleGroupPermissionRepository, chỉ khác bảng đích (bổ sung 19/08/2026, xem AppUserRoleGroup.java).
public interface AppUserRoleGroupRepository extends IRepository<AppUserRoleGroup> {

    List<AppUserRoleGroup> findByAppUserIdAndDeletedOnIsNull(UUID appUserId);

    // Dùng cho Search/GetAppUsersUseCase enrich nhiều user cùng lúc — tránh N+1 query.
    List<AppUserRoleGroup> findByAppUserIdInAndDeletedOnIsNull(List<UUID> appUserIds);
}
