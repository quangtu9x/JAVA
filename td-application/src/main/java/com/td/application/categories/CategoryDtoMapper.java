package com.td.application.categories;

import com.td.domain.categories.Category;

import java.util.Map;

final class CategoryDtoMapper {

    private CategoryDtoMapper() {}

    // dynamicFields do caller (UseCase, có DynamicDocumentFieldsService) tự parse rồi truyền vào —
    // giữ mapper này thuần static/không cần Spring DI, cùng convention với OrganizationDtoMapper.
    static CategoryDto map(Category c, Map<String, Object> dynamicFields) {
        var dto = new CategoryDto();
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
        dto.setCreatedOn(c.getCreatedOn());
        dto.setLastModifiedOn(c.getLastModifiedOn());
        dto.setDynamicFields(dynamicFields == null ? Map.of() : dynamicFields);
        return dto;
    }
}
