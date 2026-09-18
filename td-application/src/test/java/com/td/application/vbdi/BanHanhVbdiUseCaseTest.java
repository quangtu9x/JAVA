package com.td.application.vbdi;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.numbering.DocumentNumberingService;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-220 (VBDi Core) — DoD (3): ban hành văn bản đi qua {@link BanHanhVbdiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class BanHanhVbdiUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentNumberingService documentNumberingService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private BanHanhVbdiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new BanHanhVbdiUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentNumberingService,
            documentLogService, documentCacheService);
    }

    @Test
    void execute_withUnknownDocument_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new BanHanhVbdiRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonVbdiDocument_shouldReturnFailure() {
        VbdiDocument document = newDocument("vbden", VbdiStatus.PENDING_SIGNATURE);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new BanHanhVbdiRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withoutPriorTrinhKy_shouldReturnFailure() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.DRAFT);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new BanHanhVbdiRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(documentNumberingService, never()).generateNumber(any());
    }

    @Test
    void execute_shouldGenerateDocumentNumberAndTransitionToIssued() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.PENDING_SIGNATURE);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentNumberingService.generateNumber(eq("vbdi"))).thenReturn(Optional.of("5/2026-VBD"));

        var request = new BanHanhVbdiRequest();
        request.setId(document.getId());
        request.setPromulgationDate("2026-08-05");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("5/2026-VBD");
        assertThat(document.getStatus()).isEqualTo(VbdiStatus.ISSUED);

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("document_number", "5/2026-VBD");
        assertThat(fields).containsEntry("promulgation_date", "2026-08-05");

        verify(documentLogService).logUpdate(any(), any(), any());
        verify(documentCacheService).evict(document.getId());
    }

    @Test
    void execute_shouldOverwriteExistingPlaceholderDocumentNumber() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.PENDING_SIGNATURE);
        document.replaceDynamicFields("{\"document_number\":\"1/2026-VBD\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentNumberingService.generateNumber(eq("vbdi"))).thenReturn(Optional.of("7/2026-VBD"));

        var request = new BanHanhVbdiRequest();
        request.setId(document.getId());

        useCase.execute(request);

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields).containsEntry("document_number", "7/2026-VBD");
    }

    @Test
    void execute_withoutExplicitPromulgationDate_shouldDefaultToToday() {
        VbdiDocument document = newDocument("vbdi", VbdiStatus.PENDING_SIGNATURE);
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentNumberingService.generateNumber(eq("vbdi"))).thenReturn(Optional.empty());

        var request = new BanHanhVbdiRequest();
        request.setId(document.getId());

        useCase.execute(request);

        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(fields.get("promulgation_date")).isEqualTo(java.time.LocalDate.now().toString());
    }

    private VbdiDocument newDocument(String documentType, String status) {
        VbdiDocument document = new VbdiDocument("Van ban di", documentType, status, null, null, null, null);
        document.replaceDynamicFields("{}");
        return document;
    }
}
