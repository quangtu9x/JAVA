package com.td.infrastructure.categories;

import com.td.application.categories.UploadCategoryFileRequest;
import com.td.application.categories.UploadCategoryFileUseCase;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.categories.CategoryFileMetadata;
import com.td.domain.sharedcore.AppUser;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.controlplane.CategoryFileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

// BL-118: tải lên tệp đính kèm cho Category dùng chung (Banner/Footer/Hướng dẫn sử dụng) — cùng
// pattern UploadFileUseCaseImpl (Document) nhưng ghi metadata vào bảng control-plane
// (category_file_metadata, qua CategoryFileMetadataJpaRepository) thay vì file_metadata tenant-scoped.
// MinioService dùng CHUNG 1 bucket cho toàn app (không tenant-scoped) — an toàn tái sử dụng nguyên vẹn.
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadCategoryFileUseCaseImpl implements UploadCategoryFileUseCase {

    private final MinioService minioService;
    private final CategoryFileMetadataJpaRepository categoryFileMetadataRepo;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    @Override
    public Result<UUID> execute(UploadCategoryFileRequest request) {
        // Seed đặt tên vật lý trong MinIO — KHÔNG nhất thiết trùng id bản ghi metadata (entity tự
        // sinh id riêng trong constructor, cùng convention Category.java) — trả về đúng
        // saved.getId() ở cuối, không dùng nhầm seed này.
        String storedNameSeed = UUID.randomUUID().toString();
        String extension = extractExtension(request.getFileName());
        String storedName = storedNameSeed + (extension.isEmpty() ? "" : "." + extension);
        String objectPath = "categories/" + request.getCategoryId() + "/" + storedName;

        minioService.uploadObject(objectPath, request.getFileContent(),
                request.getFileSize() != null ? request.getFileSize() : -1,
                request.getMimeType());

        var entity = new CategoryFileMetadata(
                request.getCategoryId(),
                request.getFileName(),
                storedName,
                objectPath,
                request.getFileSize() != null ? request.getFileSize() : 0L,
                request.getMimeType(),
                extension.isEmpty() ? null : extension,
                minioService.getBucketName(),
                request.getDescription());
        entity.setCreatedBy(resolveCurrentUserId());

        var saved = categoryFileMetadataRepo.save(entity);
        log.info("Uploaded file {} for category {}", saved.getId(), request.getCategoryId());
        return Result.success(saved.getId());
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    // Cùng convention DeleteCategoryUseCase (BL-113): username từ token -> tra AppUser (tenant) lấy
    // UUID thật cho createdBy, null (không phải UUID ngẫu nhiên) khi không xác định được.
    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
