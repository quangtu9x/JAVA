package com.td.application.documents;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UpdateDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentCacheService documentCacheService;
    private final DocumentSearchService documentSearchService;

    public Result<UUID> execute(UpdateDocumentRequest request) {
        try {
            if (request.getId() == null) {
                return Result.failure("ID tài liệu không được để trống");
            }

            if (request.getTitle() != null && request.getTitle().isBlank()) {
                return Result.failure("Tiêu đề không được để trống");
            }

            var documentOptional = documentRepository.findById(request.getId());
            if (documentOptional.isEmpty()) {
                return Result.failure("Không tìm thấy tài liệu với ID: " + request.getId());
            }

            var document = documentOptional.get();
            if (document.isDeleted()) {
                return Result.failure("Tài liệu đã bị xóa và không thể cập nhật");
            }

            boolean hasBaseFieldChanges = request.getTitle() != null
                || request.getDocumentType() != null
                || request.getStatus() != null
                || request.getContent() != null
                || request.getTags() != null;

            boolean hasCustomFieldChanges = request.getAttributes() != null || request.hasDynamicFields();
            boolean hasMetadataChanges = request.getMetadata() != null;

            if (!hasBaseFieldChanges && !hasCustomFieldChanges && !hasMetadataChanges) {
                return Result.failure("Không có dữ liệu cập nhật");
            }

            String tagsJson = request.getTags() == null
                ? null
                : DocumentJsonMapper.toJsonArray(request.getTags());

            String customFieldsJson = null;
            String metadataJson = null;

            if (hasCustomFieldChanges || hasMetadataChanges) {
                Map<String, Object> existingCustomFields = DocumentJsonMapper.toMap(document.getAttributesJson());
                Map<String, Object> existingMetadata = DocumentJsonMapper.toMap(document.getMetadataJson());

                DocumentFieldLayout.ResponseProjection projection =
                    DocumentFieldLayout.splitForResponse(existingCustomFields, existingMetadata);

                Map<String, Object> updatedAttributes = new LinkedHashMap<>(projection.attributes());
                Map<String, Object> updatedTopLevelFields = new LinkedHashMap<>(projection.topLevelFields());
                Map<String, Object> updatedMetadata = new LinkedHashMap<>(projection.metadata());

                if (request.getAttributes() != null) {
                    updatedAttributes.putAll(request.getAttributes());
                }

                if (request.hasDynamicFields()) {
                    updatedTopLevelFields.putAll(request.getDynamicFields());
                }

                if (request.getMetadata() != null) {
                    updatedMetadata.putAll(request.getMetadata());
                }

                customFieldsJson = DocumentJsonMapper.toJsonObject(
                    DocumentFieldLayout.mergeCustomFields(updatedAttributes, updatedTopLevelFields));

                metadataJson = DocumentJsonMapper.toJsonObject(
                    DocumentFieldLayout.metadataForPersistence(updatedMetadata, updatedAttributes));
            }

            document.update(
                normalize(request.getTitle()),
                normalize(request.getDocumentType()),
                normalize(request.getStatus()),
                request.getContent(),
                tagsJson,
                customFieldsJson,
                metadataJson
            );

            var saved = documentRepository.save(document);
            documentSearchService.index(saved);
            documentCacheService.evict(saved.getId());
            documentCacheService.evictAllListCaches();
            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Cập nhật tài liệu thất bại: " + ex.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
