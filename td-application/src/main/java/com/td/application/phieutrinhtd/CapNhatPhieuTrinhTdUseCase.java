package com.td.application.phieutrinhtd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hiệu chỉnh 1 phiếu trình thẩm định ĐÃ TỒN TẠI (KHÔNG đổi {@code status} — xem
 * {@link ChuyenTrangThaiPhieuTrinhTdUseCase} cho việc đó), đúng khuôn {@code CapNhatHoSoLuuTruUseCase}:
 * merge {@code dynamic_fields} (KHÔNG replace toàn bộ, đúng
 * {@code docs/architecture/04-quy-tac-crud-api-shared-core.md} mục 6).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CapNhatPhieuTrinhTdUseCase {

    private static final String NOT_FOUND_MESSAGE = "Không tìm thấy phiếu trình";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;
    private final AuthorAclFieldsService authorAclFieldsService;

    public Result<UUID> execute(LuuPhieuTrinhTdRequest request) {
        if (request == null || request.getId() == null) {
            return Result.failure("Thiếu id phiếu trình cần cập nhật");
        }
        PhieuTrinhTdDocument document = phieuTrinhTdDocumentRepository.findById(request.getId()).orElse(null);
        if (document == null || document.isDeleted()) {
            return Result.failure(NOT_FOUND_MESSAGE);
        }

        Map<String, Object> existing = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        Map<String, Object> incoming = dynamicDocumentFieldsService.parseDynamicFields(
            dynamicDocumentFieldsService.buildDynamicFieldsJson(
                request.getFields(), request.getDateFields(), request.getNumberFields(), request.getRichtextFields()));
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(incoming);
        authorAclFieldsService.applyOnUpdate(
            PhieuTrinhTdDocument.DOCUMENT_TYPE, merged, request.getFields().keySet());
        document.replaceDynamicFields(toJson(merged));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            document.update(request.getTitle(), null);
        }

        var saved = phieuTrinhTdDocumentRepository.save(document);
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
