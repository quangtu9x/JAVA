package com.td.application.documents;

import com.td.domain.documents.DocumentLog;

final class DocumentLogDtoMapper {

    private DocumentLogDtoMapper() {
    }

    static DocumentLogDto map(DocumentLog documentLog) {
        var dto = new DocumentLogDto();
        dto.setId(documentLog.getId());
        dto.setDocumentId(documentLog.getDocumentId());
        dto.setLogType(documentLog.getLogType());
        dto.setLogData(DocumentJsonMapper.toMap(documentLog.getLogDataJson()));
        dto.setCreatedBy(documentLog.getCreatedBy());
        dto.setCreatedAt(documentLog.getCreatedAt());
        return dto;
    }
}
