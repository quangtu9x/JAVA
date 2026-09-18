package com.td.application.hosoluutru;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port module "Hồ sơ lưu trữ" — hiệu chỉnh 1 hồ sơ lưu trữ ĐÃ TỒN TẠI, đúng khuôn
 * {@code CapNhatHoSoUseCase} (hosocv): merge {@code dynamic_fields} (KHÔNG replace toàn bộ, đúng
 * {@code docs/architecture/04-quy-tac-crud-api-shared-core.md} mục 6), loại {@code user_creator} khỏi
 * field client gửi trước khi merge (SERVER-AUTHORITATIVE).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CapNhatHoSoLuuTruUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy hồ sơ lưu trữ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(LuuHoSoLuuTruRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id hồ sơ lưu trữ cần cập nhật");
        }
        HoSoLuuTruDocument document = hoSoLuuTruDocumentRepository.findById(request.getId()).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, String> fields = request.getFields() == null
            ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getFields());
        fields.remove(HoSoLuuTruLifecycleStatus.FIELD_USER_CREATOR);

        Map<String, Object> existing = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        Map<String, Object> incoming = dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(
                fields, request.getDateFields(), request.getNumberFields(), request.getRichtextFields()));
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(incoming);
        authorAclFieldsService.applyOnUpdate(TaoHoSoLuuTruTuHoSoCVUseCase.DOCUMENT_TYPE, merged, fields.keySet());
        document.replaceDynamicFields(toJson(merged));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            document.update(request.getTitle(), null, null, null, null, null, null);
        }

        var saved = hoSoLuuTruDocumentRepository.save(document);
        return Result.success(saved.getId());
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
