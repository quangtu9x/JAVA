package com.td.application.hosocv;

import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.duthao.DuThaoDocumentRepository;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.hosocv.HoSoCVDocument;
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
 * BL-240 (HoSoCV Core) — DoD (2): gắn văn bản đến/đi/dự thảo vào hồ sơ qua
 * {@link GanKemVanBanUseCase}.
 *
 * <p><b>Mở rộng — sửa bug tra sai bảng (xem javadoc {@link GanKemVanBanUseCase})</b>: bản test
 * trước đây mock DUY NHẤT {@code DocumentRepository} và trả thẳng {@code BusinessDocument} bất kể
 * {@code documentType} (kể cả cho {@code duthao}/{@code vbdi}) — che giấu hoàn toàn bug tra sai
 * bảng vì Mockito không biết/không quan tâm bảng vật lý thật. Bản này mock ĐÚNG 4 repository theo
 * đúng bảng vật lý thật ({@link HoSoCVDocumentRepository} cho hồ sơ, {@link DuThaoDocumentRepository}
 * cho dự thảo, {@link VbdiDocumentRepository} cho văn bản đi, {@link DocumentRepository} chỉ còn cho
 * văn bản đến/phiếu trình — 2 loại CHƯA tách bảng riêng) — nếu bug tra sai bảng tái diễn (vd. tra
 * dự thảo qua {@code documentRepository}), các test {@code ganKemDuThao}/{@code ganvanbandi} sẽ ĐỎ
 * ngay vì mock tương ứng không được stub.
 */
