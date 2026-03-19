package com.td.application.sharedcore;

import com.td.domain.sharedcore.UserDataScope;

final class UserDataScopeDtoMapper {

    private UserDataScopeDtoMapper() {
    }

    static UserDataScopeDto map(UserDataScope entity) {
        var dto = new UserDataScopeDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setScopeModule(entity.getScopeModule());
        dto.setScopeType(entity.getScopeType());
        dto.setScopeOrgId(entity.getScopeOrgId());
        dto.setScopeValue(entity.getScopeValue());
        dto.setActive(entity.isActive());
        dto.setCreatedOn(entity.getCreatedOn());
        dto.setLastModifiedOn(entity.getLastModifiedOn());
        return dto;
    }
}
