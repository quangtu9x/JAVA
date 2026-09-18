package com.td.application.vbdi;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 29/08/2026 (port "Gắn VB đi" — {@code DocumentOutProcesses.java#add_Document_Revoke}, xem
 * javadoc {@link GanVbdiDocumentUseCase}): test liên kết B→A qua field động
 * {@value GanVbdiDocumentUseCase#REPLACES_FIELD}.
 */
@ExtendWith(MockitoExtension.class)
class GanVbdiDocumentUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GanVbdiDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GanVbdiDocumentUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService);
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private VbdiDocument vbdiDocument() {
        return new VbdiDocument("Van ban B", "vbdi", VbdiStatus.DRAFT, null, null, null, null);
    }

    @Test
    void execute_withValidLink_shouldWriteReplacesFieldOnDocumentB() {
        var documentB = vbdiDocument();
        var documentA = vbdiDocument();
        when(vbdiDocumentRepository.findById(documentB.getId())).thenReturn(Optional.of(documentB));
        when(vbdiDocumentRepository.findById(documentA.getId())).thenReturn(Optional.of(documentA));

        var request = new GanVbdiDocumentRequest();
        request.setDocumentId(documentB.getId());
        request.setReplacesDocumentId(documentA.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(documentB.getDynamicFieldsJson());
        assertThat(fields.get(GanVbdiDocumentUseCase.REPLACES_FIELD)).isEqualTo(documentA.getId().toString());
        verify(documentCacheService).evict(documentB.getId());
    }

    @Test
    void execute_withNullReplacesDocumentId_shouldRemoveExistingLink() {
        var documentB = vbdiDocument();
        documentB.replaceDynamicFields("{\"" + GanVbdiDocumentUseCase.REPLACES_FIELD + "\":\"" + UUID.randomUUID() + "\"}");
        when(vbdiDocumentRepository.findById(documentB.getId())).thenReturn(Optional.of(documentB));

        var request = new GanVbdiDocumentRequest();
        request.setDocumentId(documentB.getId());
        request.setReplacesDocumentId(null);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(documentB.getDynamicFieldsJson());
        assertThat(fields).doesNotContainKey(GanVbdiDocumentUseCase.REPLACES_FIELD);
    }

    @Test
    void execute_whenLinkingToSelf_shouldFail() {
        var documentB = vbdiDocument();
        when(vbdiDocumentRepository.findById(documentB.getId())).thenReturn(Optional.of(documentB));

        var request = new GanVbdiDocumentRequest();
        request.setDocumentId(documentB.getId());
        request.setReplacesDocumentId(documentB.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("chính nó");
    }

    @Test
    void execute_whenLinkedDocumentMissing_shouldFail() {
        var documentB = vbdiDocument();
        UUID missingId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(documentB.getId())).thenReturn(Optional.of(documentB));
        when(vbdiDocumentRepository.findById(missingId)).thenReturn(Optional.empty());

        var request = new GanVbdiDocumentRequest();
        request.setDocumentId(documentB.getId());
        request.setReplacesDocumentId(missingId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi cần gắn");
    }

    @Test
    void execute_whenDocumentBMissing_shouldFail() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new GanVbdiDocumentRequest();
        request.setDocumentId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đi");
    }

    @Test
    void execute_withoutDocumentId_shouldFail() {
        var result = useCase.execute(new GanVbdiDocumentRequest());

        assertThat(result.isSuccess()).isFalse();
    }
}
