package com.td.application.vbden;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.vbden.VbdenUserActionLog;
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
 * 18/09/2026 (yêu cầu người dùng "thêm vết audit của văn bản đến"): test
 * {@link AddVbdenUserActionLogUseCase} ghi vết vào {@code vbden_user_action_logs} — mirror
 * {@code com.td.application.vbdi.AddVbdiUserActionLogUseCaseTest}.
 */
@ExtendWith(MockitoExtension.class)
class AddVbdenUserActionLogUseCaseTest {

    @Mock
    private VbdenUserActionLogRepository vbdenUserActionLogRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;

    private AddVbdenUserActionLogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddVbdenUserActionLogUseCase(
            vbdenUserActionLogRepository, documentRepository, currentUserProvider, documentLogService);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xuanlt.skhcn"));
    }

    private BusinessDocument existingVbden(String documentType) {
        return new BusinessDocument("Van ban den", documentType, "1", null, null, null, null);
    }

    private AddVbdenUserActionLogRequest request(UUID documentId) {
        var request = new AddVbdenUserActionLogRequest();
        request.setDocumentId(documentId);
        request.setActionType("chuyenxuly");
        request.setFullname("Le Thi Xuan");
        request.setUnit("Van phong so");
        request.setIdentifier("H56.07.01");
        return request;
    }

    @Test
    void execute_shouldSaveEntryWithServerStampedUsername() {
        var document = existingVbden("vbden");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        var saved = new VbdenUserActionLog(document.getId(), "xuanlt.skhcn", "Le Thi Xuan", "Van phong so",
            "H56.07.01", "chuyenxuly");
        when(vbdenUserActionLogRepository.save(any())).thenReturn(saved);

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<VbdenUserActionLog> captor = ArgumentCaptor.forClass(VbdenUserActionLog.class);
        verify(vbdenUserActionLogRepository).save(captor.capture());
        var entity = captor.getValue();
        assertThat(entity.getParentId()).isEqualTo(document.getId());
        assertThat(entity.getActionType()).isEqualTo("chuyenxuly");
        assertThat(entity.getFullname()).isEqualTo("Le Thi Xuan");
        // Danh tinh thuc do server gan (SERVER-AUTHORITATIVE) - chi username la security-relevant.
        assertThat(entity.getUsername()).isEqualTo("xuanlt.skhcn");
        verify(documentLogService).logCreate(eq(saved.getId()), anyMap());
    }

    @Test
    void execute_whenDocumentMissing_shouldFail() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        var result = useCase.execute(request(documentId));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đến");
        verifyNoInteractions(vbdenUserActionLogRepository);
    }

    @Test
    void execute_whenDocumentWrongType_shouldFail() {
        var document = existingVbden("vbden_res");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(vbdenUserActionLogRepository);
    }

    @Test
    void execute_whenDocumentDeleted_shouldFail() {
        var document = existingVbden("vbden");
        document.markAsDeleted(null);
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(vbdenUserActionLogRepository);
    }

    @Test
    void execute_whenDocumentIdMissing_shouldFailWithoutQuerying() {
        var result = useCase.execute(new AddVbdenUserActionLogRequest());

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(documentRepository);
        verifyNoInteractions(vbdenUserActionLogRepository);
    }

    @Test
    void execute_whenActionTypeMissing_shouldFail() {
        var document = existingVbden("vbden");
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new AddVbdenUserActionLogRequest();
        request.setDocumentId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(vbdenUserActionLogRepository);
    }
}
