package com.td.application.documents;

import com.td.application.common.models.Result;
import com.td.application.numbering.DocumentNumberingService;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CreateDocumentUseCase {

    private static final String DOCUMENT_NUMBER_FIELD = "document_number";

    private final DocumentRepository documentRepository;
    private final DocumentCacheService documentCacheService;
    private final DocumentSearchService documentSearchService;
    private final DocumentLogService documentLogService;
    private final DocumentNumberingService documentNumberingService;

    public Result<UUID> execute(CreateDocumentRequest request) {
        try {
            String documentType = normalize(request.getDocumentType());
            Map<String, Object> customFields = request.resolveCustomFieldsForPersistence();
            assignDocumentNumberIfConfigured(documentType, customFields);

            var document = new BusinessDocument(
                request.getTitle().trim(),
                documentType,
                normalizeDefault(request.getStatus(), "DRAFT"),
                request.getContent(),
                DocumentJsonMapper.toJsonArray(request.getTags()),
                DocumentJsonMapper.toJsonObject(customFields),
                DocumentJsonMapper.toJsonObject(request.resolveMetadataForPersistence())
            );

            var saved = documentRepository.save(document);
            documentSearchService.index(saved);
            documentCacheService.evictAllListCaches();
            documentLogService.logCreate(saved.getId(), DocumentDtoMapper.map(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo tài liệu thất bại", ex);
            return Result.failure("Tạo tài liệu thất bại: " + ex.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    /**
     * BL-104: sinh {@code document_number} tự động nếu {@code documentType} có cấu hình numbering
     * (vd. vbden, vbdi) và caller chưa tự cung cấp field này (giữ nguyên số cũ khi migrate dữ liệu
     * lịch sử — BL-305 — thay vì ghi đè).
     */
    private void assignDocumentNumberIfConfigured(String documentType, Map<String, Object> customFields) {
        if (customFields.containsKey(DOCUMENT_NUMBER_FIELD)
                && customFields.get(DOCUMENT_NUMBER_FIELD) != null
                && !String.valueOf(customFields.get(DOCUMENT_NUMBER_FIELD)).isBlank()) {
            return;
        }
        documentNumberingService.generateNumber(documentType)
            .ifPresent(number -> customFields.put(DOCUMENT_NUMBER_FIELD, number));
    }
}
