package com.td.application.documents;

import com.td.application.common.models.Result;
import com.td.application.numbering.DocumentNumberingService;
import com.td.domain.documents.BusinessDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-101 (Dynamic Data Engine): tạo document với field động lưu vào {@code dynamic_fields} JSONB,
 * tương đương {@code qlvb/add} phía Domino.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddDynamicDocumentUseCase {

    private static final String DEFAULT_TITLE = "Document";
    private static final String DEFAULT_STATUS = "DRAFT";
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";

    private final DocumentRepository documentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final DocumentNumberingService documentNumberingService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(AddDynamicDocumentRequest request) {
        try {
            String title = (request.getTitle() == null || request.getTitle().isBlank())
                ? DEFAULT_TITLE
                : request.getTitle().trim();
            String status = (request.getStatus() == null || request.getStatus().isBlank())
                ? DEFAULT_STATUS
                : request.getStatus().trim();

            assignDocumentNumberIfConfigured(request);
            authorAclFieldsService.applyOnCreate(request.getDocumentType(), request.getFields());

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new BusinessDocument(title, request.getDocumentType(), status, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = documentRepository.save(document);
            documentCacheService.evictAllListCaches();
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo tài liệu (dynamic) thất bại", ex);
            return Result.failure("Tạo tài liệu (dynamic) thất bại: " + ex.getMessage());
        }
    }

    /**
     * BL-104: sinh {@code document_number} tự động nếu {@code documentType} có cấu hình numbering,
     * trừ khi FE đã tự gửi field này (giữ nguyên khi migrate dữ liệu lịch sử — BL-305).
     */
    private void assignDocumentNumberIfConfigured(AddDynamicDocumentRequest request) {
        Map<String, String> fields = request.getFields();
        String existing = fields == null ? null : fields.get(DOCUMENT_NUMBER_FIELD);
        if (existing != null && !existing.isBlank()) {
            return;
        }
        documentNumberingService.generateNumber(request.getDocumentType())
            .ifPresent(number -> fields.put(DOCUMENT_NUMBER_FIELD, number));
    }

    private Map<String, Object> buildSnapshot(BusinessDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}
