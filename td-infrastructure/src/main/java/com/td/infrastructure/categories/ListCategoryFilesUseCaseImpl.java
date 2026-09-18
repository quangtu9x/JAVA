package com.td.infrastructure.categories;

import com.td.application.categories.CategoryFileDto;
import com.td.application.categories.ListCategoryFilesUseCase;
import com.td.application.common.models.PaginationResponse;
import com.td.infrastructure.persistence.controlplane.CategoryFileMetadataJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListCategoryFilesUseCaseImpl implements ListCategoryFilesUseCase {

    private final CategoryFileMetadataJpaRepository categoryFileMetadataRepo;

    @Override
    public PaginationResponse<CategoryFileDto> execute(UUID categoryId) {
        List<CategoryFileDto> items = categoryFileMetadataRepo
                .findAllByCategoryIdAndDeletedOnIsNull(categoryId).stream()
                .map(e -> CategoryFileDto.builder()
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
                        .build())
                .collect(Collectors.toList());

        return new PaginationResponse<>(items, 0, items.size(), (long) items.size(), 1, true, true);
    }
}
