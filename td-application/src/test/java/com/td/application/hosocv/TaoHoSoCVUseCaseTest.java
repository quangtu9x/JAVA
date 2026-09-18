package com.td.application.hosocv;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
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
import static org.mockito.Mockito.when;

/**
 * BL-240 (HoSoCV Core) — DoD (1): tạo hồ sơ công việc qua {@link TaoHoSoCVUseCase}.
 *
 * <p>Mở rộng: đổi mock từ {@code AddDynamicDocumentUseCase} (bảng {@code documents} chung) sang
 * {@link AddHoSoCVDynamicDocumentUseCase} (bảng riêng {@code hosocv_documents}), thêm test cho
 * bugfix stamp {@code user_creator} SERVER-AUTHORITATIVE (đúng khuôn {@code TaoDuThaoUseCaseTest}).
 */
@ExtendWith(MockitoExtension.class)
class TaoHoSoCVUseCaseTest {

    @Mock
    private AddHoSoCVDynamicDocumentUseCase addHoSoCVDynamicDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private TaoHoSoCVUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TaoHoSoCVUseCase(addHoSoCVDynamicDocumentUseCase, currentUserProvider);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nhanvien01"));
    }

    @Test
    void execute_shouldDelegateToAddHoSoCVDynamicDocumentUseCaseWithHoSoCVDocumentTypeAndDangXuLyStatus() {
        UUID id = UUID.randomUUID();
        when(addHoSoCVDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(id));

        var request = new TaoHoSoCVRequest();
        request.setTitle("Ho so xu ly khieu nai");
        request.getFields().put("category", "Hanh chinh");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(id);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addHoSoCVDynamicDocumentUseCase).execute(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getDocumentType()).isEqualTo("hosocv");
        assertThat(captured.getTitle()).isEqualTo("Ho so xu ly khieu nai");
        assertThat(captured.getStatus()).isEqualTo(HoSoCVStatus.DANG_XU_LY);
        assertThat(captured.getFields()).containsEntry("category", "Hanh chinh");
        assertThat(captured.getFields()).containsEntry("user_creator", "nhanvien01");
    }

    @Test
    void execute_clientSentUserCreator_shouldBeOverriddenByLoggedInUsername() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo1"));
        when(addHoSoCVDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TaoHoSoCVRequest();
        request.setTitle("Ho so gia mao nguoi tao");
        request.getFields().put("user_creator", "nguoikhac");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addHoSoCVDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("user_creator", "demo1");
    }

    @Test
    void execute_withoutCurrentUser_shouldFallBackToSystemUsername() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        when(addHoSoCVDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TaoHoSoCVRequest();
        request.setTitle("Ho so test");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addHoSoCVDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("user_creator", "system");
    }

    @Test
    void execute_withExplicitStatus_shouldNotOverrideWithDangXuLy() {
        when(addHoSoCVDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TaoHoSoCVRequest();
        request.setTitle("Ho so test");
        request.setStatus("1");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addHoSoCVDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("1");
    }
}
