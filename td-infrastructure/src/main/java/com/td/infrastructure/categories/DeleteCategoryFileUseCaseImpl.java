package com.td.infrastructure.categories;

import com.td.application.categories.DeleteCategoryFileRequest;
import com.td.application.categories.DeleteCategoryFileUseCase;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.controlplane.CategoryFileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

// BL-118: xóa mềm tệp đính kèm Category (giữ đúng convention soft-delete của Category/AuditableEntity
// — xem CategoryFileMetadataJpaRepository dùng ...AndDeletedOnIsNull) — object vật lý trong MinIO XÓA
// THẬT NGAY (không cần giữ lại, khác với bản ghi metadata vẫn còn cho mục đích audit).
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteCategoryFileUseCaseImpl implements DeleteCategoryFileUseCase {

    private final CategoryFileMetadataJpaRepository categoryFileMetadataRepo;
    private final MinioService minioService;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    @Override
    public Result<UUID> execute(DeleteCategoryFileRequest request) {
        var opt = categoryFileMetadataRepo
                .findByIdAndCategoryIdAndDeletedOnIsNull(request.getFileId(), request.getCategoryId());
        if (opt.isEmpty()) {
            return Result.failure("File không tìm thấy");
        }
        var entity = opt.get();
        minioService.removeObject(entity.getFilePath());
        entity.markAsDeleted(resolveCurrentUserId());
        categoryFileMetadataRepo.save(entity);
        log.info("Deleted file {} from category {}", request.getFileId(), request.getCategoryId());
        return Result.success(request.getFileId());
    }

    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
