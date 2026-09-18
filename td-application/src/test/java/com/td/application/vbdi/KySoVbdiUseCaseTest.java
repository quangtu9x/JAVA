package com.td.application.vbdi;

import com.td.application.common.models.Result;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.UploadFileRequest;
import com.td.application.documents.UploadFileUseCase;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-221 (VBDi — Ký số & Gửi đi) — DoD (1): ký số văn bản đi qua {@link KySoVbdiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class KySoVbdiUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private UploadFileUseCase uploadFileUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private KySoVbdiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new KySoVbdiUseCase(
            vbdiDocumentRepository, uploadFileUseCase, dynamicDocumentFieldsService, documentLogService, documentCacheService);
    }

    @Test
    void execute_withUnknownDocument_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new KySoVbdiRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(uploadFileUseCase, never()).execute(any());
    }

    @Test
    void execute_withNonVbdiDocument_shouldReturnFailure() {
        VbdiDocument document = newDocument("vbden");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new KySoVbdiRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldUploadFileAsPrimaryAndMarkDaKySo() {
        VbdiDocument document = newDocument("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID fileId = UUID.randomUUID();
        when(uploadFileUseCase.execute(any())).thenReturn(Result.success(fileId));

        var request = new KySoVbdiRequest();
        request.setId(document.getId());
        request.setFileContent(new ByteArrayInputStream("pdf".getBytes()));
        request.setFileName("vbdi_signed.pdf");
        request.setMimeType("application/pdf");
        request.setFileSize(3L);
        request.setCoQuan(false);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(fileId);

        ArgumentCaptor<UploadFileRequest> captor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(uploadFileUseCase).execute(captor.capture());
        assertThat(captor.getValue().getDocumentId()).isEqualTo(document.getId());
        assertThat(captor.getValue().getIsPrimary()).isTrue();
        assertThat(captor.getValue().getFileName()).isEqualTo("vbdi_signed.pdf");

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("dakyso", "1");
        assertThat(fields).doesNotContainKey("dakycoquan");
        verify(documentLogService).logUpdate(any(), any(), any());
    }

    @Test
    void execute_withCoQuanTrue_shouldMarkDaKyCoQuan() {
        VbdiDocument document = newDocument("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(uploadFileUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new KySoVbdiRequest();
        request.setId(document.getId());
        request.setCoQuan(true);

        useCase.execute(request);

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("dakyso", "1");
        assertThat(fields).containsEntry("dakycoquan", "1");
    }

    @Test
    void execute_whenUploadFails_shouldNotMarkDaKySo() {
        VbdiDocument document = newDocument("vbdi");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(uploadFileUseCase.execute(any())).thenReturn(Result.failure("loi upload"));

        var request = new KySoVbdiRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(vbdiDocumentRepository, never()).save(any());
    }

    private VbdiDocument newDocument(String documentType) {
        VbdiDocument document = new VbdiDocument("Van ban di", documentType, "ISSUED", null, null, null, null);
        document.replaceDynamicFields("{}");
        return document;
    }
}
