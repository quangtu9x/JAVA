package com.td.application.categories;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.categories.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CreateCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final CategoryCacheService categoryCacheService;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(CreateCategoryRequest request) {
        try {
            // 1. Chuẩn hóa NFC, sanitize XSS; code → viết HOA, thay space bằng _
            String code        = normalizeCode(request.getCode());
            String name        = TextNormalizer.normalizeAndSanitize(request.getName());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());
            String form        = normalizeForm(request.getForm());

            // 2. Code không bắt buộc (BL-113) — tự sinh từ name khi form FE không có ô nhập riêng
            // (vd "Nhóm cơ quan ngoài"). code == null (không phải chỉ isBlank()) PHẢI được chặn
            // trước — normalizeCode(null) trả về null chứ không phải chuỗi rỗng (bug NPE đã xảy ra
            // thật với CreateAppRoleUseCase.code, xem docs/architecture/04-quy-tac-crud-api-shared-core.md).
            if (code == null || code.isBlank()) {
                code = generateUniqueCode(name);
            } else if (categoryRepository.existsByCodeAndDeletedOnIsNull(code)) {
                return Result.failure("Mã danh mục '" + code + "' đã tồn tại");
            }

            // 3. Tính level và fullPath từ parent
            int    level    = 0;
            String fullPath = name;
            UUID   parentId = request.getParentId();

            if (parentId != null) {
                var parentOpt = categoryRepository.findByIdAndDeletedOnIsNull(parentId);
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

            // 4. Lưu
            var category = new Category(code, name, description, form, parentId, level, fullPath, request.getSortOrder());

            if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                    request.getDynamicFields(), request.getDateFields(),
                    request.getNumberFields(), request.getRichtextFields());
                category.replaceDynamicFields(dynamicFieldsJson);
            }

            var saved    = categoryRepository.save(category);

            // 5. Cache: xóa toàn bộ list cache (dữ liệu cũ lỗi thời)
            categoryCacheService.evictAllListCaches();

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo danh mục thất bại", ex);
            return Result.failure("Tạo danh mục thất bại: " + ex.getMessage());
        }
    }

    /** NFC normalize + viết HOA + thay khoảng trắng liên tiếp bằng _ */
    private String normalizeCode(String code) {
        if (code == null) return null;
        return TextNormalizer.normalize(code).toUpperCase().replaceAll("\\s+", "_");
    }

    // Tự sinh code từ name khi form FE không gửi (không có ô nhập riêng) — thêm hậu tố số nếu
    // trùng, cùng mẫu CreateAppRoleUseCase.generateUniqueCode (BL-111)/CreateOrganizationUseCase
    // (BL-105) tự sinh identifier cho agency_level.
    private String generateUniqueCode(String name) {
        String base = (name == null || name.isBlank() ? "DANHMUC" : name)
            .toUpperCase().replaceAll("\\s+", "_");
        String candidate = base;
        int suffix = 2;
        while (categoryRepository.existsByCodeAndDeletedOnIsNull(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String normalizeForm(String form) {
        if (form == null || form.isBlank()) {
            return null;
        }
        return TextNormalizer.normalizeAndSanitize(form);
    }
}
