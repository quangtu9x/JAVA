package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-220 (VBDi Core) — DoD (2): trình ký văn bản đi qua {@link TrinhKyVbdiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class TrinhKyVbdiUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private AddVbdiDynamicDocumentUseCase addVbdiDynamicDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TrinhKyVbdiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TrinhKyVbdiUseCase(
            vbdiDocumentRepository, addVbdiDynamicDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService);
    }

    @Test
    void execute_withUnknownDocument_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new TrinhKyVbdiRequest();
        request.setId(id);
        request.setNguoiKy("lanhdao01");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonVbdiDocument_shouldReturnFailure() {
        VbdiDocument document = newDocument("vbden", VbdiStatus.DRAFT);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new TrinhKyVbdiRequest();
        request.setId(document.getId());
        request.setNguoiKy("lanhdao01");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldCreateTrinhKyRecordAndTransitionToPendingSignature() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.DRAFT);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        UUID childId = UUID.randomUUID();
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(childId));

        var request = new TrinhKyVbdiRequest();
        request.setId(document.getId());
        request.setNguoiKy("lanhdao01");
        request.setOpinion("Trinh lanh dao ky duyet");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(childId);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdiDynamicDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getDocumentType()).isEqualTo("vbdi_res");
        assertThat(captor.getValue().getFields()).containsEntry("parentid", document.getId().toString());
        assertThat(captor.getValue().getFields()).containsEntry("nguoi_ky", "lanhdao01");
        assertThat(captor.getValue().getFields()).containsEntry("nguoi_trinh", "nguyenvana");

        assertThat(document.getStatus()).isEqualTo(VbdiStatus.PENDING_SIGNATURE);
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(updatedFields).containsEntry("nguoi_ky", "lanhdao01");

        verify(documentLogService).logUpdate(any(), any(), any());
        verify(documentCacheService).evict(document.getId());
    }

    @Test
    void execute_whenChildCreationFails_shouldNotUpdateDocument() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.DRAFT);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(addVbdiDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("loi"));

        var request = new TrinhKyVbdiRequest();
        request.setId(document.getId());
        request.setNguoiKy("lanhdao01");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(document.getStatus()).isEqualTo(VbdiStatus.DRAFT);
    }

    private VbdiDocument newDocument(String documentType, String status) {
        VbdiDocument document = new VbdiDocument("Van ban di", documentType, status, null, null, null, null);
        document.replaceDynamicFields("{}");
        return document;
    }
}
