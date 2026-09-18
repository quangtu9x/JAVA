package com.td.application.documents;

import com.td.application.numbering.DocumentNumberingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-103 (Audit Log): tạo document qua endpoint legacy-compatible (BL-101 {@code /add}) cũng phải
 * ghi log {@code create}, bao gồm cả field động.
 */
@ExtendWith(MockitoExtension.class)
class AddDynamicDocumentUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentNumberingService documentNumberingService;
    @Mock
    private AuthorAclFieldsService authorAclFieldsService;

    @Test
    @SuppressWarnings("unchecked")
    void execute_shouldLogCreateWithDynamicFieldsSnapshot() {
        var useCase = new AddDynamicDocumentUseCase(
            documentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService,
            documentNumberingService, authorAclFieldsService);

        when(dynamicDocumentFieldsService.buildDynamicFieldsJson(any(), any(), any(), any()))
            .thenReturn("{\"received_date\":\"2026-08-04\"}");
        when(dynamicDocumentFieldsService.parseDynamicFields(any()))
            .thenReturn(Map.of("received_date", "2026-08-04"));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new AddDynamicDocumentRequest();
        request.setTitle("Van ban dynamic");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("received_date", "2026-08-04");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();

        ArgumentCaptor<Object> snapshotCaptor = ArgumentCaptor.forClass(Object.class);
        verify(documentLogService).logCreate(eq(result.getData()), snapshotCaptor.capture());

        Map<String, Object> snapshot = (Map<String, Object>) snapshotCaptor.getValue();
        assertThat(snapshot).containsEntry("title", "Van ban dynamic");
        assertThat(snapshot).containsEntry("received_date", "2026-08-04");
    }

    @Test
    void execute_withConfiguredDocumentType_shouldInjectGeneratedDocumentNumberIntoFields() {
        var useCase = new AddDynamicDocumentUseCase(
            documentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService, documentNumberingService, authorAclFieldsService);

        when(documentNumberingService.generateNumber("vbdi")).thenReturn(java.util.Optional.of("7/2026-VBD"));
        when(dynamicDocumentFieldsService.buildDynamicFieldsJson(any(), any(), any(), any())).thenReturn("{}");
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new AddDynamicDocumentRequest();
        request.setTitle("Van ban di");
        request.setDocumentType("vbdi");
        request.setFields(new LinkedHashMap<>());

        useCase.execute(request);

        assertThat(request.getFields()).containsEntry("document_number", "7/2026-VBD");
    }

    @Test
    void execute_whenDocumentNumberFieldAlreadyProvided_shouldNotOverwrite() {
        var useCase = new AddDynamicDocumentUseCase(
            documentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService, documentNumberingService, authorAclFieldsService);

        when(dynamicDocumentFieldsService.buildDynamicFieldsJson(any(), any(), any(), any())).thenReturn("{}");
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new AddDynamicDocumentRequest();
        request.setTitle("Van ban di");
        request.setDocumentType("vbdi");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("document_number", "SO-CU-01");
        request.setFields(fields);

        useCase.execute(request);

        verify(documentNumberingService, org.mockito.Mockito.never()).generateNumber(any());
        assertThat(request.getFields()).containsEntry("document_number", "SO-CU-01");
    }
}
