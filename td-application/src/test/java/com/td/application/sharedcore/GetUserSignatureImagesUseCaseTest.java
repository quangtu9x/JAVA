package com.td.application.sharedcore;

import com.td.application.common.models.PaginationResponse;
import com.td.application.documents.FileDto;
import com.td.application.identityfiles.ListIdentityFilesUseCase;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserSignatureImagesUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ListIdentityFilesUseCase listIdentityFilesUseCase;

    private GetUserSignatureImagesUseCase newUseCase() {
        return new GetUserSignatureImagesUseCase(appUserRepository, listIdentityFilesUseCase);
    }

    @Test
    void execute_withUnknownUser_shouldFail() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findByIdAndDeletedOnIsNull(userId)).thenReturn(Optional.empty());

        var result = newUseCase().execute(userId);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNoImagesUploaded_shouldReturnBothNull() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findByIdAndDeletedOnIsNull(userId))
            .thenReturn(Optional.of(org.mockito.Mockito.mock(AppUser.class)));
        when(listIdentityFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 0, 0L, 1, true, true));

        var result = newUseCase().execute(userId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getOfficial()).isNull();
        assertThat(result.getData().getDraft()).isNull();
    }

    @Test
    void execute_withOnlyOfficialImageUploaded_shouldReturnOfficialOnly() {
        UUID userId = UUID.randomUUID();
        UUID officialDocumentId = UserSignatureImageDocumentIdResolver.resolve(userId, UserSignatureImageType.OFFICIAL);
        UUID draftDocumentId = UserSignatureImageDocumentIdResolver.resolve(userId, UserSignatureImageType.DRAFT);
        when(appUserRepository.findByIdAndDeletedOnIsNull(userId))
            .thenReturn(Optional.of(org.mockito.Mockito.mock(AppUser.class)));

        FileDto officialFile = FileDto.builder()
            .fileId(UUID.randomUUID())
            .documentId(officialDocumentId)
            .originalFileName("chu-ky-chinh.png")
            .fileSize(2048L)
            .uploadDate(LocalDateTime.now())
            .isPrimary(true)
            .build();
        when(listIdentityFilesUseCase.execute(officialDocumentId))
            .thenReturn(new PaginationResponse<>(List.of(officialFile), 0, 1, 1L, 1, true, true));
        when(listIdentityFilesUseCase.execute(draftDocumentId))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 0, 0L, 1, true, true));

        var result = newUseCase().execute(userId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getOfficial()).isNotNull();
        assertThat(result.getData().getOfficial().getFileName()).isEqualTo("chu-ky-chinh.png");
        assertThat(result.getData().getOfficial().getDocumentId()).isEqualTo(officialDocumentId);
        assertThat(result.getData().getDraft()).isNull();
    }

    @Test
    void execute_shouldIgnoreDemotedNonPrimaryFiles() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UserSignatureImageDocumentIdResolver.resolve(userId, UserSignatureImageType.OFFICIAL);
        when(appUserRepository.findByIdAndDeletedOnIsNull(userId))
            .thenReturn(Optional.of(org.mockito.Mockito.mock(AppUser.class)));

        FileDto oldDemoted = FileDto.builder()
            .fileId(UUID.randomUUID())
            .documentId(documentId)
            .originalFileName("cu.png")
            .uploadDate(LocalDateTime.now().minusDays(1))
            .isPrimary(false)
            .build();
        FileDto current = FileDto.builder()
            .fileId(UUID.randomUUID())
            .documentId(documentId)
            .originalFileName("moi.png")
            .uploadDate(LocalDateTime.now())
            .isPrimary(true)
            .build();
        when(listIdentityFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(oldDemoted, current), 0, 2, 2L, 1, true, true));

        var result = newUseCase().execute(userId);

        assertThat(result.getData().getOfficial().getFileName()).isEqualTo("moi.png");
    }
}
