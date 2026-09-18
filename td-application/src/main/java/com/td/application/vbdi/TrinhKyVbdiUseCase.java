package com.td.application.vbdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbdi.VbdiDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BL-220 (VBDi Core) — DoD (2): trình ký — gửi văn bản đi cho lãnh đạo ký duyệt trước khi ban hành.
 * DoD gọi đây là "workflow task cho lãnh đạo", nhưng đúng phát hiện đã ghi nhận từ BL-211: "Workflow
 * module hiện tại" ({@code WorkflowsController}/...) là scaffold chưa triển khai — không dùng, thay
 * bằng đúng cơ chế document con + field trạng thái đã dùng cho {@code ChuyenXuLyVbdenUseCase}
 * (BL-211)/{@code CapNhatTienDoUseCase} (BL-250): tạo 1 "vết" trình ký trong {@code vbdi_res} (đóng
 * vai trò tương đương "nhiệm vụ ký duyệt" cho lãnh đạo xem qua danh sách document con của mình) và
 * cập nhật {@code nguoi_ky}/{@code status} ngay trên văn bản gốc.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrinhKyVbdiUseCase {

    public static final String DOCUMENT_TYPE = "vbdi";
    public static final String CHILD_DOCUMENT_TYPE = "vbdi_res";
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy văn bản đi";
    private static final String NGUOI_KY_FIELD = "nguoi_ky";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbdiDocumentRepository vbdiDocumentRepository;
    private final AddVbdiDynamicDocumentUseCase addVbdiDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final CurrentUserProvider currentUserProvider;
    private final DocumentLogService documentLogService;
    private final DocumentCacheService documentCacheService;

    public Result<UUID> execute(TrinhKyVbdiRequest request) {
        var documentOptional = vbdiDocumentRepository.findById(request.getId());
        if (documentOptional.isEmpty() || documentOptional.get().isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }
        var document = documentOptional.get();
        if (!DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        String actingUsername = currentUserProvider.currentUsername().orElse("system");

        var childResult = createTrinhKyRecord(request, actingUsername);
        if (!childResult.isSuccess()) {
            return childResult;
        }

        updateDocumentStatus(document, request, actingUsername);

        return Result.success(childResult.getData());
    }

    private Result<UUID> createTrinhKyRecord(TrinhKyVbdiRequest request, String actingUsername) {
        var childRequest = new AddDynamicDocumentRequest();
        childRequest.setTitle("Trình ký");
        childRequest.setDocumentType(CHILD_DOCUMENT_TYPE);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("parentid", request.getId().toString());
        fields.put(NGUOI_KY_FIELD, request.getNguoiKy());
        fields.put("nguoi_trinh", actingUsername);
        if (request.getOpinion() != null && !request.getOpinion().isBlank()) {
            fields.put("opinion", request.getOpinion());
        }
        childRequest.setFields(fields);

        return addVbdiDynamicDocumentUseCase.execute(childRequest);
    }

    private void updateDocumentStatus(VbdiDocument document, TrinhKyVbdiRequest request, String actingUsername) {
        var before = buildSnapshot(document);
        Map<String, Object> fields = new LinkedHashMap<>(dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        fields.put(NGUOI_KY_FIELD, request.getNguoiKy());
        fields.put("nguoi_trinh", actingUsername);

        document.replaceDynamicFields(toJson(fields));
        document.update(null, null, VbdiStatus.PENDING_SIGNATURE, null, null, null, null);
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
