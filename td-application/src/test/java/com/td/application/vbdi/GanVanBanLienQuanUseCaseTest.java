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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 11/09/2026 — test {@link GanVanBanLienQuanUseCase}: xem javadoc lớp đó (port hợp nhất
 * {@code ganvanbanden}/{@code ganvanbandi}, field {@code vanban_lienquan_obj}).
 */
@ExtendWith(MockitoExtension.class)
class GanVanBanLienQuanUseCaseTest {

    private final com.td.application.documentlinks.InMemoryDocumentLinkRepository linkRepository = new com.td.application.documentlinks.InMemoryDocumentLinkRepository();

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GanVanBanLienQuanUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GanVanBanLienQuanUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService,
            com.td.application.documentlinks.InMemoryDocumentLinkRepository.newSnapshots(linkRepository));
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private VbdiDocument newVbdiDocument(String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("CV so 1", "vbdi", "2", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    private Map<String, Object> relatedItem(String id, String databaseType) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("code", "10/CV-VBDEN");
        item.put("subject", "Trich yeu van ban lien quan");
        item.put("unit_publish", "So Noi vu");
        item.put("promulgation_date", "01/09/2026");
        item.put("database_type", databaseType);
        return item;
    }

    @Test
    void execute_withoutVbdiId_shouldReturnFailure() {
        var request = new GanVanBanLienQuanRequest();
        request.setRelatedDocuments(List.of(relatedItem(UUID.randomUUID().toString(), "vbden")));

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutRelatedDocuments_shouldReturnFailure() {
        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(UUID.randomUUID());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownVbdi_shouldReturnFailure() {
        UUID vbdiId = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.empty());

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdiId);
        request.setRelatedDocuments(List.of(relatedItem(UUID.randomUUID().toString(), "vbden")));

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedVbdi_shouldReturnFailure() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        vbdi.markAsDeleted(UUID.randomUUID());
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(UUID.randomUUID().toString(), "vbden")));

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    // ganvanbanden: chon van ban DEN lien quan.
    @Test
    void execute_ganVanBanDen_shouldAppendRelatedDocumentWithVbdenType() {
        // 06/10/2026: "Van ban kem theo" luu o bang document_links (vbdi_kemtheo_vbden), ban chup o link_data.
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        String vbdenId = UUID.randomUUID().toString();

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(vbdenId, "vbden")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(linkRepository.activeRows()).singleElement().satisfies(link -> {
            assertThat(link.getLinkType()).isEqualTo("vbdi_kemtheo_vbden");
            assertThat(link.getTargetId().toString()).isEqualTo(vbdenId);
            assertThat(link.getLinkDataJson()).contains("\"database_type\":\"vbden\"");
        });
        verify(vbdiDocumentRepository, never()).save(any());
        verify(documentCacheService).evict(vbdi.getId());
        verify(documentCacheService).evictAllListCaches();
    }

    // ganvanbandi: chon van ban DI lien quan - CUNG field, chi khac database_type FE tu gan.
    @Test
    void execute_ganVanBanDi_shouldAppendRelatedDocumentWithVbdiType() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        String otherVbdiId = UUID.randomUUID().toString();

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(otherVbdiId, "vbdi")));

        useCase.execute(request);

        assertThat(linkRepository.activeRows()).extracting(link -> link.getLinkType())
            .containsExactly("vbdi_kemtheo_vbdi");
    }

    @Test
    void execute_withAlreadyLinkedDocument_shouldNotDuplicate() {
        String id = UUID.randomUUID().toString();
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(id, "vbden")));
        useCase.execute(request);
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(linkRepository.activeRows()).hasSize(1);
        verify(vbdiDocumentRepository, never()).save(any());
    }

    @Test
    void execute_withMixedNewAndExistingDocuments_shouldOnlyAppendNewOnes() {
        String existingId = UUID.randomUUID().toString();
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        String newId = UUID.randomUUID().toString();

        var first = new GanVanBanLienQuanRequest();
        first.setVbdiId(vbdi.getId());
        first.setRelatedDocuments(List.of(relatedItem(existingId, "vbden")));
        useCase.execute(first);

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(existingId, "vbden"), relatedItem(newId, "vbden")));
        useCase.execute(request);

        assertThat(linkRepository.activeRows()).extracting(link -> link.getTargetId().toString())
            .containsExactly(existingId, newId);
    }

    @Test
    void execute_withBlankIdItem_shouldSkipItem() {
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        Map<String, Object> badItem = new LinkedHashMap<>();
        badItem.put("id", "");

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(badItem));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(vbdiDocumentRepository, never()).save(any());
    }

    @Test
    void execute_withDominoLegacyUnidId_shouldAcceptNonUuidId() {
        // 06/10/2026: bang document_links chi nhan id van ban Java (UUID) - phan tu UNID Domino bi bo qua
        // (khong loi), dung chu truong khong mang du lieu Domino cu.
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        String legacyUnid = "AB12CD34EF56AB12CD34EF56AB12CD34";

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(legacyUnid, "vbden")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(linkRepository.activeRows()).isEmpty();
    }
}
