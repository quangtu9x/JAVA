package com.td.application.recordsstorage;

import com.td.domain.recordsstorage.RecordsStorageCatalog;

final class RecordsStorageCatalogDtoMapper {

    private RecordsStorageCatalogDtoMapper() {}

    static RecordsStorageCatalogDto map(RecordsStorageCatalog e) {
        var dto = new RecordsStorageCatalogDto();
        dto.setId(e.getId());
        dto.setCategory(e.getCategory());
        dto.setCode(e.getCode());
        dto.setPhong(e.getPhong());
        dto.setPhongId(e.getPhongId());
        dto.setFirstTopic(e.getFirstTopic());
        dto.setFirstTopicId(e.getFirstTopicId());
        dto.setSecondTopic(e.getSecondTopic());
        dto.setSecondTopicId(e.getSecondTopicId());
        dto.setStoragePeriod(e.getStoragePeriod());
        dto.setYearInception(e.getYearInception());
        dto.setSortOrder(e.getSortOrder());
        dto.setNote(e.getNote());
        dto.setNumberOfDocumentNumber(e.getNumberOfDocumentNumber());
        dto.setKyhieu(e.getKyhieu());
        dto.setInternalUnitObj(e.getInternalUnitObj());
        dto.setIndividualObj(e.getIndividualObj());
        dto.setIdentifier(e.getIdentifier());
        dto.setCreatedOn(e.getCreatedOn());
        dto.setLastModifiedOn(e.getLastModifiedOn());
        return dto;
    }
}
