package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.messaging.OutboundMessage;
import com.td.application.messaging.OutboxPublisherService;
import com.td.application.processdiagram.ProcessDiagramService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.vbden.VbdenResDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N11 (01/09/2026 — "Thu hồi", nhánh {@code thuHoiToanBo()}) — xem javadoc {@link ThuHoiVanBanUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ThuHoiVanBanUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private VbdenResDocumentRepository vbdenResDocumentRepository;
    @Mock
    private AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ProcessDiagramService processDiagramService;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    // Bo sung 08/09/2026 (giai doan 2 vbden.forward_unit - xem javadoc ThuHoiVanBanUseCase): dung
    // instance THAT (khong mock) voi OutboxPublisherService mock ben duoi, de test duoc CA hanh vi
    // cascadeRevoke that thay vi chi verify goi ham suong.
    @Mock
    private OutboxPublisherService outboxPublisherService;
    @Mock
    private AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final VbdenStatusService vbdenStatusService = new VbdenStatusService();
    private PublishForwardUnitRevokeUseCase publishForwardUnitRevokeUseCase;
    private ThuHoiVanBanUseCase useCase;

    @BeforeEach
    void setUp() {
        publishForwardUnitRevokeUseCase = new PublishForwardUnitRevokeUseCase(outboxPublisherService);
        useCase = new ThuHoiVanBanUseCase(
            documentRepository, vbdenResDocumentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, vbdenStatusService,
            processDiagramService, resolveIdentifierByLevelUseCase, publishForwardUnitRevokeUseCase,
            authorAclFieldsService);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(vbdenResDocumentRepository.findByParentId(any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1000), 0));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parentId);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedDocument_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        parent.markAsDeleted(null);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    // Nguoi trong snapshot ma HIEN VAN CON trong user_process (chua xu ly) bi thu hoi; nguoi KHONG
    // con trong user_process (da xu ly xong hoac chua tung duoc giao that) KHONG bi dung tram.
    @Test
    void execute_shouldRevokeOnlyStillPendingRecipientFromSnapshot() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvana\",\"tranthic\"],\"user_processed\":[]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"vanthu01\","
            + "\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":\"[{\\\"username\\\":\\\"nguyenvana\\\",\\\"name\\\":\\\"Nguyen Van A\\\"},"
            + "{\\\"username\\\":\\\"daxulyxongroi\\\",\\\"name\\\":\\\"Da xu ly xong\\\"}]\"}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Can xem lai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<String> userProcess = (List<String>) updatedFields.get("user_process");
        // nguyenvana bi thu hoi (con pending); tranthic khong lien quan snapshot nen giu nguyen;
        // "vanthu01" (nguoi thao tac) duoc them lai vao user_process.
        assertThat(userProcess).containsExactlyInAnyOrder("tranthic", "vanthu01");
        assertThat((List<?>) updatedFields.get("user_recalled")).asList().contains("vanthu01");
        verify(processDiagramService).removeNode(parent.getId(), "vbden", "nguyenvana");
        verify(processDiagramService, never()).removeNode(any(), any(), eq("daxulyxongroi"));
    }

    // Bugfix 14/09/2026 (phát hiện khi điều tra báo lỗi GetNguoiTraLai trả trống cho demo2 — xem
    // javadoc VbdenObjSnapshotFields): dữ liệu THẬT lưu "user_process_obj" dưới dạng MẢNG JSON LỒNG
    // (jsonb_typeof = "array"), KHÔNG PHẢI chuỗi JSON escaped như fixture ở test phía trên (đúng giả
    // định SAI của code trước bản fix này, khiến "thu hồi toàn bộ" KHÔNG BAO GIỜ thu hồi được ai qua
    // nhánh chuyenxuly trong thực tế). Test này dùng ĐÚNG hình dạng thật để tránh tái diễn.
    @Test
    void execute_snapshotStoredAsNativeNestedArray_shouldStillRevokePendingRecipient() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvana\"],\"user_processed\":[]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"vanthu01\","
            + "\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":[{\"username\":\"nguyenvana\",\"name\":\"Nguyen Van A\"}]}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Can xem lai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<String> userProcess = (List<String>) updatedFields.get("user_process");
        assertThat(userProcess).containsExactlyInAnyOrder("vanthu01");
        verify(processDiagramService).removeNode(parent.getId(), "vbden", "nguyenvana");
    }

    @Test
    void execute_shouldIgnoreHistoryRecordsCreatedByOtherUsers() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvana\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"nguoikhac\","
            + "\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":\"[{\\\"username\\\":\\\"nguyenvana\\\"}]\"}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        // nguyenvana KHONG bi thu hoi vi ban ghi lich su do NGUOI KHAC tao, khong phai nguoi dang thao tac.
        assertThat((List<?>) updatedFields.get("user_process")).asList().contains("nguyenvana");
        verify(processDiagramService, never()).removeNode(any(), any(), eq("nguyenvana"));
    }

    @Test
    void execute_shouldIgnoreNonRevocableActions() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvana\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"vanthu01\","
            + "\"action\":\"phanxuly\","
            + "\"user_process_obj\":\"[{\\\"username\\\":\\\"nguyenvana\\\"}]\"}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat((List<?>) updatedFields.get("user_process")).asList().contains("nguyenvana");
    }

    @Test
    void execute_shouldCreateProcessingRecordWithActionThuHoiAndRevokedSnapshot() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvana\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        VbdenResDocument historyRecord = newResRecord("{"
            + "\"parentid\":\"" + parent.getId() + "\",\"nguoi_thuc_hien\":\"vanthu01\","
            + "\"action\":\"chuyenxuly\","
            + "\"user_process_obj\":\"[{\\\"username\\\":\\\"nguyenvana\\\",\\\"name\\\":\\\"Nguyen Van A\\\"}]\"}");
        when(vbdenResDocumentRepository.findByParentId(eq(parent.getId().toString()), any()))
            .thenReturn(new PageImpl<>(List.of(historyRecord), PageRequest.of(0, 1000), 1));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Can xem lai");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        AddDynamicDocumentRequest childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("action", "thuhoi");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Can xem lai");
        assertThat(childRequest.getFields().get("user_process_obj")).contains("nguyenvana");
    }

    // Bo sung 03/09/2026 (cung gap voi ChuyenXuLyVbdenUseCase - xem javadoc lop do).
    @Test
    void execute_withResolvableIdentifier_shouldWriteIdentifierOntoChildProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvana\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.08"));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Can xem lai");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("identifier", "G10.08");
    }

    @Test
    void execute_withUnresolvableIdentifier_shouldNotWriteIdentifierField() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvana\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Can xem lai");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("identifier");
    }

    @Test
    void execute_withOverdueDeadline_shouldRecomputeStatusToQuaHan() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"01/01/2020\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DANG_XU_LY);
    }

    /**
     * Bổ sung 08/09/2026 (giai đoạn 2 {@code vbden.forward_unit} — xem javadoc lớp): văn bản có
     * {@code forwarded_units} CHƯA {@code revoked} → PHẢI publish {@code vbden.forward_unit.revoke}
     * cho tenant đích, VÀ đánh dấu {@code revoked=true} ngay trong dynamic_fields được lưu lại.
     */
    @Test
    void execute_withForwardedUnits_shouldPublishForwardUnitRevokeAndMarkRevoked() {
        BusinessDocument parent = newDocument("vbden", """
            {"forwarded_units":[{"identifier":"H05.02","tenantId":"h05_02","documentId":"11111111-1111-1111-1111-111111111111"}]}
            """);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Thu hoi vi sai sot");

        assertThat(useCase.execute(request).isSuccess()).isTrue();

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(eq("vbden"), eq(parent.getId()), captor.capture());
        var message = captor.getValue();
        assertThat(message.taskType()).isEqualTo("vbden.forward_unit.revoke");
        var unit = message.units().get(0);
        assertThat(unit.tenantId()).isEqualTo("h05_02");
        var payload = (VbdenForwardUnitRevokeMessagePayload) unit.payload();
        assertThat(payload.getTargetDocumentId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(payload.getOriginDocumentId()).isEqualTo(parent.getId().toString());
        assertThat(payload.getActingUsername()).isEqualTo("vanthu01");
        assertThat(payload.getOpinion()).isEqualTo("Thu hoi vi sai sot");
        assertThat(payload.getReason()).isEqualTo("thuhoi");

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        List<Map<String, Object>> forwardedUnits = (List<Map<String, Object>>) updatedFields.get("forwarded_units");
        assertThat(forwardedUnits).hasSize(1);
        assertThat(forwardedUnits.get(0).get("revoked")).isEqualTo(true);
    }

    /** Entry {@code forwarded_units} ĐÃ {@code revoked} từ trước — KHÔNG publish lại. */
    @Test
    void execute_withAlreadyRevokedForwardedUnit_shouldNotPublishAgain() {
        BusinessDocument parent = newDocument("vbden", """
            {"forwarded_units":[{"identifier":"H05.02","tenantId":"h05_02",
              "documentId":"11111111-1111-1111-1111-111111111111","revoked":true}]}
            """);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(outboxPublisherService);
    }

    /** Không có {@code forwarded_units} nào — không lỗi, không publish gì. */
    @Test
    void execute_withoutForwardedUnits_shouldNotPublishAnything() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(outboxPublisherService);
    }

    // Bo sung 18/09/2026 (cutover ModalThuHoiVanBan.jsx, nhanh "select" - xem javadoc lop muc "Bo
    // sung 18/09/2026"): type="select" tin THANG 4 mang *Obj tu request, KHONG doc lich su vbden_res.
    @Test
    void execute_withTypeSelect_shouldRevokeExactlyRequestedRecipients() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvana\",\"tranthic\"],\"user_processed\":[]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Chon thu hoi 1 nguoi");
        request.setType("select");
        request.setUserProcessObj("[{\"username\":\"nguyenvana\",\"name\":\"Nguyen Van A\"}]");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        @SuppressWarnings("unchecked")
        List<String> userProcess = (List<String>) updatedFields.get("user_process");
        // nguyenvana bi thu hoi (co trong request); tranthic KHONG bi dung tram (khong co trong
        // request, du van dang "chua xu ly") - khac han nhanh "all" (derive tu lich su).
        assertThat(userProcess).containsExactlyInAnyOrder("tranthic", "vanthu01");
        verify(processDiagramService).removeNode(parent.getId(), "vbden", "nguyenvana");
        // Nhanh "select" KHONG can quet lich su vbden_res.
        verify(vbdenResDocumentRepository, never()).findByParentId(any(), any());
    }

    // "Theo doi" (user_track) CHI duoc thu hoi o nhanh "select" (doc SONG, khac nhanh "all" - xem
    // gioi han (2) trong javadoc lop, van dung cho nhanh "all").
    @Test
    void execute_withTypeSelect_shouldRevokeUserTrack() {
        BusinessDocument parent = newDocument("vbden", "{\"user_track\":[\"quanlyvien01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Thu hoi theo doi");
        request.setType("select");
        request.setUserTrackObj("[{\"username\":\"quanlyvien01\",\"name\":\"Quan Ly Vien\"}]");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat((List<?>) updatedFields.get("user_track")).asList().doesNotContain("quanlyvien01");
        verify(processDiagramService).removeNode(parent.getId(), "vbden", "quanlyvien01");

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields().get("user_track_obj")).contains("quanlyvien01");
    }

    // Regression: khong gui "type" (hoac gui "all") PHAI bo qua hoan toan 4 field *Obj neu FE lo gui
    // kem (khong xay ra thuc te, nhung dam bao nhanh mac dinh khong bao gio bi anh huong).
    @Test
    void execute_withoutType_shouldIgnoreObjFieldsAndKeepToanBoBehavior() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvana\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new ThuHoiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Khong lien quan");
        request.setUserProcessObj("[{\"username\":\"nguyenvana\"}]");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        // Nhanh mac dinh derive tu lich su vbden_res (rong trong test nay) - "nguyenvana" KHONG bi
        // thu hoi du co trong userProcessObj, vi field do bi bo qua hoan toan o nhanh mac dinh.
        assertThat((List<?>) updatedFields.get("user_process")).asList().contains("nguyenvana");
        verify(vbdenResDocumentRepository).findByParentId(any(), any());
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    // BL-210 DoD (4), yeu cau nguoi dung 05/09/2026 ("tach vbden_res sang bang rieng"): ban ghi lich
    // su xu ly gio la VbdenResDocument (bang doc lap), khong con la BusinessDocument.
    private VbdenResDocument newResRecord(String dynamicFieldsJson) {
        VbdenResDocument document = new VbdenResDocument("Xu ly", "vbden_res", "DRAFT", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
