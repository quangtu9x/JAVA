package template.department.application;

import template.department.domain.Department;

final class DepartmentDtoMapper {

    private DepartmentDtoMapper() {
    }

    static DepartmentDto map(Department department) {
        var dto = new DepartmentDto();
        dto.setId(department.getId());
        dto.setCode(department.getCode());
        dto.setName(department.getName());
        dto.setDescription(department.getDescription());
        dto.setParentId(department.getParentId());
        dto.setLevel(department.getLevel());
        dto.setFullPath(department.getFullPath());
        dto.setSortOrder(department.getSortOrder());
        dto.setActive(department.isActive());
        dto.setCreatedOn(department.getCreatedOn());
        dto.setLastModifiedOn(department.getLastModifiedOn());
        return dto;
    }
}