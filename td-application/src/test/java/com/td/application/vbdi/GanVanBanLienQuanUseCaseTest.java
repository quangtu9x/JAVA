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
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService);
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
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        String vbdenId = UUID.randomUUID().toString();

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(vbdenId, "vbden")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> relatedList = (List<Object>) updated.get("vanban_lienquan_obj");
        assertThat(relatedList).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> saved = (Map<String, Object>) relatedList.get(0);
        assertThat(saved).containsEntry("id", vbdenId).containsEntry("database_type", "vbden");
        verify(documentLogService).logUpdate(any(), any(), any());
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

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> relatedList = (List<Object>) updated.get("vanban_lienquan_obj");
        assertThat(relatedList).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> saved = (Map<String, Object>) relatedList.get(0);
        assertThat(saved).containsEntry("database_type", "vbdi");
    }

    @Test
    void execute_withAlreadyLinkedDocument_shouldNotDuplicate() {
        String id = UUID.randomUUID().toString();
        VbdiDocument vbdi = newVbdiDocument(
            "{\"vanban_lienquan_obj\":[{\"id\":\"" + id + "\",\"code\":\"OLD\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(id, "vbden")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> relatedList = (List<Object>) updated.get("vanban_lienquan_obj");
        assertThat(relatedList).hasSize(1);
        // Toan bo la trung -> khong co gi de luu, khong goi save/log.
        verify(vbdiDocumentRepository, never()).save(any());
        verify(documentLogService, never()).logUpdate(any(), any(), any());
    }

    @Test
    void execute_withMixedNewAndExistingDocuments_shouldOnlyAppendNewOnes() {
        String existingId = UUID.randomUUID().toString();
        VbdiDocument vbdi = newVbdiDocument(
            "{\"vanban_lienquan_obj\":[{\"id\":\"" + existingId + "\",\"code\":\"OLD\"}]}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        String newId = UUID.randomUUID().toString();

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(existingId, "vbden"), relatedItem(newId, "vbden")));

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> relatedList = (List<Object>) updated.get("vanban_lienquan_obj");
        assertThat(relatedList).hasSize(2);
        verify(vbdiDocumentRepository, times(1)).save(vbdi);
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
        // id co the la UNID Domino (32 hex, khong dau gach) - server KHONG ep kieu UUID (xem javadoc).
        VbdiDocument vbdi = newVbdiDocument("{}");
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));
        String legacyUnid = "AB12CD34EF56AB12CD34EF56AB12CD34";

        var request = new GanVanBanLienQuanRequest();
        request.setVbdiId(vbdi.getId());
        request.setRelatedDocuments(List.of(relatedItem(legacyUnid, "vbden")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<Object> relatedList = (List<Object>) updated.get("vanban_lienquan_obj");
        assertThat(relatedList).hasSize(1);
    }
}
