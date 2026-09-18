package com.td.application.privatecatalog;

import com.td.domain.privatecatalog.PrivateCategory;

import java.util.Map;

final class PrivateCategoryDtoMapper {

    private PrivateCategoryDtoMapper() {}

    static PrivateCategoryDto map(PrivateCategory c, Map<String, Object> dynamicFields) {
        var dto = new PrivateCategoryDto();
        dto.setId(c.getId());
        dto.setCode(c.getCode());
        dto.setName(c.getName());
        dto.setDescription(c.getDescription());
        dto.setForm(c.getForm());
        dto.setParentId(c.getParentId());
        dto.setLevel(c.getLevel());
        dto.setFullPath(c.getFullPath());
        dto.setSortOrder(c.getSortOrder());
        dto.setActive(c.isActive());
        dto.setIdentifier(c.getIdentifier());
        dto.setCreatedOn(c.getCreatedOn());
        dto.setLastModifiedOn(c.getLastModifiedOn());
        dto.setDynamicFields(dynamicFields == null ? Map.of() : dynamicFields);
        return dto;
    }
}
