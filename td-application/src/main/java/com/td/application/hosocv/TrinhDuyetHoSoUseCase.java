package com.td.application.hosocv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosocv.HoSoCVDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G3 ({@code docs/analysis/08-...md} mục 2.2/8.1
 * {@code TrinhDuyetHoSoUseCase}): trình lãnh đạo duyệt hồ sơ công việc —
 * {@code trinhlanhdaoduyet: "" → "2"} (đang chờ duyệt), tuỳ chọn set {@code user_approved_by}. Ghi
 * vết vào {@code hosocv_process_documents} ({@code process_type=trinhduyet}) — tương đương
 * {@code quatrinhduyet} phía Domino.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrinhDuyetHoSoUseCase {

    private static final String DOCUMENT_TYPE = TaoHoSoCVUseCase.DOCUMENT_TYPE;
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ công việc";
    private static final String USER_APPROVED_BY_FIELD = "user_approved_by";
    private static final String PROCESS_TYPE_TRINH_DUYET = "trinhduyet";
    private static final String FALLBACK_USERNAME = "system";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Xem AuthorAclFieldsService javadoc muc "user_fields dong".
    private static final java.util.Set<String> AUTHOR_TOUCHED_FIELDS = java.util.Set.of(USER_APPROVED_BY_FIELD);

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(TrinhDuyetHoSoRequest request) {
        if (request == null || request.getHosocvId() == null) {
            return Result.failure("Thiếu id hồ sơ công việc");
        }
        var documentOptional = hoSoCVDocumentRepository.findById(request.getHosocvId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(documentOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        var before = buildSnapshot(document);

        fields.put(HoSoCVLifecycleStatus.FIELD_TRINHLANHDAODUYET, HoSoCVLifecycleStatus.TRINH_CHO_DUYET);
        if (request.getNguoiDuyet() != null && !request.getNguoiDuyet().isBlank()) {
            fields.put(USER_APPROVED_BY_FIELD, request.getNguoiDuyet().trim());
        }

        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, fields, AUTHOR_TOUCHED_FIELDS);
        document.replaceDynamicFields(toJson(fields));
        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        appendProcessRecord(saved.getId(), request.getGhiChu(), actingUsername);

        return Result.success(saved.getId());
    }

    private void appendProcessRecord(UUID hosocvId, String ghiChu, String actingUsername) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle("Trình duyệt hồ sơ công việc");
            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put("hosocv_id", hosocvId.toString());
            processFields.put("process_type", PROCESS_TYPE_TRINH_DUYET);
            processFields.put("creator", actingUsername);
            if (ghiChu != null && !ghiChu.isBlank()) {
                processFields.put("note", ghiChu);
            }
            processRequest.setFields(processFields);
            addHoSoCVProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ignored) {
            // Vet xu ly la audit phu - loi ghi vet KHONG duoc lam hong thao tac trinh duyet chinh.
        }
    }

    private Map<String, Object> buildSnapshot(HoSoCVDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
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
