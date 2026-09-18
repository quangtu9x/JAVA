package com.td.application.numbering;

import com.td.domain.numbering.NumberingConfig;

final class NumberingConfigDtoMapper {

    private NumberingConfigDtoMapper() {
    }

    static NumberingConfigDto map(NumberingConfig config) {
        var dto = new NumberingConfigDto();
        dto.setId(config.getId());
        dto.setDocumentType(config.getDocumentType());
        dto.setFormat(config.getFormat());
        dto.setResetPolicy(config.getResetPolicy());
        dto.setActive(config.isActive());
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }
}
