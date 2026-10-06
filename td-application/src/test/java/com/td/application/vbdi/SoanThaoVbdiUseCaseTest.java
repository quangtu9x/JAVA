package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.documentregister.DocumentRegisterRepository;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.domain.documentregister.DocumentRegister;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BL-220 (VBDi Core) — DoD (1): soạn thảo văn bản đi qua {@link SoanThaoVbdiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class SoanThaoVbdiUseCaseTest {

    private final com.td.application.documentlinks.InMemoryDocumentLinkRepository linkRepository = new com.td.application.documentlinks.InMemoryDocumentLinkRepository();

    @Mock
    private AddVbdiDynamicDocumentUseCase addVbdiDynamicDocumentUseCase;
    @Mock
    private DocumentRegisterRepository documentRegisterRepository;

    private SoanThaoVbdiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SoanThaoVbdiUseCase(addVbdiDynamicDocumentUseCase, documentRegisterRepository,
            com.td.application.documentlinks.InMemoryDocumentLinkRepository.newSnapshots(linkRepository));
    }

    @Test
    void execute_shouldDelegateToAddDynamicDocumentUseCaseWithVbdiDocumentTypeAndDraftStatus() {
        UUID id = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(id));

        var request = new SoanThaoVbdiRequest();
        request.setSubject("Cong van ve viec kiem tra");
        request.getFields().put("unit_in_charge", "So Noi Vu");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(id);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdiDynamicDocumentUseCase).execute(captor.capture());
        var captured = captor.getValue();
        assertThat(captured.getDocumentType()).isEqualTo("vbdi");
        assertThat(captured.getTitle()).isEqualTo("Cong van ve viec kiem tra");
        assertThat(captured.getStatus()).isEqualTo(VbdiStatus.DRAFT);
        assertThat(captured.getFields()).containsEntry("unit_in_charge", "So Noi Vu");
    }

    @Test
    void execute_withExplicitStatus_shouldNotOverrideWithDraft() {
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SoanThaoVbdiRequest();
        request.setSubject("Van ban test");
        request.setStatus("CUSTOM");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdiDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CUSTOM");
    }

    // Bugfix 28/08/2026 (phat hien khi port route FE "vbdi/xuly/cq_toanboso"): ChiTietModal.jsx that
    // (onFinish, nhanh tao moi) LUON gui status="2" (ma Domino tho) - khong map se bi luu nguyen van
    // "2", khong bao gio khop VbdiStatus.ISSUED o moi query/guard khac - xem javadoc SoanThaoVbdiUseCase.
    @Test
    void execute_withLegacyStatusCode2_shouldMapToIssued() {
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SoanThaoVbdiRequest();
        request.setSubject("Van ban da ban hanh tu ChiTietModal");
        request.setStatus("2");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdiDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(VbdiStatus.ISSUED);
    }

    @Test
    void execute_withLegacyStatusCode0_shouldMapToDraft() {
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SoanThaoVbdiRequest();
        request.setSubject("Van ban cho vao so");
        request.setStatus("0");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdiDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(VbdiStatus.DRAFT);
    }

    // 28/08/2026 ("Chon so van ban", so quan ly TAP TRUNG cung bang document_registers voi van ban
    // den, phan biet bang documentRegisterType) - mirror TiepNhanVbdenUseCase#applyDocumentRegisterNumbering.
    private DocumentRegister newRegister(String name, int codeStartNumber, boolean autoIncrement, String codeStartNumbers) {
        return new DocumentRegister(name, SoanThaoVbdiUseCase.REGISTER_TYPE, 2026, "H05.01",
            null, null, null, null, null, null, null, null,
            codeStartNumber, autoIncrement, true, codeStartNumbers);
    }

    @Test
    void execute_withDocumentRegisterId_shouldNumberFromRegisterAndRecordIssuedNumber() {
        var register = newRegister("So noi bo", 10, true, "8;9");
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId()))
            .thenReturn(Optional.of(register));
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SoanThaoVbdiRequest();
        request.setSubject("Cong van test so");
        request.setDocumentRegisterId(register.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRegisterRepository).save(register);
        assertThat(register.getCodeStartNumber()).isEqualTo(11); // 10 (peekNextNumber) + 1
        assertThat(register.getCodeStartNumbers()).isEqualTo("8;9;10");

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdiDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields())
            .containsEntry("document_number", "10")
            .containsEntry("document_register", "So noi bo")
            .containsEntry("document_register_id", register.getId().toString());
    }

    @Test
    void execute_withDocumentRegisterId_whenRegisterMissing_shouldFailWithoutCreatingDocument() {
        UUID registerId = UUID.randomUUID();
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(registerId)).thenReturn(Optional.empty());

        var request = new SoanThaoVbdiRequest();
        request.setSubject("Cong van test");
        request.setDocumentRegisterId(registerId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Không tìm thấy sổ văn bản");
        verifyNoInteractions(addVbdiDynamicDocumentUseCase);
    }

    @Test
    void execute_withDocumentRegisterId_whenRegisterNotAutoIncrementAndNoManualNumber_shouldFail() {
        var register = newRegister("So thu cong", 1, false, null);
        when(documentRegisterRepository.findByIdAndDeletedOnIsNull(register.getId()))
            .thenReturn(Optional.of(register));

        var request = new SoanThaoVbdiRequest();
        request.setSubject("Cong van test");
        request.setDocumentRegisterId(register.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("không tự động đánh số");
        verify(documentRegisterRepository, never()).save(any());
        verifyNoInteractions(addVbdiDynamicDocumentUseCase);
    }

    @Test
    void execute_withoutDocumentRegisterId_shouldNotTouchRegisterRepository() {
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new SoanThaoVbdiRequest();
        request.setSubject("Cong van khong chon so");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(documentRegisterRepository);
    }
}
