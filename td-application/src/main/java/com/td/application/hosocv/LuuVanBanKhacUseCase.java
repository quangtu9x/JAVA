package com.td.application.hosocv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserFullNameResolver;
import com.td.domain.hosocv.HoSoCVDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 02/10/2026 (cutover tab "VĂN BẢN LIÊN QUAN" của Hồ sơ công việc — panel "Văn bản, tài liệu liên
 * quan khác"): thêm/sửa 1 "văn bản khác" gắn vào hồ sơ, thay {@code qlvb/add?databasetype=hosocv}
 * (Domino lưu document {@code Form="other_related_document"} trong CSDL hồ sơ, khoá
 * {@code document_profiles_id} = id hồ sơ — view {@code vwOther_Related_Document_Document_Profiles_ID},
 * {@code view_hosocv.dxl} dòng 469).
 *
 * <p>Lưu ở bảng {@code hosocv_documents} nhưng với {@link #DOCUMENT_TYPE} RIÊNG (không phải
 * {@code "hosocv"}): mọi view/thao tác hồ sơ đều lọc theo {@code document_type}, nên văn bản khác
 * không lẫn vào danh sách hồ sơ và không bị {@code GanKemVanBanUseCase} nhận nhầm là hồ sơ. Vẫn giữ
 * field {@code form=other_related_document} như Domino. Tệp đính kèm tải lên SAU khi lưu qua API tệp
 * chung {@code /api/v1/documents/{id}/files}.</p>
 *
 * <p>Chỉ nhận các ô của form {@code VBKhacModal.jsx} ({@link #EDITABLE_FIELDS}); người nhập
 * ({@code creator}/{@code user_creator}) do server ghi theo người đăng nhập lúc thêm mới.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LuuVanBanKhacUseCase {

    public static final String DOCUMENT_TYPE = "hosocv_vbkhac";
    public static final String FORM = "other_related_document";
    public static final String FIELD_DOCUMENT_PROFILES_ID = "document_profiles_id";
    static final List<String> EDITABLE_FIELDS = List.of(
        "code", "promulgation_date", "unit_publisher", "signer", "subject");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String HOSOCV_DOCUMENT_TYPE = "hosocv";
    private static final String DEFAULT_TITLE = "Văn bản khác";
    private static final String STATUS = "1";
    private static final String HOSO_NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ công việc";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản khác";

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final UserFullNameResolver userFullNameResolver;

    public Result<UUID> execute(LuuVanBanKhacRequest request) {
        Map<String, String> editable = pickEditableFields(request.getFields());
        return request.getId() == null
            ? create(request.getHosocvId(), editable)
            : update(request.getId(), editable);
    }

    private Result<UUID> create(UUID hosocvId, Map<String, String> editable) {
        if (hosocvId == null) {
            return Result.failure(HOSO_NOT_FOUND_MESSAGE);
        }
        var hosocv = hoSoCVDocumentRepository.findById(hosocvId)
            .filter(document -> !document.isDeleted()
                && HOSOCV_DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType()));
        if (hosocv.isEmpty()) {
            return Result.failure(HOSO_NOT_FOUND_MESSAGE);
        }

        String username = currentUserProvider.currentUsername().orElse(null);
        Map<String, String> fields = new LinkedHashMap<>(editable);
        fields.put("form", FORM);
        fields.put(FIELD_DOCUMENT_PROFILES_ID, hosocvId.toString());
        fields.put("user_creator", username == null ? "" : username);
        fields.put("creator", username == null ? "" : userFullNameResolver.resolveForWrite(username, null));

        var document = new HoSoCVDocument(titleOf(editable), DOCUMENT_TYPE, STATUS, null, null, null, null)
            .replaceDynamicFields(dynamicDocumentFieldsService.buildDynamicFieldsJson(fields, null, null, null));
        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evictAllListCaches();
        documentLogService.logCreate(saved.getId(), snapshot(saved));
        return Result.success(saved.getId());
    }

    private Result<UUID> update(UUID id, Map<String, String> editable) {
        var existing = hoSoCVDocumentRepository.findById(id)
            .filter(document -> !document.isDeleted() && DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType()));
        if (existing.isEmpty()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = existing.get();
        var before = snapshot(document);

        Map<String, Object> merged = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        merged.putAll(dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(editable, null, null, null)));
        document.update(titleOf(editable), null, null, null, null, null, null);
        document.replaceDynamicFields(toJson(merged));

        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, snapshot(saved));
        return Result.success(saved.getId());
    }

    private Map<String, String> pickEditableFields(Map<String, String> fields) {
        Map<String, String> editable = new LinkedHashMap<>();
        if (fields == null) {
            return editable;
        }
        fields.forEach((key, value) -> {
            String normalized = key == null ? "" : key.trim().toLowerCase();
            if (EDITABLE_FIELDS.contains(normalized)) {
                editable.put(normalized, value == null ? "" : value.trim());
            }
        });
        return editable;
    }

    private String titleOf(Map<String, String> editable) {
        String subject = editable.get("subject");
        return subject == null || subject.isBlank() ? DEFAULT_TITLE : subject;
    }

    private Map<String, Object> snapshot(HoSoCVDocument document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", document.getTitle());
        snapshot.put("documentType", document.getDocumentType());
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
