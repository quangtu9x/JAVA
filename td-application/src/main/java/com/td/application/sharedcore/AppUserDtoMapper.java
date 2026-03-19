package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppUser;

final class AppUserDtoMapper {

    private AppUserDtoMapper() {
    }

    static AppUserDto map(AppUser entity) {
        var dto = new AppUserDto();
        dto.setId(entity.getId());
        dto.setKeycloakSubject(entity.getKeycloakSubject());
        dto.setUsername(entity.getUsername());
        dto.setFullName(entity.getFullName());
        dto.setEmail(entity.getEmail());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setPositionId(entity.getPositionId());
        dto.setActive(entity.isActive());
        dto.setCreatedOn(entity.getCreatedOn());
        dto.setLastModifiedOn(entity.getLastModifiedOn());
        return dto;
    }
}