@ExtendWith(MockitoExtension.class)
class GanKemVanBanUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GanKemVanBanUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GanKemVanBanUseCase(
            hoSoCVDocumentRepository, documentRepository, duThaoDocumentRepository, vbdiDocumentRepository,
            dynamicDocumentFieldsService, documentLogService, documentCacheService);
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void execute_withUnknownActionName_shouldReturnFailure() {
        var request = new GanKemVanBanRequest();
        request.setHosocvId(UUID.randomUUID());
        request.setActionName("khongtontai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownHoSoCV_shouldReturnFailure() {
        UUID hosocvId = UUID.randomUUID();
        when(hoSoCVDocumentRepository.findById(hosocvId)).thenReturn(Optional.empty());

        var request = new GanKemVanBanRequest();
        request.setHosocvId(hosocvId);
        request.setActionName("ganvanbandi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonHoSoCVDocument_shouldReturnFailure() {
        HoSoCVDocument notHoSo = newHoSoCVDocument(UUID.randomUUID(), "vbdi_typo", "{}");
        when(hoSoCVDocumentRepository.findById(notHoSo.getId())).thenReturn(Optional.of(notHoSo));

        var request = new GanKemVanBanRequest();
        request.setHosocvId(notHoSo.getId());
        request.setActionName("ganvanbandi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    // Bugfix xac nhan: "ganvanbandi" phai tra VbdiDocumentRepository (bang vbdi_documents rieng),
    // KHONG con dung documentRepository (bang documents chung) nhu ban truoc day.
    @Test
    void execute_addAction_ganVanBanDi_shouldResolveThroughVbdiDocumentRepository() {
        HoSoCVDocument hosocv = newHoSoCVDocument(UUID.randomUUID(), "hosocv", "{}");
        VbdiDocument vbdi = newVbdiDocument(UUID.randomUUID(), "vbdi", "{}");
        when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));
        when(vbdiDocumentRepository.findById(vbdi.getId())).thenReturn(Optional.of(vbdi));

        var request = new GanKemVanBanRequest();
        request.setHosocvId(hosocv.getId());
        request.setVanBanIds(List.of(vbdi.getId()));
        request.setActionName("ganvanbandi");
        request.setAction("add");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(hosocv.getId());

        Map<String, Object> vbdiFields = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        assertThat(vbdiFields.get("document_profiles_id")).isEqualTo(hosocv.getId().toString());
        assertThat(vbdiFields.get("hoso_attached_id")).isEqualTo(hosocv.getId().toString());

        Map<String, Object> hosocvFields = dynamicDocumentFieldsService.parseDynamicFields(hosocv.getDynamicFieldsJson());
        assertThat(hosocvFields.get("document_out_attached_id")).isEqualTo(vbdi.getId().toString());
        verify(hoSoCVDocumentRepository).save(hosocv);
        verify(documentRepository, never()).findById(any());
    }

    // Bugfix xac nhan: "gankemduthao" phai tra DuThaoDocumentRepository (bang duthao_documents rieng).
    @Test
    void execute_addAction_ganKemDuThao_shouldResolveThroughDuThaoDocumentRepository() {
        HoSoCVDocument hosocv = newHoSoCVDocument(UUID.randomUUID(), "hosocv", "{}");
        DuThaoDocument duThao = newDuThaoDocument(UUID.randomUUID(), "duthao", "{}");
        when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));
        when(duThaoDocumentRepository.findById(duThao.getId())).thenReturn(Optional.of(duThao));

        var request = new GanKemVanBanRequest();
        request.setHosocvId(hosocv.getId());
        request.setVanBanIds(List.of(duThao.getId()));
        request.setActionName("gankemduthao");
        request.setAction("add");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> duThaoFields = dynamicDocumentFieldsService.parseDynamicFields(duThao.getDynamicFieldsJson());
        assertThat(duThaoFields.get("document_profiles_id")).isEqualTo(hosocv.getId().toString());
        verify(documentRepository, never()).findById(any());
    }

    @Test
    void execute_addAction_whenAlreadyLinked_shouldNotDuplicateOrSaveAgain() {
        UUID hosocvId = UUID.randomUUID();
        UUID vbdiId = UUID.randomUUID();
        HoSoCVDocument hosocv = newHoSoCVDocument(hosocvId, "hosocv",
            "{\"document_out_attached_id\":\"" + vbdiId + "\"}");
        VbdiDocument vbdi = newVbdiDocument(vbdiId, "vbdi",
            "{\"document_profiles_id\":\"" + hosocvId + "\",\"hoso_attached_id\":\"" + hosocvId + "\"}");
        when(hoSoCVDocumentRepository.findById(hosocvId)).thenReturn(Optional.of(hosocv));
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));

        var request = new GanKemVanBanRequest();
        request.setHosocvId(hosocvId);
        request.setVanBanIds(List.of(vbdiId));
        request.setActionName("ganvanbandi");

        useCase.execute(request);

        verify(vbdiDocumentRepository, never()).save(vbdi);
        verify(hoSoCVDocumentRepository, never()).save(hosocv);
    }

    // "ganvanbanden"/"ganvanbandenxuly" van chua tach bang rieng (vbden con o bang documents chung).
    @Test
    void execute_removeAction_ganVanBanDen_shouldUpdateBothSidesAndClearHosoAttachedId() {
        UUID hosocvId = UUID.randomUUID();
        UUID vbdenId = UUID.randomUUID();
        HoSoCVDocument hosocv = newHoSoCVDocument(hosocvId, "hosocv",
            "{\"document_in_attached_id\":\"" + vbdenId + "\"}");
        BusinessDocument vbden = newBusinessDocument(vbdenId, "vbden",
            "{\"document_profiles_id\":\"" + hosocvId + "\",\"hoso_attached_id\":\"" + hosocvId + "\"}");
        when(hoSoCVDocumentRepository.findById(hosocvId)).thenReturn(Optional.of(hosocv));
        when(documentRepository.findById(vbdenId)).thenReturn(Optional.of(vbden));

        var request = new GanKemVanBanRequest();
        request.setHosocvId(hosocvId);
        request.setVanBanIds(List.of(vbdenId));
        request.setActionName("ganvanbanden");
        request.setAction("remove");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();

        Map<String, Object> vbdenFields = dynamicDocumentFieldsService.parseDynamicFields(vbden.getDynamicFieldsJson());
        assertThat(vbdenFields.get("document_profiles_id")).isEqualTo("");
        assertThat(vbdenFields.get("hoso_attached_id")).isEqualTo("");

        Map<String, Object> hosocvFields = dynamicDocumentFieldsService.parseDynamicFields(hosocv.getDynamicFieldsJson());
        assertThat(hosocvFields.get("document_in_attached_id")).isEqualTo("");
        verify(documentLogService, org.mockito.Mockito.atLeastOnce()).logUpdate(any(), any(), any());
    }

    @Test
    void execute_ganKemDuThao_add_shouldSyncLinkedVbdiDocumentProfilesFromDraftId() {
        UUID hosocvId = UUID.randomUUID();
        UUID vbdiId = UUID.randomUUID();
        HoSoCVDocument hosocv = newHoSoCVDocument(hosocvId, "hosocv", "{}");
        DuThaoDocument duThao = newDuThaoDocument(UUID.randomUUID(), "duthao",
            "{\"document_out_id\":\"" + vbdiId + "\"}");
        VbdiDocument vbdi = newVbdiDocument(vbdiId, "vbdi", "{}");
        when(hoSoCVDocumentRepository.findById(hosocvId)).thenReturn(Optional.of(hosocv));
        when(duThaoDocumentRepository.findById(duThao.getId())).thenReturn(Optional.of(duThao));
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));

        var request = new GanKemVanBanRequest();
        request.setHosocvId(hosocvId);
        request.setVanBanIds(List.of(duThao.getId()));
        request.setActionName("gankemduthao");
        request.setAction("add");

        useCase.execute(request);

        Map<String, Object> vbdiFields = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        assertThat(vbdiFields.get("document_profiles_from_draft_id")).isEqualTo(hosocvId.toString());
    }

    @Test
    void execute_ganKemDuThao_remove_shouldRemoveFromLinkedVbdiDocumentProfilesFromDraftId() {
        UUID hosocvId = UUID.randomUUID();
        UUID vbdiId = UUID.randomUUID();
        HoSoCVDocument hosocv = newHoSoCVDocument(hosocvId, "hosocv",
            "{\"duthao_attached_id\":\"" + UUID.randomUUID() + "\"}");
        DuThaoDocument duThao = newDuThaoDocument(UUID.randomUUID(), "duthao",
            "{\"document_out_id\":\"" + vbdiId + "\",\"document_profiles_id\":\"" + hosocvId + "\"}");
        VbdiDocument vbdi = newVbdiDocument(vbdiId, "vbdi",
            "{\"document_profiles_from_draft_id\":\"" + hosocvId + "\"}");
        when(hoSoCVDocumentRepository.findById(hosocvId)).thenReturn(Optional.of(hosocv));
        when(duThaoDocumentRepository.findById(duThao.getId())).thenReturn(Optional.of(duThao));
        when(vbdiDocumentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));

        var request = new GanKemVanBanRequest();
        request.setHosocvId(hosocvId);
        request.setVanBanIds(List.of(duThao.getId()));
        request.setActionName("gankemduthao");
        request.setAction("remove");

        useCase.execute(request);

        Map<String, Object> vbdiFields = dynamicDocumentFieldsService.parseDynamicFields(vbdi.getDynamicFieldsJson());
        assertThat(vbdiFields.get("document_profiles_from_draft_id")).isEqualTo("");
    }

    @Test
    void execute_withUnknownVanBanId_shouldSkipGracefully() {
        HoSoCVDocument hosocv = newHoSoCVDocument(UUID.randomUUID(), "hosocv", "{}");
        UUID unknownId = UUID.randomUUID();
        when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));
        when(vbdiDocumentRepository.findById(unknownId)).thenReturn(Optional.empty());

        var request = new GanKemVanBanRequest();
        request.setHosocvId(hosocv.getId());
        request.setVanBanIds(List.of(unknownId));
        request.setActionName("ganvanbandi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
    }

    private HoSoCVDocument newHoSoCVDocument(UUID id, String documentType, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Test", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        org.springframework.test.util.ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private DuThaoDocument newDuThaoDocument(UUID id, String documentType, String dynamicFieldsJson) {
        DuThaoDocument document = new DuThaoDocument("Test", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        org.springframework.test.util.ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private VbdiDocument newVbdiDocument(UUID id, String documentType, String dynamicFieldsJson) {
        VbdiDocument document = new VbdiDocument("Test", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        org.springframework.test.util.ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private BusinessDocument newBusinessDocument(UUID id, String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Test", documentType, "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        org.springframework.test.util.ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
