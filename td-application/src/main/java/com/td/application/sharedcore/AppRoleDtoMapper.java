package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppRole;

import java.util.Map;

final class AppRoleDtoMapper {

    private AppRoleDtoMapper() {
    }

    // dynamicFields do caller (UseCase, có DynamicDocumentFieldsService) tự parse rồi truyền vào —
    // giữ mapper này thuần static/không cần Spring DI, cùng convention với OrganizationDtoMapper.
    static AppRoleDto map(AppRole entity, Map<String, Object> dynamicFields) {
        var dto = new AppRoleDto();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setSystemRole(entity.isSystemRole());
        dto.setActive(entity.isActive());
        dto.setSortOrder(entity.getSortOrder());
        dto.setCreatedOn(entity.getCreatedOn());
        dto.setLastModifiedOn(entity.getLastModifiedOn());
        dto.setDynamicFields(dynamicFields == null ? Map.of() : dynamicFields);
        return dto;
    }
}
