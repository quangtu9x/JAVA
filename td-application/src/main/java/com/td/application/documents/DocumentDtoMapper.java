package com.td.application.documents;

import com.td.domain.documents.BusinessDocument;

final class DocumentDtoMapper {

    private DocumentDtoMapper() {
    }

    static DocumentDto map(BusinessDocument document) {
        var dto = new DocumentDto();
        dto.setId(document.getId());
        dto.setTitle(document.getTitle());
        dto.setDocumentType(document.getDocumentType());
        dto.setStatus(document.getStatus());
        dto.setContent(document.getContent());
        dto.setTags(DocumentJsonMapper.toStringList(document.getTagsJson()));
        dto.setAttributes(DocumentJsonMapper.toMap(document.getAttributesJson()));
        dto.setMetadata(DocumentJsonMapper.toMap(document.getMetadataJson()));
        dto.setVersionNo(document.getVersionNo());
        dto.setCreatedOn(document.getCreatedOn());
        dto.setLastModifiedOn(document.getLastModifiedOn());
        dto.setDeleted(document.isDeleted());
        return dto;
    }
}