package com.td.application.tenant;

import com.td.domain.tenant.Tenant;

final class TenantDtoMapper {

    private TenantDtoMapper() {}

    // KHÔNG map encryptedPassword — xem javadoc TenantDto.
    static TenantDto map(Tenant e) {
        var dto = new TenantDto();
        dto.setId(e.getId());
        dto.setTenantId(e.getTenantId());
        dto.setDisplayName(e.getDisplayName());
        dto.setHost(e.getHost());
        dto.setPort(e.getPort());
        dto.setDatabaseName(e.getDatabaseName());
        dto.setUsername(e.getUsername());
        dto.setDriverClassName(e.getDriverClassName());
        dto.setActive(e.isActive());
        dto.setCreatedOn(e.getCreatedOn());
        dto.setLastModifiedOn(e.getLastModifiedOn());
        return dto;
    }
}
