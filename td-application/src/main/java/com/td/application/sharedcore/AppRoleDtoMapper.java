package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppRole;

final class AppRoleDtoMapper {

    private AppRoleDtoMapper() {
    }

    static AppRoleDto map(AppRole entity) {
        var dto = new AppRoleDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setSystemRole(entity.isSystemRole());
        dto.setActive(entity.isActive());
        dto.setCreatedOn(entity.getCreatedOn());
        dto.setLastModifiedOn(entity.getLastModifiedOn());
        return dto;
    }
}
