package com.td.application.categories;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final CategoryCacheService categoryCacheService;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = categoryRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy danh mục với ID: " + id);
            }

            var category = opt.get();
            // TODO: thay bằng authenticated user id khi có security context
            category.markAsDeleted(UUID.randomUUID());
            var saved = categoryRepository.save(category);

            categoryCacheService.evict(saved.getId());
            categoryCacheService.evictAllListCaches();

            return Result.success(saved.getId());
        } catch (Exception ex) {
            return Result.failure("Xóa danh mục thất bại: " + ex.getMessage());
        }
    }
}
