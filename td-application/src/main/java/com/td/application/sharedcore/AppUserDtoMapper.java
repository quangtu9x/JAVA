package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppUser;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AppUserDtoMapper {

    private AppUserDtoMapper() {
    }

    // dynamicFields do caller (UseCase, có DynamicDocumentFieldsService) tự parse rồi truyền vào,
    // roleGroupIds do caller (có AppUserRoleGroupRepository) tự nạp rồi truyền vào — giữ mapper này
    // thuần static/không cần Spring DI, cùng convention với OrganizationDtoMapper/RoleGroupDtoMapper.
    static AppUserDto map(AppUser entity, Map<String, Object> dynamicFields, List<UUID> roleGroupIds,
                          boolean hasKiemNhiem) {
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
        dto.setLevel(entity.getLevel());
        dto.setSortOrder(entity.getSortOrder());
        dto.setPhoneNumber(entity.getPhoneNumber());
        dto.setGender(entity.getGender());
        dto.setBirthdayDate(entity.getBirthdayDate());
        dto.setAddress(entity.getAddress());
        dto.setUserIdNo(entity.getUserIdNo());
        dto.setCapLanhDao(entity.getCapLanhDao());
        dto.setIdentifier(entity.getIdentifier());
        dto.setSim(entity.isSim());
        dto.setParentUserId(entity.getParentUserId());
        dto.setHasKiemNhiem(hasKiemNhiem);
        dto.setRoleGroupIds(roleGroupIds == null ? List.of() : roleGroupIds);
        dto.setDynamicFields(dynamicFields == null ? Map.of() : dynamicFields);
        return dto;
    }
}
