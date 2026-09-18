package com.td.application.privatecatalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UpdatePrivateCategoryUseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PrivateCategoryRepository privateCategoryRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(UUID id, UpdatePrivateCategoryRequest request) {
        try {
            var opt = privateCategoryRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy danh mục với ID: " + id);
            }
            var category = opt.get();

            String code = request.getCode() != null
                ? TextNormalizer.normalize(request.getCode()).toUpperCase().replaceAll("\\s+", "_")
                : null;
            String name        = TextNormalizer.normalizeAndSanitize(request.getName());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());
            String form        = normalizeForm(request.getForm());

            if (code != null && !code.equals(category.getCode())) {
                if (privateCategoryRepository.existsByCodeAndIdNotAndDeletedOnIsNull(code, id)) {
                    return Result.failure("Mã danh mục '" + code + "' đã được sử dụng");
                }
            }

            UUID effectiveParentId = request.isUpdateParent()
                ? request.getParentId()
                : category.getParentId();

            if (effectiveParentId != null && effectiveParentId.equals(id)) {
                return Result.failure("Danh mục không thể là cha của chính nó");
            }

            String effectiveName = (name != null) ? name : category.getName();
            int    newLevel      = 0;
            String newFullPath   = effectiveName;

            if (effectiveParentId != null) {
                var parentOpt = privateCategoryRepository.findByIdAndDeletedOnIsNull(effectiveParentId);
                if (parentOpt.isEmpty()) {
                    return Result.failure("Không tìm thấy danh mục cha với ID: " + effectiveParentId);
                }
                var parent = parentOpt.get();
                newLevel    = parent.getLevel() + 1;
                newFullPath = parent.getFullPath() + " / " + effectiveName;
            }

            category.update(code, name, description, form, effectiveParentId,
                            newLevel, newFullPath, request.getSortOrder(), request.getIsActive(),
                            request.getIdentifier());

            if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                Map<String, Object> existingFields =
                    dynamicDocumentFieldsService.parseDynamicFields(category.getDynamicFieldsJson());
                String newFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                    request.getDynamicFields(), request.getDateFields(),
                    request.getNumberFields(), request.getRichtextFields());
                Map<String, Object> newFields = dynamicDocumentFieldsService.parseDynamicFields(newFieldsJson);

                Map<String, Object> merged = new LinkedHashMap<>(existingFields);
                merged.putAll(newFields);
                category.replaceDynamicFields(toJson(merged));
            }

            var saved = privateCategoryRepository.save(category);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Cập nhật danh mục riêng thất bại", ex);
            return Result.failure("Cập nhật danh mục riêng thất bại: " + ex.getMessage());
        }
    }

    private String normalizeForm(String form) {
        if (form == null || form.isBlank()) {
            return null;
        }
        return TextNormalizer.normalizeAndSanitize(form);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
