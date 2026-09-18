package com.td.application.vbdi;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.domain.vbdi.VbdiDocument;
import com.td.domain.vbdi.VbdiUserActionLog;
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
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 30/08/2026 (port {@code qlvb/vbdi?...&action=loguseraction}): test
 * {@link AddVbdiUserActionLogUseCase} ghi vết vào {@code vbdi_user_action_logs}.
 */
@ExtendWith(MockitoExtension.class)
class AddVbdiUserActionLogUseCaseTest {

    @Mock
    private VbdiUserActionLogRepository vbdiUserActionLogRepository;
    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;

    private AddVbdiUserActionLogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddVbdiUserActionLogUseCase(
            vbdiUserActionLogRepository, vbdiDocumentRepository, currentUserProvider, documentLogService);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xuanlt.skhcn"));
    }

    private VbdiDocument existingVbdi(String documentType) {
        return new VbdiDocument("Cong van", documentType, VbdiStatus.ISSUED, null, null, null, null);
    }

    private AddVbdiUserActionLogRequest request(UUID documentId) {
        var request = new AddVbdiUserActionLogRequest();
        request.setDocumentId(documentId);
        request.setActionType("guivanban");
        request.setFullname("Le Thi Xuan");
        request.setUnit("Van phong so");
        request.setIdentifier("H56.07.01");
        return request;
    }

    @Test
    void execute_shouldSaveEntryWithServerStampedUsername() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdiUserActionLog(document.getId(), "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "guivanban");
        when(vbdiUserActionLogRepository.save(any())).thenReturn(saved);

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<VbdiUserActionLog> captor = ArgumentCaptor.forClass(VbdiUserActionLog.class);
        verify(vbdiUserActionLogRepository).save(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getParentId()).isEqualTo(document.getId());
        assertThat(entity.getActionType()).isEqualTo("guivanban");
        assertThat(entity.getFullname()).isEqualTo("Le Thi Xuan");
        // Danh tinh thuc do server gan (SERVER-AUTHORITATIVE), khong tin actionType/fullname client
        // gui de xac dinh AI - chi username la security-relevant.
        assertThat(entity.getUsername()).isEqualTo("xuanlt.skhcn");
        verify(documentLogService).logCreate(eq(saved.getId()), anyMap());
    }

    @Test
    void execute_whenDocumentMissing_shouldFail() {
        UUID documentId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(documentId)).thenReturn(Optional.empty());

        var result = useCase.execute(request(documentId));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
        verifyNoInteractions(vbdiUserActionLogRepository);
    }

    @Test
    void execute_whenDocumentWrongType_shouldFail() {
        var document = existingVbdi("vbdi_res");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(vbdiUserActionLogRepository);
    }

    @Test
    void execute_whenDocumentIdMissing_shouldFailWithoutQuerying() {
        var result = useCase.execute(new AddVbdiUserActionLogRequest());

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(vbdiDocumentRepository);
        verifyNoInteractions(vbdiUserActionLogRepository);
    }

    @Test
    void execute_whenActionTypeMissing_shouldFail() {
        var document = existingVbdi("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new AddVbdiUserActionLogRequest();
        request.setDocumentId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(vbdiUserActionLogRepository);
    }
}
