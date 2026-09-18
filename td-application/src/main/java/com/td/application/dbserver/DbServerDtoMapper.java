package com.td.application.dbserver;

import com.td.domain.dbserver.DbServer;

final class DbServerDtoMapper {

    private DbServerDtoMapper() {}

    static DbServerDto map(DbServer e) {
        var dto = new DbServerDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setHost(e.getHost());
        dto.setPort(e.getPort());
        dto.setDescription(e.getDescription());
        dto.setUsername(e.getUsername());
        dto.setHasPassword(e.getEncryptedPassword() != null);
        dto.setActive(e.isActive());
        dto.setCreatedOn(e.getCreatedOn());
        dto.setLastModifiedOn(e.getLastModifiedOn());
        return dto;
    }
}
