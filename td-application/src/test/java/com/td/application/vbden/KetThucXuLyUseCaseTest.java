package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N17 (04/09/2026 — "Kết thúc xử lý") — xem javadoc {@link KetThucXuLyUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class KetThucXuLyUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private com.td.application.vbdi.PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;
    @Mock
    private AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final VbdenStatusService vbdenStatusService = new VbdenStatusService();
    private KetThucXuLyUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new KetThucXuLyUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, vbdenStatusService,
            resolveIdentifierByLevelUseCase, publishVbdiReplyStatusUseCase, authorAclFieldsService);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new KetThucXuLyRequest();
        request.setParentid(parentId);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedDocument_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        parent.markAsDeleted(null);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    // Domino ketthucxuly(): loai actingUsername KHONG dieu kien (khac CapNhatKetQuaXuLyUseCase, khong
    // tra identifiers/don vi) khoi toan bo 7 field, them vao user_processed.
    @Test
    void execute_shouldRemoveActingUsernameFromAllProcessFieldsAndAddToProcessed() {
        BusinessDocument parent = newDocument("vbden", "{"
            + "\"user_process\":[\"vanthu01\",\"nguyenvanb\"],"
            + "\"user_receiver\":[\"vanthu01\"],"
            + "\"user_processing\":[\"vanthu01\"],"
            + "\"user_coordinate\":[\"vanthu01\"],"
            + "\"user_to_read\":[\"vanthu01\"],"
            + "\"user_distributor\":[\"vanthu01\"],"
            + "\"user_returned\":[\"vanthu01\"]"
            + "}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("nguyenvanb"));
        assertThat(updated.get("user_receiver")).isEqualTo(List.of());
        assertThat(updated.get("user_processing")).isEqualTo(List.of());
        assertThat(updated.get("user_coordinate")).isEqualTo(List.of());
        assertThat(updated.get("user_to_read")).isEqualTo(List.of());
        assertThat(updated.get("user_distributor")).isEqualTo(List.of());
        assertThat(updated.get("user_returned")).isEqualTo(List.of());
        assertThat(updated.get("user_processed")).isEqualTo(List.of("vanthu01"));
    }

    @Test
    void execute_withActingUsernameAlreadyProcessed_shouldNotDuplicate() {
        BusinessDocument parent = newDocument("vbden", "{\"user_processed\":[\"vanthu01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_processed")).isEqualTo(List.of("vanthu01"));
    }

    @Test
    void execute_withOverdueDeadlineAndNoRemainingAssignee_shouldRecomputeStatusToQuaHanDaXuLy() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"vanthu01\"],\"deadline_date\":\"01/01/2020\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY);
    }

    @Test
    void execute_withFutureDeadlineAndRemainingAssignee_shouldRecomputeStatusToConHanDangXuLy() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"vanthu01\",\"nguyenvanb\"],\"deadline_date\":\"01/01/2099\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
    }

    // Bugfix 04/09/2026 (bao loi nguoi dung: status khong chuyen sang "da xu ly" sau ketthucxuly) -
    // Domino tinhSoNgayConLai() that: BAT KY LOI NAO (thieu deadline_date) -> tra ve 0, KHONG bo qua
    // buoc ghi status (xem javadoc KetThucXuLyUseCase#recomputeStatus). Truoc day use case nay SAI:
    // bo qua hoan toan viec tinh status khi thieu deadline_date, giu nguyen status cu.
    @Test
    void execute_withoutDeadlineDateAndNoRemainingAssignee_shouldRecomputeStatusToDungHanDaXuLy() {
        BusinessDocument parent = newDocument("vbden", "{}", "2");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY);
    }

    @Test
    void execute_withUnparseableDeadlineDateAndRemainingAssignee_shouldRecomputeStatusToConHanDangXuLy() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"vanthu01\",\"nguyenvanb\"],\"deadline_date\":\"not-a-date\"}", "2");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
    }

    @Test
    void execute_shouldCreateProcessingRecordWithActionOpinionCreatorAndPosition() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Da xu ly xong");
        request.setCreatorName("Nguyen Van Thu");
        request.setPosition("Van thu");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "ketthucxuly");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Da xu ly xong");
        assertThat(childRequest.getFields()).containsEntry("creator", "Nguyen Van Thu");
        assertThat(childRequest.getFields()).containsEntry("position", "Van thu");
        assertThat(childRequest.getFields()).containsEntry("identifier", "G10.01");
    }

    @Test
    void execute_withoutCreatorName_shouldFallBackToActingUsername() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("creator", "vanthu01");
    }

    /**
     * Bổ sung 07/09/2026 (2, "làm nốt" STATUS_06 theo yêu cầu người dùng) — đối chiếu
     * {@code CapNhatVanBanDen.java} thật case {@code ketthucxuly}: bắn STATUS_06 "Hoàn thành" VÔ
     * ĐIỀU KIỆN mỗi lần hành động này thành công, {@code staff_name}/actor = người đang thao tác.
     */
    @Test
    void execute_success_shouldPublishReplyStatusHoanThanh() {
        BusinessDocument parent = newDocument("vbden", "{\"send_log_id\":\"sl-1\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Da xu ly xong");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<com.td.application.vbdi.PublishVbdiReplyStatusRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.PublishVbdiReplyStatusRequest.class);
        verify(publishVbdiReplyStatusUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatusCode()).isEqualTo("06");
        assertThat(captor.getValue().getStatusLabel()).isEqualTo("Hoàn thành");
        assertThat(captor.getValue().getStatusDetails()).isEqualTo("Da xu ly xong");
        assertThat(captor.getValue().getActorDisplayName()).isEqualTo("vanthu01");
        assertThat(captor.getValue().getDynamicFields()).containsEntry("send_log_id", "sl-1");
    }

    @Test
    void execute_failure_shouldNotPublishReplyStatus() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new KetThucXuLyRequest();
        request.setParentid(parentId);

        useCase.execute(request);

        org.mockito.Mockito.verifyNoInteractions(publishVbdiReplyStatusUseCase);
    }

    // Bo sung 14/09/2026 (yeu cau nguoi dung "lam luon chuc nang Gan van ban tra loi o modal nay
    // luon") - resObj luu tren ban ghi vbden_res + promulgationDate cua PHAN TU CUOI lam moc thay
    // @Today khi tinh remainingDays (dung Domino processResObj() that - ngaybanhanh bi GHI DE moi
    // vong lap).
    @Test
    void execute_withResObj_shouldPersistOnProcessingRecordAndUseLastPromulgationDateAsReference() {
        LocalDate today = LocalDate.now();
        String deadline = today.plusDays(10).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"" + deadline + "\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var ref1 = new VbdenReplyDocumentRef();
        ref1.setId(UUID.randomUUID());
        ref1.setCode("VBDI-001");
        ref1.setPromulgationDate(today.minusDays(20).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        var ref2 = new VbdenReplyDocumentRef();
        ref2.setId(UUID.randomUUID());
        ref2.setCode("VBDI-002");
        // Qua han so voi deadline (chi con 10 ngay tinh tu hom nay) - phan tu CUOI nay phai la moc
        // duoc dung, khong phai ref1.
        ref2.setPromulgationDate(today.plusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());
        request.setResObj(List.of(ref1, ref2));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        // remainingDays tinh tu ref2 (moc = today+30) den deadline (today+10) = -20 (< 0) -> qua han.
        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        String resObjJson = captor.getValue().getFields().get("res_obj");
        assertThat(resObjJson).contains("VBDI-001").contains("VBDI-002");
    }

    @Test
    void execute_withoutResObj_shouldNotIncludeResObjFieldOnProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new KetThucXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("res_obj");
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        return newDocument(documentType, dynamicFieldsJson, "1");
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson, String status) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, status, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
