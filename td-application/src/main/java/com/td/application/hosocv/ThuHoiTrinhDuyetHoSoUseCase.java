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
 * 30/10/2026: "Thu hồi" hồ sơ đã trình lãnh đạo (nút trinhduyet0 ở ChiTietPage.jsx) — thay 2 lệnh
 * Domino {@code qlvb/add} (tạo bản ghi quatrinhduyet action_type=0 + set {@code trinhlanhdaoduyet="0"}).
 * Chỉ người tạo/chủ trì được thu hồi, hồ sơ phải đang chờ duyệt ("2"). Ghi vết
 * {@code hosocv_process_documents} {@code process_type=trinhduyet, action=thuhoi}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ThuHoiTrinhDuyetHoSoUseCase {

    private static final String DOCUMENT_TYPE = TaoHoSoCVUseCase.DOCUMENT_TYPE;
    private static final String TRINH_DA_THU_HOI = "0"; // giữ đúng giá trị Domino ghi khi thu hồi
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;

    public Result<UUID> execute(UUID hosocvId) {
        if (hosocvId == null) {
            return Result.failure("Thiếu id hồ sơ công việc");
        }
        var documentOptional = hoSoCVDocumentRepository.findById(hosocvId);
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()
                || !DOCUMENT_TYPE.equalsIgnoreCase(documentOptional.get().getDocumentType())) {
            return Result.failure("Không tìm thấy hồ sơ công việc");
        }
        String actingUsername = currentUserProvider.currentUsername().orElse(null);
        if (actingUsername == null || actingUsername.isBlank()) {
            return Result.failure("Không xác định được người dùng");
        }
        var document = documentOptional.get();
        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        if (!HoSoCVLifecycleStatus.TRINH_CHO_DUYET.equals(String.valueOf(fields.get(HoSoCVLifecycleStatus.FIELD_TRINHLANHDAODUYET)))) {
            return Result.failure("Hồ sơ không ở trạng thái chờ duyệt");
        }
        if (!isMember(fields.get("user_creator"), actingUsername)
                && !isMember(fields.get("user_process_handler"), actingUsername)) {
            return Result.failure("Chỉ người tạo hoặc người chủ trì hồ sơ được thu hồi");
        }

        var before = buildSnapshot(document);
        fields.put(HoSoCVLifecycleStatus.FIELD_TRINHLANHDAODUYET, TRINH_DA_THU_HOI);
        document.replaceDynamicFields(toJson(fields));
        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        appendProcessRecord(saved.getId(), actingUsername);
        return Result.success(saved.getId());
    }

    // Field người dùng có thể là chuỗi "a;b", mảng JSON hoặc chuỗi đơn.
    private boolean isMember(Object value, String username) {
        if (value == null) {
            return false;
        }
        String target = username.trim().toLowerCase();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && target.equals(item.toString().trim().toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
        for (String part : value.toString().split("[;,]")) {
            if (target.equals(part.replace("\"", "").replace("[", "").replace("]", "").trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void appendProcessRecord(UUID hosocvId, String actingUsername) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle("Thu hồi hồ sơ trình duyệt");
            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put("hosocv_id", hosocvId.toString());
            processFields.put("process_type", "trinhduyet");
            processFields.put("action", "thuhoi");
            processFields.put("creator", actingUsername);
            processRequest.setFields(processFields);
            addHoSoCVProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ignored) {
            // Vết xử lý là audit phụ - lỗi ghi vết không làm hỏng thao tác chính.
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
