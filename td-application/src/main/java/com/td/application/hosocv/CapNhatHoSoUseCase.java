package com.td.application.hosocv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
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
 * Mở rộng BL-240 (HoSoCV Core) — G1 ({@code docs/analysis/08-...md} mục 8.1
 * {@code LuuHoSoUseCase}): hiệu chỉnh 1 hồ sơ công việc ĐÃ TỒN TẠI, tương thích 1 phần
 * {@code qlvb/add?id=...&databasetype=hosocv} (khác {@link TaoHoSoCVUseCase}, chỉ xử lý tạo mới) —
 * đúng khuôn {@code LuuDuThaoUseCase}. Dùng cho cả "chọn danh mục hồ sơ" (actionName
 * {@code chondanhmuchoso} — chỉ cần gửi field {@code category}/{@code category_id} như field động
 * bình thường, không cần use case riêng).
 *
 * <p>Merge {@code dynamic_fields} (KHÔNG replace toàn bộ — đúng
 * {@code docs/architecture/04-quy-tac-crud-api-shared-core.md} mục 6); loại {@code user_creator}
 * khỏi field client gửi TRƯỚC khi merge (danh tính người tạo bất biến sau khi tạo — đúng nguyên
 * tắc SERVER-AUTHORITATIVE của {@link TaoHoSoCVUseCase}, tương tự {@code LuuDuThaoUseCase}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CapNhatHoSoUseCase {

    private static final String DOCUMENT_TYPE = TaoHoSoCVUseCase.DOCUMENT_TYPE;
    private static final String USER_CREATOR_FIELD = TaoHoSoCVUseCase.USER_CREATOR_FIELD;
    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ công việc";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoCVDocumentRepository hoSoCVDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(LuuHoSoRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id hồ sơ công việc cần cập nhật");
        }
        HoSoCVDocument document = hoSoCVDocumentRepository.findById(request.getId()).orElse(null);
        if (document == null || document.isDeleted() || !DOCUMENT_TYPE.equalsIgnoreCase(document.getDocumentType())) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, String> fields = request.getFields() == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getFields());
        // Danh tinh nguoi tao bat bien sau khi tao - client khong duoc doi (dung nguyen tac
        // SERVER-AUTHORITATIVE cua TaoHoSoCVUseCase).
        fields.remove(USER_CREATOR_FIELD);

        var before = buildSnapshot(document);
        mergeFieldsIntoDocument(document, fields, request);
        var saved = hoSoCVDocumentRepository.save(document);
        documentCacheService.evict(saved.getId());
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, buildSnapshot(saved));

        return Result.success(saved.getId());
    }

    // Merge (KHONG replace toan bo) field moi vao dynamic_fields hien co - field khong nam trong
    // request.getFields() giu nguyen gia tri cu.
    private void mergeFieldsIntoDocument(HoSoCVDocument document, Map<String, String> fields, LuuHoSoRequest request) {
        Map<String, Object> existing = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        Map<String, Object> incoming = dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(
                fields, request.getDateFields(), request.getNumberFields(), request.getRichtextFields()));
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(incoming);
        authorAclFieldsService.applyOnUpdate(DOCUMENT_TYPE, merged, fields.keySet());
        document.replaceDynamicFields(toJson(merged));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            document.update(request.getTitle(), null, null, null, null, null, null);
        }
    }

    private Map<String, Object> buildSnapshot(HoSoCVDocument document) {
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
