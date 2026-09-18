package com.td.application.documents;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.sharedcore.UserDisplayInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 09/09/2026 (yêu cầu người dùng "log xem tệp đính kèm cần thiết cho các module khác nữa... tối ưu
 * lưu trữ"): test {@link RecordFileAccessUseCase} — xem javadoc lớp đó.
 */
@ExtendWith(MockitoExtension.class)
class RecordFileAccessUseCaseTest {

    @Mock
    private GetFileUseCase getFileUseCase;
    @Mock
    private FileAccessLogRepository fileAccessLogRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserDisplayInfoService userDisplayInfoService;

    private RecordFileAccessUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordFileAccessUseCase(
            getFileUseCase, fileAccessLogRepository, currentUserProvider, userDisplayInfoService);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nhanvien01"));
        lenient().when(userDisplayInfoService.resolve("nhanvien01"))
            .thenReturn(new UserDisplayInfoService.UserDisplayInfo("Nguyen Van A", "So KH&CN / Phong TH"));
    }

    private RecordFileAccessRequest request(UUID documentId, UUID fileId, String module, String action) {
        var request = new RecordFileAccessRequest();
        request.setDocumentId(documentId);
        request.setFileId(fileId);
        request.setModule(module);
        request.setAction(action);
        return request;
    }

    @Test
    void execute_view_shouldRecordAccessWithServerResolvedFileNameAndIdentity() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        when(getFileUseCase.execute(DownloadFileRequest.builder().documentId(documentId).fileId(fileId).build()))
            .thenReturn(Result.success(FileDto.builder()
                .fileId(fileId).documentId(documentId)
                .fileName("stored-1.pdf").originalFileName("Cong van 12.pdf").build()));
        when(fileAccessLogRepository.recordAccess("vbdi", documentId, fileId, "Cong van 12.pdf", "view",
            "nhanvien01", "Nguyen Van A", "So KH&CN / Phong TH")).thenReturn(logId);

        var result = useCase.execute(request(documentId, fileId, "vbdi", "view"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(logId);
    }

    @Test
    void execute_downloadActionCaseInsensitive_shouldNormalizeToLowercase() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(getFileUseCase.execute(any())).thenReturn(Result.success(FileDto.builder()
            .fileId(fileId).documentId(documentId).fileName("a.pdf").build()));
        when(fileAccessLogRepository.recordAccess(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(UUID.randomUUID());

        var result = useCase.execute(request(documentId, fileId, "vbden", "DOWNLOAD"));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileAccessLogRepository).recordAccess(any(), any(), any(), any(), actionCaptor.capture(), any(), any(), any());
        assertThat(actionCaptor.getValue()).isEqualTo("download");
    }

    @Test
    void execute_withInvalidAction_shouldFailWithoutQuerying() {
        var result = useCase.execute(request(UUID.randomUUID(), UUID.randomUUID(), "vbdi", "xoa"));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(getFileUseCase, fileAccessLogRepository);
    }

    @Test
    void execute_whenFileNotFound_shouldFailWithoutRecording() {
        UUID documentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(getFileUseCase.execute(any())).thenReturn(Result.failure("File khong tim thay"));

        var result = useCase.execute(request(documentId, fileId, "vbdi", "view"));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(fileAccessLogRepository);
    }

    @Test
    void execute_withoutModule_shouldFailWithoutQuerying() {
        var result = useCase.execute(request(UUID.randomUUID(), UUID.randomUUID(), "", "view"));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(getFileUseCase, fileAccessLogRepository);
    }

    @Test
    void execute_withoutDocumentIdOrFileId_shouldFailWithoutQuerying() {
        var result = useCase.execute(request(null, null, "vbdi", "view"));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(getFileUseCase, fileAccessLogRepository);
    }
}
