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
 * 11/09/2026 — test {@link XoaVanBanLienQuanUseCase}: xem javadoc lớp đó (port
 * {@code VanBanLienQuanList.jsx#handleDelete}).
 */
@ExtendWith(MockitoExtension.class)
class XoaVanBanLienQuanUseCaseTest {

    private final com.td.application.documentlinks.InMemoryDocumentLinkRepository linkRepository = new com.td.application.documentlinks.InMemoryDocumentLinkRepository();

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private XoaVanBanLienQuanUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new XoaVanBanLienQuanUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService,
            com.td.application.documentlinks.InMemoryDocumentLinkRepository.newService(linkRepository));
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private VbdiDocument newVbdiDocument(String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("CV so 1", "vbdi", "2", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    @Test
    void execute_withoutVbdiId_shouldReturnFailure() {
        var request = new XoaVanBanLienQuanRequest();
        request.setDocumentId(UUID.randomUUID().toString());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutDocumentId_shouldReturnFailure() {
        var request = new XoaVanBanLienQuanRequest();
        request.setVbdiId(UUID.randomUUID());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownVbdi_shouldReturnFailure() {
        UUID vbdiId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.empty());

        var request = new XoaVanBanLienQuanRequest();
        request.setVbdiId(vbdiId);
        request.setDocumentId(UUID.randomUUID().toString());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedVbdi_shouldReturnFailure() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        vbdi.markAsDeleted(UUID.randomUUID());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new XoaVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(UUID.randomUUID().toString());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldRemoveMatchingItemById() {
        // 06/10/2026: go = xoa mem dong lien ket vbdi_kemtheo_vbden (bang document_links), khong ghi dynamic_fields.
        String targetId = UUID.randomUUID().toString();
        String keepId = UUID.randomUUID().toString();
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        linkRepository.seed(com.td.domain.documentlinks.DocumentLinkType.VBDI_KEMTHEO_VBDEN, vbdi.getId(), UUID.fromString(targetId))
            .seed(com.td.domain.documentlinks.DocumentLinkType.VBDI_KEMTHEO_VBDEN, vbdi.getId(), UUID.fromString(keepId));

        var request = new XoaVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(targetId);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(linkRepository.activeRows()).extracting(link -> link.getTargetId().toString())
            .containsExactly(keepId);
        verify(vbdiDocumentRepository, never()).save(any());
        verify(documentCacheService).evict(vbdi.getId());
        verify(documentCacheService).evictAllListCaches();
    }

    @Test
    void execute_withUnknownDocumentId_shouldNoOpAndStillSucceed() {
        String existingId = UUID.randomUUID().toString();
        VbdiDocument vbdi = newVbdiDocument(
            "{\"vanban_lienquan_obj\":[{\"id\":\"" + existingId + "\",\"code\":\"A\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new XoaVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(UUID.randomUUID().toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(vbdiDocumentRepository, never()).save(any());
        verify(documentLogService, never()).logUpdate(any(), any(), any());
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> relatedList = (List<Object>) updated.get("vanban_lienquan_obj");
        assertThat(relatedList).hasSize(1);
    }

    @Test
    void execute_withEmptyRelatedField_shouldNoOpAndStillSucceed() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new XoaVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(UUID.randomUUID().toString());

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        verify(vbdiDocumentRepository, never()).save(any());
    }

    @Test
    void execute_withDominoLegacyUnidId_shouldMatchByStringEquality() {
        // 06/10/2026: bang document_links chi co id UUID - UNID Domino khong co lien ket nao de go, tra success.
        String legacyUnid = "AB12CD34EF56AB12CD34EF56AB12CD34";
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new XoaVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setDocumentId(legacyUnid);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(vbdiDocumentRepository, never()).save(any());
    }
}
