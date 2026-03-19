package com.td.application.categories;

import com.td.domain.categories.Category;

final class CategoryDtoMapper {

    private CategoryDtoMapper() {}

    static CategoryDto map(Category c) {
        var dto = new CategoryDto();
        dto.setId(c.getId());
        dto.setCode(c.getCode());
        dto.setName(c.getName());
        dto.setDescription(c.getDescription());
        dto.setParentId(c.getParentId());
        dto.setLevel(c.getLevel());
        dto.setFullPath(c.getFullPath());
        dto.setSortOrder(c.getSortOrder());
        dto.setActive(c.isActive());
        dto.setCreatedOn(c.getCreatedOn());
        dto.setLastModifiedOn(c.getLastModifiedOn());
        return dto;
    }
}
