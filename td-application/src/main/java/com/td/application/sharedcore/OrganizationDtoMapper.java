package com.td.application.sharedcore;

import com.td.domain.sharedcore.Organization;

final class OrganizationDtoMapper {

    private OrganizationDtoMapper() {
    }

    static OrganizationDto map(Organization entity) {
        var dto = new OrganizationDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setParentId(entity.getParentId());
        dto.setLevel(entity.getLevel());
        dto.setFullPath(entity.getFullPath());
        dto.setSortOrder(entity.getSortOrder());
        dto.setActive(entity.isActive());
        dto.setCreatedOn(entity.getCreatedOn());
        dto.setLastModifiedOn(entity.getLastModifiedOn());
        return dto;
    }
}
