package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.domain.duthao.DuThaoDocument;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DT-007 (tiếp, 26/08/2026) — port nhánh "xử lý đính kèm" của {@code ChiTietPage.jsx#onFinish}
 * qua {@link GanKemTepDuThaoUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GanKemTepDuThaoUseCaseTest {

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private GanKemTepDuThaoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GanKemTepDuThaoUseCase(
            duThaoDocumentRepository, addDraftProcessDocumentUseCase, currentUserProvider);
    }

    @Test
    void execute_withUnknownDraft_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new GanKemTepDuThaoRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(addDraftProcessDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_withDeletedDraft_shouldReturnFailure() {
        DuThaoDocument draft = newDraft();
        draft.markAsDeleted(null);
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var request = new GanKemTepDuThaoRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(addDraftProcessDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_shouldCreateDraftProcessRecordWithServerResolvedCreator() {
        DuThaoDocument draft = newDraft();
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        UUID processId = UUID.randomUUID();
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(processId));

        var request = new GanKemTepDuThaoRequest();
        request.setId(draft.getId());
        request.setOpinion("Bo sung tep phieu trinh");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(processId);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("parentid", draft.getId().toString());
        assertThat(captor.getValue().getFields()).containsEntry("action_type", "gankemtep");
        assertThat(captor.getValue().getFields()).containsEntry("opinion", "Bo sung tep phieu trinh");
        // SERVER-AUTHORITATIVE: "creator" phai la username tu CurrentUserProvider, KHONG phai ten
        // hien thi client co the gui (Domino goc gui currentUser.name) - xem javadoc use case.
        assertThat(captor.getValue().getFields()).containsEntry("creator", "nguyenvana");
    }

    @Test
    void execute_withoutOpinion_shouldNotSendOpinionField() {
        DuThaoDocument draft = newDraft();
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new GanKemTepDuThaoRequest();
        request.setId(draft.getId());
        // Chi dinh kem tep, khong go y kien - ykien rong ("" tu form) hoac null deu khong duoc gui.

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("opinion");
    }

    @Test
    void execute_withoutCurrentUser_shouldFallBackToSystemUsername() {
        DuThaoDocument draft = newDraft();
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new GanKemTepDuThaoRequest();
        request.setId(draft.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("creator", "system");
    }

    private DuThaoDocument newDraft() {
        return new DuThaoDocument("Du thao", "duthao", DuThaoStatus.DRAFTING, null, null, null, null);
    }
}
