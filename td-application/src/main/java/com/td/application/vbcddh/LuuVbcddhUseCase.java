package com.td.application.vbcddh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbcddh.VbcddhDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 30/10/2026 (cutover FE vbcddh khỏi Domino): thay {@code qlvb/add?databasetype=vbcddh|vbcddh_res}
 * (tạo/hiệu chỉnh văn bản CĐĐH, nhiệm vụ, vết tiến độ/kết quả xử lý, cờ trạng thái...).
 * <ul>
 *   <li>Tạo mới: qua {@link AddVbcddhDynamicDocumentUseCase}; {@code user_creator} lấy theo tài khoản
 *   đăng nhập (không tin client); có {@code parentId} thì ghi field {@code parentid}.</li>
 *   <li>Hiệu chỉnh: merge {@code dynamic_fields} (không thay toàn bộ); KHÔNG cho đổi {@code form}
 *   (thùng rác đi qua Xoa/KhoiPhuc) và {@code user_creator}.</li>
 *   <li>Nhiệm vụ có {@code idvanban} đổi tình trạng/tạo mới → tính lại tình trạng văn bản gốc
 *   ({@link VbcddhStatusRollupService}, best-effort).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LuuVbcddhUseCase {

    static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of("vbcddh", "vbcddh_res");
    private static final Set<String> PROTECTED_ON_UPDATE = Set.of("form", "user_creator", "id");
    private static final List<String> TITLE_SOURCE_FIELDS = List.of("trichyeu", "noidung", "tieude", "ykien");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VbcddhDocumentRepository vbcddhDocumentRepository;
    private final AddVbcddhDynamicDocumentUseCase addVbcddhDynamicDocumentUseCase;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final DocumentCacheService documentCacheService;
    private final DocumentLogService documentLogService;
    private final VbcddhStatusRollupService vbcddhStatusRollupService;
    private final CurrentUserProvider currentUserProvider;

    public Result<UUID> execute(LuuVbcddhRequest request) {
        if (request == null) {
            return Result.failure("Thiếu dữ liệu lưu");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        if (request.getFields() != null) {
            request.getFields().forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    fields.put(key.trim().toLowerCase(), value);
                }
            });
        }
        if (request.getId() != null && !request.getId().isBlank()) {
            return update(request, fields);
        }
        return create(request, fields);
    }

    private Result<UUID> create(LuuVbcddhRequest request, Map<String, String> fields) {
        String documentType = request.getDocumentType() == null || request.getDocumentType().isBlank()
            ? "vbcddh" : request.getDocumentType().trim().toLowerCase();
        if (!ALLOWED_DOCUMENT_TYPES.contains(documentType)) {
            return Result.failure("Loại dữ liệu không hợp lệ: " + documentType);
        }
        currentUserProvider.currentUsername().ifPresent(username -> fields.put("user_creator", username));
        if (request.getParentId() != null && !request.getParentId().isBlank()) {
            UUID parentId = VbcddhIds.parse(request.getParentId());
            if (parentId == null || vbcddhDocumentRepository.findById(parentId).filter(d -> !d.isDeleted()).isEmpty()) {
                return Result.failure("Không tìm thấy bản ghi cha");
            }
            fields.put("parentid", parentId.toString());
        }

        AddDynamicDocumentRequest add = new AddDynamicDocumentRequest();
        add.setDocumentType(documentType);
        add.setTitle(resolveTitle(request.getTitle(), fields));
        add.setFields(fields);
        add.setDateFields(request.getDateFields());
        add.setNumberFields(request.getNumberFields());
        add.setRichtextFields(request.getRichtextFields());
        Result<UUID> result = addVbcddhDynamicDocumentUseCase.execute(add);
        if (result.isSuccess() && "vbcddh".equals(documentType) && "nhiemvu".equalsIgnoreCase(fields.get("form"))) {
            rollup(fields.get("idvanban"));
        }
        return result;
    }

    private Result<UUID> update(LuuVbcddhRequest request, Map<String, String> fields) {
        UUID id = VbcddhIds.parse(request.getId());
        if (id == null) {
            return Result.failure("id không hợp lệ");
        }
        VbcddhDocument document = vbcddhDocumentRepository.findById(id).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure("Không tìm thấy văn bản chỉ đạo/nhiệm vụ");
        }
        PROTECTED_ON_UPDATE.forEach(fields::remove);

        Map<String, Object> existing = new LinkedHashMap<>(
            dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson()));
        Map<String, Object> before = new LinkedHashMap<>(existing);
        Map<String, Object> incoming = dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(
                fields, request.getDateFields(), request.getNumberFields(), request.getRichtextFields()));
        existing.putAll(incoming);
        document.replaceDynamicFields(toJson(existing));

        String newTitle = request.getTitle() != null && !request.getTitle().isBlank()
            ? AddDynamicDocumentRequest.labelTitle(request.getTitle().trim())
            : (fields.containsKey("trichyeu") ? resolveTitle(null, fields) : null);
        if (newTitle != null) {
            document.update(newTitle, null, null, null, null, null, null);
        }
        var saved = vbcddhDocumentRepository.save(document);
        documentCacheService.evictAllListCaches();
        documentLogService.logUpdate(saved.getId(), before, existing);

        if ("nhiemvu".equalsIgnoreCase(asString(existing.get("form"))) && fields.containsKey("tinhtrang")) {
            rollup(asString(existing.get("idvanban")));
        }
        return Result.success(saved.getId());
    }

    private void rollup(String idVanBan) {
        UUID parentId = VbcddhIds.parse(idVanBan);
        if (parentId == null) {
            return;
        }
        try {
            vbcddhStatusRollupService.recomputeParentStatus(parentId);
        } catch (Exception ex) {
            log.warn("Tính lại tình trạng văn bản chỉ đạo {} thất bại: {}", parentId, ex.getMessage());
        }
    }

    private String resolveTitle(String title, Map<String, String> fields) {
        if (title != null && !title.isBlank()) {
            return AddDynamicDocumentRequest.labelTitle(title.trim());
        }
        for (String field : TITLE_SOURCE_FIELDS) {
            String value = fields.get(field);
            if (value != null && !value.isBlank()) {
                return AddDynamicDocumentRequest.labelTitle(value.trim());
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
