package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.documents.UploadFileRequest;
import com.td.application.identityfiles.UploadIdentityFileUseCase;
import com.td.domain.sharedcore.AppUser;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadUserSignatureImageUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private UploadIdentityFileUseCase uploadIdentityFileUseCase;

    private UploadUserSignatureImageUseCase newUseCase() {
        return new UploadUserSignatureImageUseCase(appUserRepository, uploadIdentityFileUseCase);
    }

    private UploadUserSignatureImageRequest.UploadUserSignatureImageRequestBuilder validRequest(UUID userId) {
        return UploadUserSignatureImageRequest.builder()
            .userId(userId)
            .type(UserSignatureImageType.OFFICIAL)
            .fileContent(new ByteArrayInputStream("anh".getBytes()))
            .fileName("chu-ky.png")
            .mimeType("image/png")
            .fileSize(1024L);
    }

    @Test
    void execute_withUnknownUser_shouldFail() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findByIdAndDeletedOnIsNull(userId)).thenReturn(Optional.empty());

        var result = newUseCase().execute(validRequest(userId).build());

        assertThat(result.isSuccess()).isFalse();
        verify(uploadIdentityFileUseCase, never()).execute(any());
    }

    @Test
    void execute_withFileOverSizeLimit_shouldFail() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findByIdAndDeletedOnIsNull(userId))
            .thenReturn(Optional.of(mockUser()));

        var result = newUseCase().execute(validRequest(userId).fileSize(1024 * 1024L + 1).build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("1MB");
        verify(uploadIdentityFileUseCase, never()).execute(any());
    }

    @Test
    void execute_withNonImageMimeType_shouldFail() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findByIdAndDeletedOnIsNull(userId))
            .thenReturn(Optional.of(mockUser()));

        var result = newUseCase().execute(validRequest(userId).mimeType("application/pdf").build());

        assertThat(result.isSuccess()).isFalse();
        verify(uploadIdentityFileUseCase, never()).execute(any());
    }

    @Test
    void execute_withDifferentTypes_shouldResolveDifferentDocumentIds() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findByIdAndDeletedOnIsNull(userId))
            .thenReturn(Optional.of(mockUser()));
        when(uploadIdentityFileUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        newUseCase().execute(validRequest(userId).type(UserSignatureImageType.OFFICIAL).build());
        newUseCase().execute(validRequest(userId).type(UserSignatureImageType.DRAFT).build());

        ArgumentCaptor<UploadFileRequest> captor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(uploadIdentityFileUseCase, org.mockito.Mockito.times(2)).execute(captor.capture());

        var requests = captor.getAllValues();
        assertThat(requests.get(0).getDocumentId()).isNotEqualTo(requests.get(1).getDocumentId());
        assertThat(requests.get(0).getIsPrimary()).isTrue();
        assertThat(requests.get(0).getModule()).isEqualTo("user-signature");
    }

    @Test
    void execute_calledTwiceWithSameUserAndType_shouldResolveSameDocumentId() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findByIdAndDeletedOnIsNull(userId))
            .thenReturn(Optional.of(mockUser()));
        when(uploadIdentityFileUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        newUseCase().execute(validRequest(userId).build());
        newUseCase().execute(validRequest(userId).build());

        ArgumentCaptor<UploadFileRequest> captor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(uploadIdentityFileUseCase, org.mockito.Mockito.times(2)).execute(captor.capture());

        var requests = captor.getAllValues();
        assertThat(requests.get(0).getDocumentId()).isEqualTo(requests.get(1).getDocumentId());
    }

    // Use case chỉ kiểm tra Optional.isEmpty() - không đọc field nào của AppUser, nên mock trơn là đủ
    // (tránh UnnecessaryStubbingException của Mockito strict stubs nếu stub thêm getter không dùng tới).
    private AppUser mockUser() {
        return org.mockito.Mockito.mock(AppUser.class);
    }
}
