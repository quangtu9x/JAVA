package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.UploadFileRequest;
import com.td.application.documents.UploadFileUseCase;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (1): ký số thành công file văn bản đi. Tái dùng
 * {@link UploadFileUseCase} (BL-006 family) để lưu file đã ký (đánh dấu {@code isPrimary=true} —
 * đây là "tệp ban hành" chính thức, đúng {@code tepbanhanh} thật), rồi đánh dấu
 * {@code dakyso="1"} (+{@code dakycoquan="1"} nếu ký cơ quan) trên chính document — đúng field
 * name và logic thật của {@code AddSignAttachment()} (Domino không kiểm tra lại chữ ký ở bước này,
 * chỉ tin tưởng client đã ký đúng — xác thực là hành động riêng, xem {@code VerifyFileSignatureUseCase}
 * DoD (2)).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class KySoVbdiUseCase {

    public static final String DOCUMENT_TYPE = "vbdi";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String DA_KY_SO_FIELD = "dakyso";
    private static final String DA_KY_CO_QUAN_FIELD = "dakycoquan";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final UploadFileUseCase uploadFileUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    public Result<UUID> execute(KySoVbdiRequest request) {
        var documentOptional = vbdiDocumentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        var uploadRequest = UploadFileRequest.builder()
            .documentId(request.getId())
            .fileContent(request.getFileContent())
            .fileName(request.getFileName())
            .mimeType(request.getMimeType())
            .fileSize(request.getFileSize())
            .isPrimary(true)
            .description("Tệp đã ký số")
            .build();
        var uploadResult = uploadFileUseCase.execute(uploadRequest);
        if (!uploadResult.isSuccess()) {
            return uploadResult;
        }

        markAsSigned(document, request.isCoQuan());

        return uploadResult;
    }

    private void markAsSigned(VbdiDocument document, boolean coQuan) {
        var before = buildSnapshot(document);
        Map<String, Object> fields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        fields.put(DA_KY_SO_FIELD, "1");
        if (coQuan) {
            fields.put(DA_KY_CO_QUAN_FIELD, "1");
        }

        document.replaceDynamicFields(toJson(fields));
        var saved = vbdiDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));
    }

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
