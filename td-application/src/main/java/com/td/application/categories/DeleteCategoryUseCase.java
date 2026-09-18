package com.td.application.categories;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeleteCategoryUseCase {

    private final CategoryRepository categoryRepository;
    private final CategoryCacheService categoryCacheService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = categoryRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy danh mục với ID: " + id);
            }

            var category = opt.get();
            category.markAsDeleted(resolveCurrentUserId());
            var saved = categoryRepository.save(category);

            categoryCacheService.evict(saved.getId());
            categoryCacheService.evictAllListCaches();

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Xóa danh mục thất bại", ex);
            return Result.failure("Xóa danh mục thất bại: " + ex.getMessage());
        }
    }

    // Ghi đúng người xóa thật (BL-103 audit) — null (không phải UUID ngẫu nhiên) khi không xác định được user
    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
