package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.numbering.DocumentNumberingService;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-220 (VBDi Core), yêu cầu người dùng (tách văn bản đi khỏi bảng dùng chung): tương đương
 * {@link com.td.application.documents.AddDynamicDocumentUseCase} (BL-101) nhưng lưu vào bảng vật lý
 * ĐỘC LẬP {@code vbdi_documents} (xem {@link VbdiDocument}) thay vì {@code documents} dùng chung —
 * dùng CHUNG bởi các nơi thuộc "cụm vbdi": {@link SoanThaoVbdiUseCase} (document chính,
 * {@code document_type=vbdi}) và {@link TrinhKyVbdiUseCase} (document {@code vbdi_res}, DoD 2) —
 * tránh viết 2 lần cùng 1 logic build {@code dynamic_fields}/cache/log.
 *
 * <p>TÍCH HỢP {@code DocumentNumberingService} (giống bản BL-101 gốc): BL-104 đã cấu hình
 * {@code document_type="vbdi"} active (sinh {@code document_number} tự động) — xem javadoc
 * {@link SoanThaoVbdiUseCase} về giới hạn "số tạm sinh sớm hơn ngữ nghĩa ban hành" (đã ghi nhận,
 * khuyến nghị review trước Big-bang; {@code BanHanhVbdiUseCase} ghi đè số chính thức).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddVbdiDynamicDocumentUseCase {

    private static final String DEFAULT_TITLE = "Văn bản đi";
    private static final String DOCUMENT_NUMBER_FIELD = "document_number";

    private final VbdiDocumentRepository vbdiDocumentRepository;
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
                ? VbdiStatus.DRAFT
                : request.getStatus().trim();

            assignDocumentNumberIfConfigured(request);
            authorAclFieldsService.applyOnCreate(request.getDocumentType(), request.getFields());

            String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields());

            var document = new VbdiDocument(title, request.getDocumentType(), status, null, null, null, null)
                .replaceDynamicFields(dynamicFieldsJson);

            var saved = vbdiDocumentRepository.save(document);
            documentCacheService.evictAllListCaches();
            documentLogService.logCreate(saved.getId(), buildSnapshot(saved));
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo văn bản đi (dynamic) thất bại", ex);
            return Result.failure("Tạo văn bản đi thất bại: " + ex.getMessage());
        }
    }

    /**
     * BL-104: sinh {@code document_number} tự động nếu {@code documentType} có cấu hình numbering,
     * trừ khi FE đã tự gửi field này (cùng cơ chế {@code AddDynamicDocumentUseCase}).
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

    private Map<String, Object> buildSnapshot(VbdiDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
        snapshot.put("status", document.getStatus());
        snapshot.putAll(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        return snapshot;
    }
}