package com.td.application.hosocv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
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
 * Mở rộng BL-240 (HoSoCV Core) — G3: lãnh đạo duyệt/không duyệt hồ sơ đã được trình
 * ({@code trinhlanhdaoduyet: "2" → "1"} đã duyệt, hoặc {@code → "3"} không được duyệt). Yêu cầu
 * hồ sơ đang ở trạng thái {@code "2"} (đang chờ duyệt) — đúng vòng đời
 * {@code docs/analysis/08-...md} mục 2.2. Ghi vết {@code hosocv_process_documents}
 * ({@code process_type=duyet}).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DuyetHoSoUseCase {

    private static final String DOCUMENT_TYPE = TaoHoSoCVUseCase.DOCUMENT_TYPE;
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ công việc";
    private static final String NOT_PENDING_MESSAGE = "Hồ sơ không ở trạng thái chờ duyệt";
    private static final String PROCESS_TYPE_DUYET = "duyet";
    private static final String FALLBACK_USERNAME = "system";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;

    public Result<UUID> execute(DuyetHoSoRequest request) {
        if (request == null || request.getHosocvId() == null || request.getDuyet() == null) {
            return Result.failure("Thiếu id hồ sơ công việc hoặc kết quả duyệt");
        }
        var documentOptional = hoSoCVDocumentRepository.findById(request.getHosocvId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(documentOptional.get().getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));

        Object currentTrinh = fields.get(HoSoCVLifecycleStatus.FIELD_TRINHLANHDAODUYET);
        if (!HoSoCVLifecycleStatus.TRINH_CHO_DUYET.equals(String.valueOf(currentTrinh))) {
            return Result.failure(NOT_PENDING_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse(FALLBACK_USERNAME);
        var before = buildSnapshot(document);

        String targetTrinh = request.getDuyet()
            ? HoSoCVLifecycleStatus.TRINH_DA_DUYET
            : HoSoCVLifecycleStatus.TRINH_KHONG_DUOC_DUYET;
        fields.put(HoSoCVLifecycleStatus.FIELD_TRINHLANHDAODUYET, targetTrinh);

        document.replaceDynamicFields(toJson(fields));
        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        appendProcessRecord(saved.getId(), request.getDuyet(), request.getYKien(), actingUsername);

        return Result.success(saved.getId());
    }

    private void appendProcessRecord(UUID hosocvId, boolean duyet, String yKien, String actingUsername) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle(duyet ? "Duyệt hồ sơ công việc" : "Không duyệt hồ sơ công việc");
            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put("hosocv_id", hosocvId.toString());
            processFields.put("process_type", PROCESS_TYPE_DUYET);
            processFields.put("action", duyet ? "duyet" : "khongduyet");
            processFields.put("creator", actingUsername);
            if (yKien != null && !yKien.isBlank()) {
                processFields.put("opinion", yKien);
            }
            processRequest.setFields(processFields);
            addHoSoCVProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ignored) {
            // Vet xu ly la audit phu - loi ghi vet KHONG duoc lam hong thao tac duyet chinh.
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
