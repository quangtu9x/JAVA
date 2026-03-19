package com.td.application.categories;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.categories.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final CategoryCacheService categoryCacheService;

    public Result<UUID> execute(CreateCategoryRequest request) {
        try {
            // 1. Chuẩn hóa NFC, sanitize XSS; code → viết HOA, thay space bằng _
            String code        = normalizeCode(request.getCode());
            String name        = TextNormalizer.normalizeAndSanitize(request.getName());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());

            // 2. Code phải duy nhất
            if (categoryRepository.existsByCodeAndDeletedOnIsNull(code)) {
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
            var category = new Category(code, name, description, parentId, level, fullPath, request.getSortOrder());
            var saved    = categoryRepository.save(category);

            // 5. Cache: xóa toàn bộ list cache (dữ liệu cũ lỗi thời)
            categoryCacheService.evictAllListCaches();

            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Tạo danh mục thất bại: " + ex.getMessage());
        }
    }

    /** NFC normalize + viết HOA + thay khoảng trắng liên tiếp bằng _ */
    private String normalizeCode(String code) {
        if (code == null) return null;
        return TextNormalizer.normalize(code).toUpperCase().replaceAll("\\s+", "_");
    }
}
