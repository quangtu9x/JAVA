package com.td.application.categories;

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
public class UpdateCategoryUseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CategoryRepository categoryRepository;
    private final CategoryCacheService categoryCacheService;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(UUID id, UpdateCategoryRequest request) {
        try {
            var opt = categoryRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy danh mục với ID: " + id);
            }
            var category = opt.get();

            // Chuẩn hóa đầu vào
            String code        = request.getCode() != null
                ? TextNormalizer.normalize(request.getCode()).toUpperCase().replaceAll("\\s+", "_")
                : null;
            String name        = TextNormalizer.normalizeAndSanitize(request.getName());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());
            String form        = normalizeForm(request.getForm());

            // Kiểm tra tính duy nhất của code khi có thay đổi
            if (code != null && !code.equals(category.getCode())) {
                if (categoryRepository.existsByCodeAndIdNotAndDeletedOnIsNull(code, id)) {
                    return Result.failure("Mã danh mục '" + code + "' đã được sử dụng");
                }
            }

            // Xác định parent hiệu lực
            UUID effectiveParentId = request.isUpdateParent()
                ? request.getParentId()
                : category.getParentId();

            // Không cho phép tự tham chiếu
            if (effectiveParentId != null && effectiveParentId.equals(id)) {
                return Result.failure("Danh mục không thể là cha của chính nó");
            }

            // Tính lại level / fullPath
            String effectiveName = (name != null) ? name : category.getName();
            int    newLevel      = 0;
            String newFullPath   = effectiveName;

            if (effectiveParentId != null) {
                var parentOpt = categoryRepository.findByIdAndDeletedOnIsNull(effectiveParentId);
                if (parentOpt.isEmpty()) {
                    return Result.failure("Không tìm thấy danh mục cha với ID: " + effectiveParentId);
                }
                var parent = parentOpt.get();
                newLevel   = parent.getLevel() + 1;
                newFullPath = parent.getFullPath() + " / " + effectiveName;
            }

            category.update(code, name, description, form, effectiveParentId,
                            newLevel, newFullPath, request.getSortOrder(), request.getIsActive());

            if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                // Merge vào field động hiện có (không xoá field cũ không được nhắc tới trong lần
                // sửa này) — cùng pattern đã dùng ở UpdateOrganizationUseCase (BL-109).
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

            var saved = categoryRepository.save(category);

            // Xóa cache chi tiết + toàn bộ list cache
            categoryCacheService.evict(saved.getId());
            categoryCacheService.evictAllListCaches();

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Cập nhật danh mục thất bại", ex);
            return Result.failure("Cập nhật danh mục thất bại: " + ex.getMessage());
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
