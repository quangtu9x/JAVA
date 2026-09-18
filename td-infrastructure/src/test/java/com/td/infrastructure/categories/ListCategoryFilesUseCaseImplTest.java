package com.td.infrastructure.categories;

import com.td.domain.categories.CategoryFileMetadata;
import com.td.infrastructure.persistence.controlplane.CategoryFileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListCategoryFilesUseCaseImplTest {

    @Mock
    private CategoryFileMetadataJpaRepository categoryFileMetadataRepo;

    @Test
    void execute_returnsMappedDtos_forActiveFilesOnly() {
        var useCase = new ListCategoryFilesUseCaseImpl(categoryFileMetadataRepo);
        UUID categoryId = UUID.randomUUID();
        var entity = new CategoryFileMetadata(categoryId, "banner.png", "abc.png",
            "categories/" + categoryId + "/abc.png", 1024L, "image/png", "png", "td-bucket", "mo ta");
        when(categoryFileMetadataRepo.findAllByCategoryIdAndDeletedOnIsNull(categoryId))
            .thenReturn(List.of(entity));

        var response = useCase.execute(categoryId);

        assertThat(response.getItems()).hasSize(1);
        var dto = response.getItems().get(0);
        assertThat(dto.getFileId()).isEqualTo(entity.getId());
        assertThat(dto.getCategoryId()).isEqualTo(categoryId);
        assertThat(dto.getOriginalFileName()).isEqualTo("banner.png");
        assertThat(dto.getStoragePath()).isEqualTo(entity.getFilePath());
        assertThat(dto.getFileSize()).isEqualTo(1024L);
    }

    @Test
    void execute_noFiles_returnsEmptyList() {
        var useCase = new ListCategoryFilesUseCaseImpl(categoryFileMetadataRepo);
        UUID categoryId = UUID.randomUUID();
        when(categoryFileMetadataRepo.findAllByCategoryIdAndDeletedOnIsNull(categoryId))
            .thenReturn(List.of());

        var response = useCase.execute(categoryId);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalItems()).isZero();
    }
}
