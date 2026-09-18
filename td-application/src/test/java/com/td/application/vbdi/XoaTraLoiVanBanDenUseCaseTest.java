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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 11/09/2026 — test {@link XoaTraLoiVanBanDenUseCase}: xem javadoc lớp đó (port
 * {@code TraLoiChoVanBanDen.jsx#handleDelete}).
 */
@ExtendWith(MockitoExtension.class)
class XoaTraLoiVanBanDenUseCaseTest {

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private XoaTraLoiVanBanDenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new XoaTraLoiVanBanDenUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService);
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private VbdiDocument newVbdiDocument(String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("CV so 1", "vbdi", "2", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    @Test
    void execute_withoutVbdiId_shouldReturnFailure() {
        var request = new XoaTraLoiVanBanDenRequest();
        request.setDocumentId(UUID.randomUUID().toString());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutDocumentId_shouldReturnFailure() {
        var request = new XoaTraLoiVanBanDenRequest();
        request.setVbdiId(UUID.randomUUID());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownVbdi_shouldReturnFailure() {
        UUID vbdiId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.empty());

        var request = new XoaTraLoiVanBanDenRequest();
        request.setVbdiId(vbdiId);
        request.setDocumentId(UUID.randomUUID().toString());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedVbdi_shouldReturnFailure() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        vbdi.markAsDeleted(UUID.randomUUID());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new XoaTraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(UUID.randomUUID().toString());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldRemoveMatchingItemFromDocumentInObj() {
        String targetId = UUID.randomUUID().toString();
        String keepId = UUID.randomUUID().toString();
        VbdiDocument vbdi = newVbdiDocument("{\"documentin_obj\":["
            + "{\"id\":\"" + targetId + "\",\"code\":\"A\"},"
            + "{\"id\":\"" + keepId + "\",\"code\":\"B\"}"
            + "]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new XoaTraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(targetId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> documentIn = (List<Object>) updated.get("documentin_obj");
        assertThat(documentIn).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> remaining = (Map<String, Object>) documentIn.get(0);
        assertThat(remaining).containsEntry("id", keepId);
        verify(documentLogService).logUpdate(any(), any(), any());
        verify(documentCacheService).evict(vbdi.getId());
        verify(documentCacheService).evictAllListCaches();
    }

    @Test
    void execute_withUnknownDocumentId_shouldNoOpAndStillSucceed() {
        String existingId = UUID.randomUUID().toString();
        VbdiDocument vbdi = newVbdiDocument(
            "{\"documentin_obj\":[{\"id\":\"" + existingId + "\",\"code\":\"A\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new XoaTraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(UUID.randomUUID().toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(vbdiDocumentRepository, never()).save(any());
        verify(documentLogService, never()).logUpdate(any(), any(), any());
    }

    @Test
    void execute_withEmptyDocumentInObj_shouldNoOpAndStillSucceed() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new XoaTraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(UUID.randomUUID().toString());

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        verify(vbdiDocumentRepository, never()).save(any());
    }

    @Test
    void execute_withDominoLegacyUnidId_shouldMatchByStringEquality() {
        String legacyUnid = "AB12CD34EF56AB12CD34EF56AB12CD34";
        VbdiDocument vbdi = newVbdiDocument(
            "{\"documentin_obj\":[{\"id\":\"" + legacyUnid + "\",\"code\":\"A\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new XoaTraLoiVanBanDenRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(legacyUnid);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> documentIn = (List<Object>) updated.get("documentin_obj");
        assertThat(documentIn).isEmpty();
    }
}
