package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DuThaoDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-230 (DuThao Core) — DT-001 (tài liệu phân tích 04 mục 12): {@link XuLyDuThaoUseCase} — đối
 * chiếu trực tiếp {@code DraftProcesses.DraftProcessing()} (xem javadoc lớp về phạm vi đã port).
 */
@ExtendWith(MockitoExtension.class)
class XuLyDuThaoUseCaseTest {

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    // DT-007 tach bang (25/08/2026): vet xu ly ghi qua AddDraftProcessDocumentUseCase (bang vat ly
    // rieng draft_process_documents), khong con qua AddDuThaoDynamicDocumentUseCase.
    @Mock
    private AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private XuLyDuThaoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new XuLyDuThaoUseCase(
            duThaoDocumentRepository, dynamicDocumentFieldsService, currentUserProvider,
            documentLogService, documentCacheService, addDraftProcessDocumentUseCase, authorAclFieldsService,
            new DraftProcessActorFields(org.mockito.Mockito.mock(com.td.application.sharedcore.AppUserRepository.class)));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        lenient().when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // DT-007: mac dinh tao draft_process thanh cong - cac test khong quan tam ket qua nay chi
        // can khong NPE; xem XuLyDuThaoUseCaseTest#execute_*_shouldCreateDraftProcessRecord rieng.
        lenient().when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownDocument_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new XuLyDuThaoRequest();
        request.setId(id);
        request.setAction("chuyenduyet");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonDuThaoDocument_shouldReturnFailure() {
        DuThaoDocument document = newDocument("vbdi", DuThaoStatus.DRAFTING, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenduyet");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withUnsupportedAction_shouldReturnFailure() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("hanhdongkhonghople");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withChuyenVanThuAction_shouldReturnFailure() {
        // chuyenvanthu di qua ChuyenThanhVanBanDiUseCase rieng - KHONG duoc ho tro o day.
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenvanthu");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_chuyenduyet_shouldSetProcessHandlerFieldsAndTransitionStatus() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenduyet");
        request.setReceiver("Nguyen Van B");
        request.setReceiverUsername("nguyenvanb");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.FORWARDED_FOR_APPROVAL);
        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).containsEntry("process_handler", "Nguyen Van B");
        assertThat(fields).containsEntry("user_process_handler", "nguyenvanb");
        assertThat(fields).containsEntry("user_processed_handler", "nguyenvana");
        assertThat(fields).containsEntry("status_pre", DuThaoStatus.DRAFTING);

        verify(documentLogService).logUpdate(any(), any(), any());
        verify(documentCacheService).evict(document.getId());
        verify(documentCacheService).evictAllListCaches();
    }

    // DT-007 (24/08/2026, cap nhat 25/08 tach bang): tao document con draft_process qua
    // AddDraftProcessDocumentUseCase (bang vat ly rieng draft_process_documents - V1.0.30), ghi id
    // cua no vao last_process_id tren document cha - dxl DraftProcesses.java diem 1-3 muc 4.2 tai
    // lieu phan tich 04. documentType khong con do caller dat - use case moi tu ep 'draft_process'
    // (assert o AddDraftProcessDocumentUseCaseTest).
    @Test
    void execute_chuyenduyet_shouldCreateDraftProcessRecordAndSetLastProcessId() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        UUID processId = UUID.randomUUID();
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(processId));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenduyet");
        request.setReceiver("Nguyen Van B");
        request.setReceiverUsername("nguyenvanb");
        request.setOpinion("Trinh lanh dao duyet");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(com.td.application.documents.AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(requestCaptor.capture());
        var processRequest = requestCaptor.getValue();
        assertThat(processRequest.getStatus()).isEqualTo(DuThaoStatus.FORWARDED_FOR_APPROVAL);
        assertThat(processRequest.getFields()).containsEntry("parentid", document.getId().toString());
        // Bugfix 26/08/2026 (port vwGroup_process_by_draft_id): khoa THAT SU Domino la "action_type",
        // khong phai "action" - xem javadoc PROCESS_ACTION_FIELD.
        assertThat(processRequest.getFields()).containsEntry("action_type", "chuyenduyet");
        assertThat(processRequest.getFields()).containsEntry("status", DuThaoStatus.FORWARDED_FOR_APPROVAL);
        assertThat(processRequest.getFields()).containsEntry("oldstatus", DuThaoStatus.DRAFTING);
        assertThat(processRequest.getFields()).containsEntry("receiver", "Nguyen Van B");
        assertThat(processRequest.getFields()).containsEntry("receiver_username", "nguyenvanb");
        assertThat(processRequest.getFields()).containsEntry("opinion", "Trinh lanh dao duyet");
        assertThat(processRequest.getFields()).containsEntry("creator", "nguyenvana");

        assertThat(fieldsOf(document)).containsEntry("last_process_id", processId.toString());
    }

    @Test
    void execute_whenDraftProcessCreationFails_shouldStillTransitionStatus() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.failure("loi tao process"));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenduyet");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.FORWARDED_FOR_APPROVAL);
        assertThat(fieldsOf(document)).doesNotContainKey("last_process_id");
    }

    @Test
    void execute_danhgiaduthao_shouldNotCreateDraftProcessRecord() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("danhgiaduthao");
        request.setRating("5");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(addDraftProcessDocumentUseCase, org.mockito.Mockito.never()).execute(any());
        assertThat(fieldsOf(document)).doesNotContainKey("last_process_id");
    }

    @Test
    void execute_chuyennguoikhac_shouldAppendToUserOtherProcessedHandlerWithoutDedup() {
        DuThaoDocument document = newDocument(
            "duthao", DuThaoStatus.DRAFTING, "{\"user_other_processed_handler\":\"nguyenvanc\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyennguoikhac");
        request.setReceiver("Nguyen Van B");
        request.setReceiverUsername("nguyenvanb");

        useCase.execute(request);

        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.OTHER_HANDLER_SUBMIT);
        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).containsEntry("other_process_handler", "Nguyen Van B");
        assertThat(fields).containsEntry("user_other_process_handler", "nguyenvanb");
        assertThat(fields).containsEntry("user_other_processed_handler", "nguyenvanc;nguyenvana");
    }

    @Test
    void execute_chuyenphoihop_shouldBehaveExactlyLikeChuyenNguoiKhac() {
        // Alias - dxl DraftProcesses.java: ca 2 action cung khop dieu kien "status=9", chi khac
        // notification (khong port o Java - xem javadoc lop).
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenphoihop");
        request.setReceiver("Nguyen Van B");
        request.setReceiverUsername("nguyenvanb");

        useCase.execute(request);

        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.OTHER_HANDLER_SUBMIT);
    }

    @Test
    void execute_chuyenkyduyet_withSignerObjAndBlankUserSigner_shouldFillUserSignerFromSignerObj() {
        DuThaoDocument document = newDocument(
            "duthao", DuThaoStatus.DRAFTING,
            "{\"signer_obj\":\"{\\\"user\\\":\\\"bacpv.stc\\\",\\\"name\\\":\\\"Pham Viet Bac\\\"}\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenkyduyet");

        useCase.execute(request);

        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.PENDING_SIGNATURE);
        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).containsEntry("user_signer", "bacpv.stc");
    }

    @Test
    void execute_chuyenkyduyet_withUserSignerAlreadySet_shouldNotOverrideIt() {
        DuThaoDocument document = newDocument(
            "duthao", DuThaoStatus.DRAFTING,
            "{\"user_signer\":\"nguoikyhientai\",\"signer_obj\":\"{\\\"user\\\":\\\"bacpv.stc\\\"}\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenkyduyet");

        useCase.execute(request);

        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).containsEntry("user_signer", "nguoikyhientai");
    }

    @Test
    void execute_chuyenkyduyet_shouldClearStaleReceiverUsername() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING, "{\"receiver_username\":\"conson\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenkyduyet");

        useCase.execute(request);

        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).doesNotContainKey("receiver_username");
    }

    // Bugfix 01/10/2026: nguoi duoc "Chuyen xu ly" bam "Chuyen ky duyet" - FE gui receiver = nguoi da
    // chuyen xu ly (tu nguoichuyen_obj). Vet phai ghi Nguoi nhan = NGUOI KY cua du thao.
    @Test
    void execute_chuyenkyduyet_shouldRecordSignerAsReceiverIgnoringClientReceiver() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.OTHER_HANDLER_SUBMIT,
            "{\"user_creator\":\"demo3\",\"signer\":\"Le Phu Ha\",\"user_signer\":\"lpha\",\"user_other_process_handler\":\"demo2\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenkyduyet");
        request.setReceiver("Chuyen vien 1");
        request.setReceiverUsername("demo3");

        assertThat(useCase.execute(request).isSuccess()).isTrue();

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(com.td.application.documents.AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFields())
            .containsEntry("receiver", "Le Phu Ha")
            .containsEntry("receiver_username", "lpha");
    }

    // signer rong -> lay ten tu signer_obj; user_signer rong -> lay tu signer_obj.user.
    @Test
    void execute_chuyenkyduyet_withOnlySignerObj_shouldUseSignerObjNameAndUser() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING,
            "{\"signer_obj\":\"{\\\"name\\\":\\\"Le Phu Ha\\\",\\\"user\\\":\\\"lpha\\\"}\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenkyduyet");
        request.setReceiverUsername("demo3");

        useCase.execute(request);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(com.td.application.documents.AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFields())
            .containsEntry("receiver", "Le Phu Ha")
            .containsEntry("receiver_username", "lpha");
    }

    // signer_obj luu dang OBJECT long (du lieu that) - truoc day String.valueOf(map) khong parse duoc.
    @Test
    void execute_chuyenkyduyet_withNestedSignerObject_shouldFillUserSignerAndReceiver() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING,
            "{\"signer_obj\":{\"name\":\"Le Phu Ha\",\"user\":\"lpha\"}}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenkyduyet");

        useCase.execute(request);

        assertThat(fieldsOf(document)).containsEntry("user_signer", "lpha");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(com.td.application.documents.AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getFields())
            .containsEntry("receiver", "Le Phu Ha")
            .containsEntry("receiver_username", "lpha");
    }

    @Test
    void execute_ketthuc_shouldSetDraftEndFields() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.PENDING_SIGNATURE, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("ketthuc");

        useCase.execute(request);

        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.ENDED);
        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).containsEntry("draft_end", "1");
        assertThat(fields).containsEntry("draft_end_date", DateTimeFormatter.ofPattern("dd/MM/yyyy").format(LocalDate.now()));
    }

    @Test
    void execute_tralai_fromForwardedForApproval_shouldCleanUpProcessHandlerFields() {
        DuThaoDocument document = newDocument(
            "duthao", DuThaoStatus.FORWARDED_FOR_APPROVAL,
            "{\"process_handler\":\"Nguyen Van B\",\"user_process_handler\":\"nguyenvanb\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("tralai");
        request.setReceiver("Nguoi Tao");
        request.setReceiverUsername("nguoitao");

        useCase.execute(request);

        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.RETURNED);
        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).doesNotContainKey("process_handler");
        assertThat(fields).doesNotContainKey("user_process_handler");
        assertThat(fields).doesNotContainKey("status_pre");
        assertThat(fields).containsEntry("receiver_username", "nguoitao");
    }

    @Test
    void execute_tralai_fromOtherHandlerSubmit_shouldCleanUpOtherHandlerFields() {
        DuThaoDocument document = newDocument(
            "duthao", DuThaoStatus.OTHER_HANDLER_SUBMIT,
            "{\"other_process_handler\":\"Nguyen Van B\",\"user_other_process_handler\":\"nguyenvanb\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("tralai");

        useCase.execute(request);

        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).doesNotContainKey("other_process_handler");
        assertThat(fields).doesNotContainKey("user_other_process_handler");
        assertThat(fields).doesNotContainKey("status_pre");
    }

    @Test
    void execute_chonNguoiTraLai_shouldBehaveExactlyLikeTraLai() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.PENDING_SIGNATURE, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chonnguoitralai");

        useCase.execute(request);

        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.RETURNED);
    }

    @Test
    void execute_thuhoi_shouldReadStatusPreFromDocumentNotClient() {
        DuThaoDocument document = newDocument(
            "duthao", DuThaoStatus.RETURNED, "{\"status_pre\":\"" + DuThaoStatus.FORWARDED_FOR_APPROVAL + "\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("thuhoi");

        useCase.execute(request);

        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.FORWARDED_FOR_APPROVAL);
    }

    @Test
    void execute_thuhoi_withoutStatusPre_shouldFallBackToDrafting() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.PENDING_SIGNATURE, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("thuhoi");

        useCase.execute(request);

        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.DRAFTING);
    }

    @Test
    void execute_danhgiaduthao_shouldOnlySetDraftRateAndNotChangeStatus() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.PUBLISHED, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("danhgiaduthao");
        request.setRating("5");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.getStatus()).isEqualTo(DuThaoStatus.PUBLISHED); // khong doi
        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).containsEntry("draft_rate", "5");
        assertThat(fields).doesNotContainKey("status_pre"); // khong dong cham
    }

    @Test
    void execute_whenActingUserIsCurrentProcessHandler_shouldAppendToUserProcessed() {
        DuThaoDocument document = newDocument(
            "duthao", DuThaoStatus.FORWARDED_FOR_APPROVAL, "{\"user_process_handler\":\"nguyenvana\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("ketthuc");

        useCase.execute(request);

        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).containsEntry("user_processed", "nguyenvana");
    }

    @Test
    void execute_withNguoiChuyenObj_shouldCopyItOntoDraft() {
        DuThaoDocument document = newDocument("duthao", DuThaoStatus.DRAFTING, "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XuLyDuThaoRequest();
        request.setId(document.getId());
        request.setAction("chuyenduyet");
        request.setNguoiChuyenObj("[{\"username\":\"nguyenvana\"}]");

        useCase.execute(request);

        Map<String, Object> fields = fieldsOf(document);
        assertThat(fields).containsEntry("nguoichuyen_obj", "[{\"username\":\"nguyenvana\"}]");
    }

    private Map<String, Object> fieldsOf(DuThaoDocument document) {
        return dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
    }

    private DuThaoDocument newDocument(String documentType, String status, String dynamicFieldsJson) {
        DuThaoDocument document = new DuThaoDocument("Du thao test", documentType, status, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
