package com.td.application.privatecatalog;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.privatecatalog.PrivateCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CreatePrivateCategoryUseCase {

    private final PrivateCategoryRepository privateCategoryRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(CreatePrivateCategoryRequest request) {
        try {
            String code        = normalizeCode(request.getCode());
            String name        = TextNormalizer.normalizeAndSanitize(request.getName());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());
            String form        = normalizeForm(request.getForm());

            // Code không bắt buộc — tự sinh từ name khi form FE không có ô nhập riêng (cùng quyết
            // định Category/BL-113). code == null (không phải chỉ isBlank()) PHẢI được chặn trước —
            // normalizeCode(null) trả về null chứ không phải chuỗi rỗng.
            if (code == null || code.isBlank()) {
                code = generateUniqueCode(name);
            } else if (privateCategoryRepository.existsByCodeAndDeletedOnIsNull(code)) {
                return Result.failure("Mã danh mục '" + code + "' đã tồn tại");
            }

            int    level    = 0;
            String fullPath = name;
            UUID   parentId = request.getParentId();

            if (parentId != null) {
                var parentOpt = privateCategoryRepository.findByIdAndDeletedOnIsNull(parentId);
                if (parentOpt.isEmpty()) {
                    return Result.failure("Không tìm thấy danh mục cha với ID: " + parentId);
                }
                var parent = parentOpt.get();
                if (!parent.isActive()) {
                    return Result.failure("Danh mục cha đang bị vô hiệu hóa, không thể tạo danh mục con");
                }
                level    = parent.getLevel() + 1;
                fullPath = parent.getFullPath() + " / " + name;
            }

            var category = new PrivateCategory(code, name, description, form, parentId, level, fullPath,
                request.getSortOrder() == null ? CreatePrivateCategoryRequest.DEFAULT_SORT_ORDER : request.getSortOrder(),
                request.getIdentifier());

            if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                    request.getDynamicFields(), request.getDateFields(),
                    request.getNumberFields(), request.getRichtextFields());
                category.replaceDynamicFields(dynamicFieldsJson);
            }

            var saved = privateCategoryRepository.save(category);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo danh mục riêng thất bại", ex);
            return Result.failure("Tạo danh mục riêng thất bại: " + ex.getMessage());
        }
    }

    private String normalizeCode(String code) {
        if (code == null) return null;
        return TextNormalizer.normalize(code).toUpperCase().replaceAll("\\s+", "_");
    }

    private String normalizeForm(String form) {
        if (form == null || form.isBlank()) {
            return null;
        }
        return TextNormalizer.normalizeAndSanitize(form);
    }

    private String generateUniqueCode(String name) {
        String base = (name == null || name.isBlank() ? "DANHMUC" : name)
            .toUpperCase().replaceAll("\\s+", "_");
        String candidate = base;
        int suffix = 2;
        while (privateCategoryRepository.existsByCodeAndDeletedOnIsNull(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }
}
