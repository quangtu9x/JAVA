package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppPermission;
import com.td.domain.sharedcore.AppRole;
import com.td.domain.sharedcore.RoleGroup;

import java.util.List;

final class RoleGroupDtoMapper {

    private RoleGroupDtoMapper() {
    }

    // roles/permissions do caller (UseCase, có RoleGroupRoleRepository/RoleGroupPermissionRepository
    // + AppRoleRepository/AppPermissionRepository) tự nạp rồi truyền vào — giữ mapper này thuần
    // static/không cần Spring DI, cùng convention với OrganizationDtoMapper.
    static RoleGroupDto map(RoleGroup entity, List<AppRole> roles, List<AppPermission> permissions) {
        var dto = new RoleGroupDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setSortOrder(entity.getSortOrder());
        dto.setFullAccess(entity.isFullAccess());
        dto.setActive(entity.isActive());
        dto.setCreatedOn(entity.getCreatedOn());
        dto.setLastModifiedOn(entity.getLastModifiedOn());
        dto.setRoleIds(roles.stream().map(AppRole::getId).toList());
        dto.setRoleNames(roles.stream().map(AppRole::getName).toList());
        dto.setPermissionIds(permissions.stream().map(AppPermission::getId).toList());
        dto.setPermissionCodes(permissions.stream().map(AppPermission::getCode).toList());
        return dto;
    }
}
