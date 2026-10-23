package com.td.application.duthao;

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
 * BL-230 (DuThao Core) — DoD (1): tạo dự thảo qua {@link TaoDuThaoUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class TaoDuThaoUseCaseTest {

    private final com.td.application.documentlinks.InMemoryDocumentLinkRepository linkRepository = new com.td.application.documentlinks.InMemoryDocumentLinkRepository();

    @Mock
    private AddDuThaoDynamicDocumentUseCase addDuThaoDynamicDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private TaoDuThaoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TaoDuThaoUseCase(addDuThaoDynamicDocumentUseCase, currentUserProvider,
            com.td.application.documentlinks.InMemoryDocumentLinkRepository.newSnapshots(linkRepository),
            new DuThaoCoordinatorDelegations(com.td.application.sharedcore.UserDelegationTestSupport.emptyResolver()));
        // Bugfix 24/08/2026: khong phai test nao cung can stub nay (vd. khi request da tu gui san
        // user_creator) - lenient() de tranh UnnecessaryStubbingException.
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nhanvien01"));
    }

    @Test
    void execute_shouldDelegateToAddDuThaoDynamicDocumentUseCaseWithDuThaoDocumentTypeAndDraftingStatus() {
        UUID id = UUID.randomUUID();
        when(addDuThaoDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(id));

        var request = new TaoDuThaoRequest();
        request.setSubject("Du thao cong van");
        request.getFields().put("unit_in_charge", "So Noi Vu");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(id);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDuThaoDynamicDocumentUseCase).execute(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getDocumentType()).isEqualTo("duthao");
        assertThat(captured.getTitle()).isEqualTo("Du thao cong van");
        assertThat(captured.getStatus()).isEqualTo(DuThaoStatus.DRAFTING);
        assertThat(captured.getFields()).containsEntry("unit_in_charge", "So Noi Vu");
        // Bugfix 24/08/2026 (bao loi nguoi dung: GetView theo user_creator luon rong) - xem javadoc lop.
        assertThat(captured.getFields()).containsEntry("user_creator", "nhanvien01");
    }

    @Test
    void execute_clientSentUserCreator_shouldBeOverriddenByLoggedInUsername() {
        // 26/08/2026 (đảo chiều SERVER-AUTHORITATIVE): client KHÔNG được quyết định user_creator —
        // luôn ghi đè bằng username người đang đăng nhập.
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo1"));
        when(addDuThaoDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TaoDuThaoRequest();
        request.setSubject("Du thao sao chep");
        request.getFields().put("user_creator", "nguoikhac");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDuThaoDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("user_creator", "demo1");
    }
    @Test
    void execute_demo1DangNhapVaClientGuiUserCreatorSkhcn_phaiLuuLaDemo1() {
        // Kịch bản kiểm tra 26/08/2026 (yêu cầu đảo chiều): đăng nhập demo1 nhưng
        // POST /api/v1/duthao/TaoDuThao gửi user_creator=skhcn -> PHẢI lưu "demo1" (danh tính
        // người tạo thật), tuyệt đối KHÔNG lưu "skhcn" vào dynamic_fields.user_creator. Ai soạn hộ
        // vẫn truy vết được qua cột audit created_by (AuditableEntityListener) — độc lập với field này.
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo1"));
        when(addDuThaoDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TaoDuThaoRequest();
        request.setSubject("Du thao soan ho cho lanh dao");
        request.getFields().put("user_creator", "skhcn");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDuThaoDynamicDocumentUseCase).execute(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getFields()).containsEntry("user_creator", "demo1");
        assertThat(captured.getFields().get("user_creator")).isNotEqualTo("skhcn");
    }

    @Test
    void execute_withoutCurrentUser_shouldFallBackToSystemUsername() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        when(addDuThaoDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TaoDuThaoRequest();
        request.setSubject("Du thao test");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDuThaoDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("user_creator", "system");
    }

    @Test
    void execute_withExplicitStatus_shouldNotOverrideWithDrafting() {
        when(addDuThaoDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new TaoDuThaoRequest();
        request.setSubject("Van ban test");
        request.setStatus("CUSTOM");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDuThaoDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CUSTOM");
    }
}
