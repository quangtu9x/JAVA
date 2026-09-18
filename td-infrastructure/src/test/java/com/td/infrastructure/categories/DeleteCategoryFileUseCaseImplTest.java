package com.td.infrastructure.categories;

import com.td.application.categories.DeleteCategoryFileRequest;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.categories.CategoryFileMetadata;
import com.td.domain.sharedcore.AppUser;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.controlplane.CategoryFileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteCategoryFileUseCaseImplTest {

    @Mock
    private CategoryFileMetadataJpaRepository categoryFileMetadataRepo;
    @Mock
    private MinioService minioService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    @Test
    void execute_fileExists_removesFromMinioAndSoftDeletes() {
        var useCase = new DeleteCategoryFileUseCaseImpl(
            categoryFileMetadataRepo, minioService, currentUserProvider, appUserRepository);
        UUID categoryId = UUID.randomUUID();
        var entity = new CategoryFileMetadata(categoryId, "banner.png", "abc.png",
            "categories/" + categoryId + "/abc.png", 1024L, "image/png", "png", "td-bucket", null);
        UUID appUserId = UUID.randomUUID();
        var appUser = mock(AppUser.class);
        when(appUser.getId()).thenReturn(appUserId);
        when(categoryFileMetadataRepo.findByIdAndCategoryIdAndDeletedOnIsNull(entity.getId(), categoryId))
            .thenReturn(Optional.of(entity));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("skhcn"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn")).thenReturn(Optional.of(appUser));

        var request = DeleteCategoryFileRequest.builder().categoryId(categoryId).fileId(entity.getId()).build();
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(entity.getId());
        verify(minioService).removeObject(entity.getFilePath());
        assertThat(entity.isDeleted()).isTrue();
        assertThat(entity.getDeletedBy()).isEqualTo(appUserId);
        verify(categoryFileMetadataRepo).save(entity);
    }

    @Test
    void execute_fileNotFound_returnsFailure_doesNotTouchMinio() {
        var useCase = new DeleteCategoryFileUseCaseImpl(
            categoryFileMetadataRepo, minioService, currentUserProvider, appUserRepository);
        UUID categoryId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(categoryFileMetadataRepo.findByIdAndCategoryIdAndDeletedOnIsNull(fileId, categoryId))
            .thenReturn(Optional.empty());

        var request = DeleteCategoryFileRequest.builder().categoryId(categoryId).fileId(fileId).build();
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(minioService, never()).removeObject(org.mockito.ArgumentMatchers.anyString());
    }
}
