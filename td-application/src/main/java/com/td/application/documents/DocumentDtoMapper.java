package com.td.application.documents;

import com.td.domain.documents.BusinessDocument;
import java.util.Map;

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
        Map<String, Object> customFields = DocumentJsonMapper.toMap(document.getAttributesJson());
        Map<String, Object> storedMetadata = DocumentJsonMapper.toMap(document.getMetadataJson());
        DocumentFieldLayout.ResponseProjection responseProjection =
            DocumentFieldLayout.splitForResponse(customFields, storedMetadata);
        dto.setAttributes(responseProjection.attributes());
        dto.setExtraFields(responseProjection.topLevelFields());
        dto.setMetadata(responseProjection.metadata());
        dto.setVersionNo(document.getVersionNo());
        dto.setCreatedOn(document.getCreatedOn());
        dto.setLastModifiedOn(document.getLastModifiedOn());
        dto.setDeleted(document.isDeleted());
        return dto;
    }
}