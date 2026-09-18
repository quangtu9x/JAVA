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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mở rộng BL-240 (HoSoCV Core) — G4/G9 ({@code docs/analysis/08-...md} mục 5.2
 * {@code saveInitHoso()}/{@code TDDocument.java} dòng 916-967): ghi ý kiến phối hợp/góp ý vào hồ sơ
 * công việc, đúng cơ chế thật — khi có ý kiến phối hợp mới, đồng bộ NGƯỢC {@code coordinated}
 * (tên người đã phối hợp) + {@code user_coordinated} (username) lên chính hồ sơ.
 *
 * <p>Đơn giản hoá CÓ CHỦ Ý: nối {@code username} (không phải "tên đầy đủ") vào CẢ 2 field
 * {@code coordinated}/{@code user_coordinated} — Domino thật đọc tên hiển thị từ profile người
 * dùng (`fullname`), Java hiện chưa có bằng chứng field tương đương gắn liền context này; ghi rõ
 * để không suy diễn thêm 1 lượt tra cứu hồ sơ người dùng không cần thiết cho phạm vi lượt port này.
 *
 * <p>KHÔNG dùng {@code TDInit.setViewError} (view counter Domino, G5) — Java tính trực tiếp từ
 * field ở tầng {@code ViewQueryService} (xem {@code VIEW_HOSOCV_COORDINATED_STATUS}), không cần cơ
 * chế view-counter tương đương.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GopYPhoiHopUseCase {

    private static final String DOCUMENT_TYPE = TaoHoSoCVUseCase.DOCUMENT_TYPE;
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ công việc";
    private static final String COORDINATED_FIELD = "coordinated";
    private static final String USER_COORDINATED_FIELD = "user_coordinated";
    private static final String DEFAULT_PROCESS_TYPE = "phoihop";
    private static final String FALLBACK_USERNAME = "system";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;

    public Result<UUID> execute(GopYPhoiHopRequest request) {
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
        String processType = (request.getLoai() == null || request.getLoai().isBlank())
            ? DEFAULT_PROCESS_TYPE : request.getLoai().trim().toLowerCase();

        Map<String, Object> fields = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        var before = buildSnapshot(document);

        appendToSemicolonList(fields, COORDINATED_FIELD, actingUsername);
        appendToSemicolonList(fields, USER_COORDINATED_FIELD, actingUsername);

        document.replaceDynamicFields(toJson(fields));
        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        appendProcessRecord(saved.getId(), processType, request.getOpinion(), actingUsername);

        return Result.success(saved.getId());
    }

    private void appendToSemicolonList(Map<String, Object> fields, String fieldName, String value) {
        List<String> values = new ArrayList<>();
        Object existing = fields.get(fieldName);
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    values.add(item.toString());
                }
            }
        } else if (existing instanceof String str && !str.isBlank()) {
            for (String part : str.split(";")) {
                if (!part.isBlank()) {
                    values.add(part.trim());
                }
            }
        }
        if (!values.contains(value)) {
            values.add(value);
        }
        fields.put(fieldName, String.join(";", values));
    }

    private void appendProcessRecord(UUID hosocvId, String processType, String opinion, String actingUsername) {
        try {
            var processRequest = new AddDynamicDocumentRequest();
            processRequest.setTitle("gopy".equals(processType) ? "Góp ý hồ sơ công việc" : "Phối hợp hồ sơ công việc");
            Map<String, String> processFields = new LinkedHashMap<>();
            processFields.put("hosocv_id", hosocvId.toString());
            processFields.put("process_type", processType);
            processFields.put("opinion", opinion);
            processFields.put("creator", actingUsername);
            processRequest.setFields(processFields);
            addHoSoCVProcessDocumentUseCase.execute(processRequest);
        } catch (Exception ignored) {
            // Vet xu ly la audit phu - loi ghi vet KHONG duoc lam hong thao tac chinh.
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
