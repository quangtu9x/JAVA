package com.td.infrastructure.categories;

import com.td.application.categories.UploadCategoryFileRequest;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.categories.CategoryFileMetadata;
import com.td.domain.sharedcore.AppUser;
import com.td.infrastructure.config.MinioService;
import com.td.infrastructure.persistence.controlplane.CategoryFileMetadataJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadCategoryFileUseCaseImplTest {

    @Mock
    private MinioService minioService;
    @Mock
    private CategoryFileMetadataJpaRepository categoryFileMetadataRepo;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    @Test
    void execute_uploadsToMinioAndSavesMetadata_returnsSavedEntityId() {
        var useCase = new UploadCategoryFileUseCaseImpl(
            minioService, categoryFileMetadataRepo, currentUserProvider, appUserRepository);
        UUID categoryId = UUID.randomUUID();
        UUID appUserId = UUID.randomUUID();
        var request = UploadCategoryFileRequest.builder()
            .categoryId(categoryId)
            .fileContent(new ByteArrayInputStream("banner".getBytes()))
            .fileName("logo.png")
            .mimeType("image/png")
            .fileSize(6L)
            .description("Logo banner")
            .build();

        when(minioService.getBucketName()).thenReturn("td-bucket");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("skhcn"));
        var appUser = mock(AppUser.class);
        when(appUser.getId()).thenReturn(appUserId);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn")).thenReturn(Optional.of(appUser));
        when(categoryFileMetadataRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();

        var uploadedPath = ArgumentCaptor.forClass(String.class);
        verify(minioService).uploadObject(uploadedPath.capture(), any(), anyLong(), anyString());
        assertThat(uploadedPath.getValue()).startsWith("categories/" + categoryId + "/").endsWith(".png");

        var savedCaptor = ArgumentCaptor.forClass(CategoryFileMetadata.class);
        verify(categoryFileMetadataRepo).save(savedCaptor.capture());
        var saved = savedCaptor.getValue();
        assertThat(saved.getCategoryId()).isEqualTo(categoryId);
        assertThat(saved.getOriginalFilename()).isEqualTo("logo.png");
        assertThat(saved.getCreatedBy()).isEqualTo(appUserId);
        assertThat(result.getData()).isEqualTo(saved.getId());
    }
}
