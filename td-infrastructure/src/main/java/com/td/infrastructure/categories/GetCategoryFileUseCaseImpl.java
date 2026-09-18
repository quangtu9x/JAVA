package com.td.infrastructure.categories;

import com.td.application.categories.CategoryFileDto;
import com.td.application.categories.DownloadCategoryFileRequest;
import com.td.application.categories.GetCategoryFileUseCase;
import com.td.application.common.models.Result;
import com.td.infrastructure.persistence.controlplane.CategoryFileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetCategoryFileUseCaseImpl implements GetCategoryFileUseCase {

    private final CategoryFileMetadataJpaRepository categoryFileMetadataRepo;

    @Override
    public Result<CategoryFileDto> execute(DownloadCategoryFileRequest request) {
        var opt = categoryFileMetadataRepo
                .findByIdAndCategoryIdAndDeletedOnIsNull(request.getFileId(), request.getCategoryId());
        if (opt.isEmpty()) {
            return Result.failure("File không tìm thấy");
        }
        var e = opt.get();
        return Result.success(CategoryFileDto.builder()
                .fileId(e.getId())
                .categoryId(e.getCategoryId())
                .fileName(e.getStoredFilename())
                .originalFileName(e.getOriginalFilename())
                .fileSize(e.getFileSize())
                .mimeType(e.getContentType())
                .uploadDate(e.getCreatedOn())
                .uploadedBy(e.getCreatedBy() != null ? e.getCreatedBy().toString() : null)
                .storagePath(e.getFilePath())
                .description(e.getDescription())
                .build());
    }
}
