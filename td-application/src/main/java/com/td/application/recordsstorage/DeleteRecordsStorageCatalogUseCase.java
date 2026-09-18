package com.td.application.recordsstorage;

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
public class DeleteRecordsStorageCatalogUseCase {

    private final RecordsStorageCatalogRepository recordsStorageCatalogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = recordsStorageCatalogRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy danh mục hồ sơ với ID: " + id);
            }
            var entity = opt.get();
            entity.markAsDeleted(resolveCurrentUserId());
            var saved = recordsStorageCatalogRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Xóa danh mục hồ sơ thất bại", ex);
            return Result.failure("Xóa danh mục hồ sơ thất bại: " + ex.getMessage());
        }
    }

    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
