package com.td.application.documents;

import com.td.application.numbering.DocumentNumberingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-103 (Audit Log) — Definition of Done (2): tạo document → có log {@code create}.
 */
@ExtendWith(MockitoExtension.class)
class CreateDocumentUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentNumberingService documentNumberingService;

    @Test
    void execute_shouldLogCreateAfterSavingDocument() {
        var useCase = new CreateDocumentUseCase(
            documentRepository, documentCacheService, documentLogService, documentNumberingService);

        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateDocumentRequest();
        request.setTitle("Van ban test BL-103");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentLogService).logCreate(org.mockito.ArgumentMatchers.eq(result.getData()), any());
    }

    @Test
    void execute_whenSaveFails_shouldNotLogCreate() {
        var useCase = new CreateDocumentUseCase(
            documentRepository, documentCacheService, documentLogService, documentNumberingService);
        when(documentRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        var request = new CreateDocumentRequest();
        request.setTitle("Van ban loi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(documentLogService, org.mockito.Mockito.never()).logCreate(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_withConfiguredDocumentType_shouldAssignGeneratedDocumentNumber() throws Exception {
        var useCase = new CreateDocumentUseCase(
            documentRepository, documentCacheService, documentLogService, documentNumberingService);

        when(documentNumberingService.generateNumber("vbden")).thenReturn(java.util.Optional.of("5/2026"));
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(com.td.domain.documents.BusinessDocument.class);
        when(documentRepository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateDocumentRequest();
        request.setTitle("Van ban den");
        request.setDocumentType("vbden");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        var attributes = new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(savedCaptor.getValue().getAttributesJson(), java.util.Map.class);
        assertThat(attributes).containsEntry("document_number", "5/2026");
    }

    @Test
    void execute_whenDocumentNumberAlreadyProvided_shouldNotOverwrite() throws Exception {
        var useCase = new CreateDocumentUseCase(
            documentRepository, documentCacheService, documentLogService, documentNumberingService);

        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateDocumentRequest();
        request.setTitle("Van ban den");
        request.setDocumentType("vbden");
        request.setAttributes(java.util.Map.of("document_number", "SO-CU-01"));

        useCase.execute(request);

        verify(documentNumberingService, org.mockito.Mockito.never()).generateNumber(any());
    }
}
