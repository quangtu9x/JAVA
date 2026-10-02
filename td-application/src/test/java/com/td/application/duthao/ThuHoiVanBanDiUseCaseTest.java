package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.vbdi.DeleteVbdiDocumentUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.vbdi.VbdiStatus;
import com.td.domain.vbdi.VbdiDocument;
import com.td.domain.duthao.DuThaoDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * BL-230 (DuThao Core) — DoD (4): thu hồi văn bản đi chờ ban hành qua
 * {@link ThuHoiVanBanDiUseCase}.
 *
 * <p>Cập nhật 24/08/2026 (yêu cầu người dùng "tạo bảng riêng, để độc lập dữ liệu"): dự thảo đọc/lưu
 * qua {@link DuThaoDocumentRepository} (bảng {@code duthao_documents} độc lập) — {@code vbdi} (văn
 * bản đi liên kết) VẪN nằm ở {@link VbdiDocumentRepository} (bảng {@code documents} dùng chung, chưa
 * chuyển) — xem javadoc lớp {@link ThuHoiVanBanDiUseCase}.</p>
 */
@ExtendWith(MockitoExtension.class)
class ThuHoiVanBanDiUseCaseTest {

    @Mock
    private VbdiDocumentRepository documentRepository;
    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private DeleteVbdiDocumentUseCase deleteDocumentUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private ThuHoiVanBanDiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ThuHoiVanBanDiUseCase(
            documentRepository, duThaoDocumentRepository, dynamicDocumentFieldsService, deleteDocumentUseCase,
            documentLogService, documentCacheService, addDraftProcessDocumentUseCase, currentUserProvider,
            new DraftProcessActorFields(org.mockito.Mockito.mock(com.td.application.sharedcore.AppUserRepository.class)));
    }

    @Test
    void execute_withUnknownDraft_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new ThuHoiVanBanDiRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withoutDocumentOutId_shouldReturnFailure() {
        DuThaoDocument draft = newDraft("{}", DuThaoStatus.DRAFTING);
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var request = new ThuHoiVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(deleteDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_withUnknownLinkedVbdi_shouldReturnFailure() {
        UUID vbdiId = UUID.randomUUID();
        DuThaoDocument draft = newDraft("{\"document_out_id\":\"" + vbdiId + "\"}", DuThaoStatus.PUBLISHED);
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(documentRepository.findById(vbdiId)).thenReturn(Optional.empty());

        var request = new ThuHoiVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(deleteDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_whenVbdiAlreadyIssued_shouldReturnFailure() {
        UUID vbdiId = UUID.randomUUID();
        DuThaoDocument draft = newDraft("{\"document_out_id\":\"" + vbdiId + "\"}", DuThaoStatus.PUBLISHED);
        VbdiDocument vbdi = newVbdi(VbdiStatus.ISSUED);
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(documentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));

        var request = new ThuHoiVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(deleteDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_shouldDeleteVbdiAndReopenDraft() {
        UUID vbdiId = UUID.randomUUID();
        DuThaoDocument draft = newDraft("{\"document_out_id\":\"" + vbdiId + "\"}", DuThaoStatus.PUBLISHED);
        VbdiDocument vbdi = newVbdi(VbdiStatus.PENDING_SIGNATURE, "DocumentOut");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(documentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deleteDocumentUseCase.execute(eq(vbdiId))).thenReturn(Result.success(vbdiId));

        var request = new ThuHoiVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(draft.getId());
        verify(deleteDocumentUseCase).execute(vbdiId);

        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.DRAFTING);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(fields).doesNotContainKey("document_out_id");
        assertThat(fields.get("return_document_out_id")).isEqualTo(vbdiId.toString());
        verify(documentLogService).logUpdate(any(), any(), any());

        // Bugfix 27/08/2026 ("dang thieu thay doi form"): Domino dong 572 doi form thanh
        // "<form_cu>_Deleted" tren CHINH van ban di.
        ArgumentCaptor<VbdiDocument> vbdiSaveCaptor = ArgumentCaptor.forClass(VbdiDocument.class);
        verify(documentRepository).save(vbdiSaveCaptor.capture());
        Map<String, Object> vbdiFields = dynamicDocumentFieldsService.parseDynamicFields(vbdiSaveCaptor.getValue().getDynamicFieldsJson());
        assertThat(vbdiFields).containsEntry("form", "DocumentOut_Deleted");

        // Bo sung 27/08/2026: ghi vet draft_process "thuhoichuyenvanthu" (KHONG ep receiver, khac
        // ChuyenThanhVanBanDiUseCase).
        ArgumentCaptor<AddDynamicDocumentRequest> trailCaptor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(trailCaptor.capture());
        var trailFields = trailCaptor.getValue().getFields();
        assertThat(trailFields).containsEntry("parentid", draft.getId().toString());
        assertThat(trailFields).containsEntry("action_type", "thuhoichuyenvanthu");
        assertThat(trailFields).containsEntry("status", DuThaoStatus.DRAFTING);
        assertThat(trailFields).containsEntry("oldstatus", DuThaoStatus.PUBLISHED);
        assertThat(trailFields).doesNotContainKey("receiver");
    }

    // form rong/chua tung dat (vd du lieu cu chua co, hoac le khong qua ChuyenThanhVanBanDiUseCase
    // moi) -> Domino thuc van chay "" + "_Deleted" = "_Deleted", khong loi.
    @Test
    void execute_whenVbdiHasNoExistingForm_shouldSetFormToJustDeletedSuffix() {
        UUID vbdiId = UUID.randomUUID();
        DuThaoDocument draft = newDraft("{\"document_out_id\":\"" + vbdiId + "\"}", DuThaoStatus.PUBLISHED);
        VbdiDocument vbdi = newVbdi(VbdiStatus.PENDING_SIGNATURE);
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(documentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deleteDocumentUseCase.execute(eq(vbdiId))).thenReturn(Result.success(vbdiId));

        var request = new ThuHoiVanBanDiRequest();
        request.setId(draft.getId());

        useCase.execute(request);

        ArgumentCaptor<VbdiDocument> vbdiSaveCaptor = ArgumentCaptor.forClass(VbdiDocument.class);
        verify(documentRepository).save(vbdiSaveCaptor.capture());
        Map<String, Object> vbdiFields = dynamicDocumentFieldsService.parseDynamicFields(vbdiSaveCaptor.getValue().getDynamicFieldsJson());
        assertThat(vbdiFields).containsEntry("form", "_Deleted");
    }

    // Loi doi form KHONG chan nghiep vu chinh (deleted_on/deleted_by cua DeleteVbdiDocumentUseCase
    // da la nguon su that chinh cho isDeleted()) - dung nguyen tac field dong phu khong chan.
    @Test
    void execute_whenMarkFormAsDeletedThrows_shouldStillSucceed() {
        UUID vbdiId = UUID.randomUUID();
        DuThaoDocument draft = newDraft("{\"document_out_id\":\"" + vbdiId + "\"}", DuThaoStatus.PUBLISHED);
        VbdiDocument vbdi = newVbdi(VbdiStatus.PENDING_SIGNATURE);
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(documentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deleteDocumentUseCase.execute(eq(vbdiId))).thenReturn(Result.success(vbdiId));
        when(documentRepository.save(any())).thenThrow(new RuntimeException("db down"));

        var request = new ThuHoiVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.DRAFTING);
        verify(addDraftProcessDocumentUseCase).execute(any());
    }

    @Test
    void execute_withOpinion_shouldIncludeOpinionInProcessTrail() {
        UUID vbdiId = UUID.randomUUID();
        DuThaoDocument draft = newDraft("{\"document_out_id\":\"" + vbdiId + "\"}", DuThaoStatus.PUBLISHED);
        VbdiDocument vbdi = newVbdi(VbdiStatus.PENDING_SIGNATURE);
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(documentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deleteDocumentUseCase.execute(eq(vbdiId))).thenReturn(Result.success(vbdiId));

        var request = new ThuHoiVanBanDiRequest();
        request.setId(draft.getId());
        request.setOpinion("Sai thong tin, thu hoi lai");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> trailCaptor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(trailCaptor.capture());
        assertThat(trailCaptor.getValue().getFields()).containsEntry("opinion", "Sai thong tin, thu hoi lai");
    }

    @Test
    void execute_whenDeleteFails_shouldNotReopenDraft() {
        UUID vbdiId = UUID.randomUUID();
        DuThaoDocument draft = newDraft("{\"document_out_id\":\"" + vbdiId + "\"}", DuThaoStatus.PUBLISHED);
        VbdiDocument vbdi = newVbdi(VbdiStatus.DRAFT);
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(documentRepository.findById(vbdiId)).thenReturn(Optional.of(vbdi));
        when(deleteDocumentUseCase.execute(eq(vbdiId))).thenReturn(Result.failure("loi xoa"));

        var request = new ThuHoiVanBanDiRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(draft.getStatus()).isEqualTo(DuThaoStatus.PUBLISHED);
        verify(duThaoDocumentRepository, never()).save(any());
        verify(addDraftProcessDocumentUseCase, never()).execute(any());
    }

    private DuThaoDocument newDraft(String dynamicFieldsJson, String status) {
        DuThaoDocument document = new DuThaoDocument("Van ban", "duthao", status, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    private VbdiDocument newVbdi(String status) {
        return newVbdi(status, null);
    }

    private VbdiDocument newVbdi(String status, String form) {
        VbdiDocument document = new VbdiDocument("Van ban di", "vbdi", status, null, null, null, null);
        document.replaceDynamicFields(form == null ? "{}" : "{\"form\":\"" + form + "\"}");
        return document;
    }
}
