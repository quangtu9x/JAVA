package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppPermission;

final class AppPermissionDtoMapper {

    private AppPermissionDtoMapper() {
    }

    static AppPermissionDto map(AppPermission entity) {
        var dto = new AppPermissionDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setModuleKey(entity.getModuleKey());
        dto.setDescription(entity.getDescription());
        dto.setActive(entity.isActive());
        dto.setCreatedOn(entity.getCreatedOn());
        dto.setLastModifiedOn(entity.getLastModifiedOn());
        return dto;
    }
}
