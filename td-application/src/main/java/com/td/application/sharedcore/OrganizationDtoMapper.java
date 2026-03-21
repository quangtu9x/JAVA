package com.td.application.sharedcore;

import com.td.domain.sharedcore.Organization;

final class OrganizationDtoMapper {

    private OrganizationDtoMapper() {
    }

    static OrganizationDto map(Organization entity) {
        var dto = new OrganizationDto();
        dto.setId(entity.getId());
        dto.setIdentifier(entity.getIdentifier());
        dto.setName(entity.getName());
        dto.setParent(entity.getParent());
        dto.setParentid(entity.getLegacyParentId());
        dto.setForm(entity.getNodeType());
        dto.setSystem(entity.getSystem());
        dto.setReceiver_id(entity.getReceiverId());
        dto.setReceiver(entity.getReceiver());
        dto.setReceiver_position(entity.getReceiverPosition());
        dto.setServername(entity.getServername());
        dto.setServer_id(entity.getServerId());
        dto.setIpserver(entity.getIpserver());
        dto.setDbpath(entity.getDbpath());
        dto.setLevel(entity.getLevel());
        dto.setSort_order(entity.getSortOrder());
        dto.setIs_active(entity.isActive());
        dto.setCreated_on(entity.getCreatedOn());
        dto.setLast_modified_on(entity.getLastModifiedOn());
        return dto;
    }
}
