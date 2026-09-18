package com.td.infrastructure.categories;

import com.td.application.categories.DownloadCategoryFileRequest;
import com.td.domain.categories.CategoryFileMetadata;
import com.td.infrastructure.persistence.controlplane.CategoryFileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCategoryFileUseCaseImplTest {

    @Mock
    private CategoryFileMetadataJpaRepository categoryFileMetadataRepo;

    @Test
    void execute_fileExists_returnsSuccessWithDto() {
        var useCase = new GetCategoryFileUseCaseImpl(categoryFileMetadataRepo);
        UUID categoryId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        var entity = new CategoryFileMetadata(categoryId, "huongdan.pdf", "xyz.pdf",
            "categories/" + categoryId + "/xyz.pdf", 2048L, "application/pdf", "pdf", "td-bucket", null);
        when(categoryFileMetadataRepo.findByIdAndCategoryIdAndDeletedOnIsNull(fileId, categoryId))
            .thenReturn(Optional.of(entity));

        var request = DownloadCategoryFileRequest.builder().categoryId(categoryId).fileId(fileId).build();
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getOriginalFileName()).isEqualTo("huongdan.pdf");
        assertThat(result.getData().getStoragePath()).isEqualTo(entity.getFilePath());
    }

    @Test
    void execute_fileNotFound_returnsFailure() {
        var useCase = new GetCategoryFileUseCaseImpl(categoryFileMetadataRepo);
        UUID categoryId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(categoryFileMetadataRepo.findByIdAndCategoryIdAndDeletedOnIsNull(fileId, categoryId))
            .thenReturn(Optional.empty());

        var request = DownloadCategoryFileRequest.builder().categoryId(categoryId).fileId(fileId).build();
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("không tìm thấy");
    }
}
