package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentTenantProvider;
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
import com.td.application.sharedcore.OrganizationRepository;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-211 (VBDen — Xử lý & Chuyển tiếp) — DoD (1)/(2)/(3): phân công, ghi ý kiến, chuyển tiếp —
 * tất cả qua {@link ChuyenXuLyVbdenUseCase}, đúng thiết kế Domino thật (1 action, phân biệt bởi
 * tham số {@code action}).
 */
@ExtendWith(MockitoExtension.class)
class ChuyenXuLyVbdenUseCaseTest {

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
    private ProcessDiagramService processDiagramService;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private com.td.application.vbdi.PublishVbdiReplyStatusUseCase publishVbdiReplyStatusUseCase;
    // Bo sung 08/09/2026 (vbden.forward_unit - xem javadoc ChuyenXuLyVbdenUseCase#publishForwardUnitIfCrossTenant):
    // HAU HET test khong set unitProcess nen nhanh nay early-return - khong stub gi o day nen khong
    // co rui ro "unnecessary stubbing" (chi mock, chua goi when()).
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private CurrentTenantProvider currentTenantProvider;
    @Mock
    private OutboxPublisherService outboxPublisherService;
    // Bo sung 08/09/2026 (3, vbden.forward_unit files - xem javadoc VbdenForwardUnitMessagePayload):
    // lenient - test khong lien quan cross-tenant khong stub gi o day.
    @Mock
    private com.td.application.documents.GetDocumentFileRefsUseCase getDocumentFileRefsUseCase;
    @Mock
    private AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final VbdenStatusService vbdenStatusService = new VbdenStatusService();
    private ChuyenXuLyVbdenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChuyenXuLyVbdenUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, vbdenStatusService,
            processDiagramService, resolveIdentifierByLevelUseCase, publishVbdiReplyStatusUseCase,
            organizationRepository, currentTenantProvider, outboxPublisherService, getDocumentFileRefsUseCase,
            authorAclFieldsService);
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parentId);
        request.setAction("phanxuly");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonVbdenParent_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbdi", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldCreateProcessingRecordInVbdenRes() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");
        request.setOpinion("Giao xu ly gap");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        AddDynamicDocumentRequest captured = captor.getValue();
        assertThat(captured.getDocumentType()).isEqualTo("vbden_res");
        assertThat(captured.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(captured.getFields()).containsEntry("action", "phanxuly");
        assertThat(captured.getFields()).containsEntry("opinion", "Giao xu ly gap");
        assertThat(captured.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
        // Bugfix 02/09/2026 (bao loi nguoi dung: tab "Qua trinh xu ly" khong hien "Nguoi thuc hien"):
        // khong gui creatorName -> fallback ve chinh actingUsername (khong de trong).
        assertThat(captured.getFields()).containsEntry("creator", "vanthu01");
        assertThat(captured.getFields()).doesNotContainKey("position");
    }

    /**
     * Bổ sung 08/09/2026 (yêu cầu người dùng "kiểm tra Java đã có chưa, nếu chưa thì port luôn" —
     * xem javadoc {@code ChuyenXuLyVbdenUseCase#publishForwardUnitIfCrossTenant}): {@code unit_process}
     * khớp 1 Organization có {@code linkedTenantId} KHÁC tenant hiện tại → phải publish
     * {@code vbden.forward_unit}.
     */
    @Test
    void execute_unitProcessResolvesToDifferentTenant_shouldPublishForwardUnitMessage() {
        BusinessDocument parent = newDocument("vbden", """
            {"code":"123/QD","promulgation_date":"01/09/2026","deadline_date":"15/09/2026",
             "unit_in_charge":"Bo Tai nguyen va Moi truong","unit_in_charge_identifier":"BTNMT"}
            """);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(currentTenantProvider.currentTenantId()).thenReturn(Optional.of("h05_01"));
        var organization = new Organization("H05.02", "So NN&MT", null, null,
            null, 0, null, 0, null, null, null, null, null, null, null, null, null, null);
        organization.updateLinkedTenant("h05_02");
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.02")).thenReturn(Optional.of(organization));
        var fileRef = com.td.application.documents.VbdiDeliveryFileRef.builder()
            .objectKey("documents/2026/09/08/origin-doc/aaa.pdf").bucketName("td-webapi-files")
            .originalFilename("to_trinh.pdf").size(20480L).contentType("application/pdf").fileExtension("pdf")
            .build();
        when(getDocumentFileRefsUseCase.execute(parent.getId())).thenReturn(List.of(fileRef));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUnitProcess("H05.02");
        request.setOpinion("Chuyen cho don vi ban");

        assertThat(useCase.execute(request).isSuccess()).isTrue();

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(eq("vbden"), eq(parent.getId()), captor.capture());
        var message = captor.getValue();
        assertThat(message.taskType()).isEqualTo("vbden.forward_unit");
        assertThat(message.units()).hasSize(1);
        var unit = message.units().get(0);
        assertThat(unit.tenantId()).isEqualTo("h05_02");
        var payload = (VbdenForwardUnitMessagePayload) unit.payload();
        assertThat(payload.getOriginDocumentId()).isEqualTo(parent.getId().toString());
        assertThat(payload.getOriginTenantId()).isEqualTo("h05_01");
        assertThat(payload.getTargetIdentifier()).isEqualTo("H05.02");
        assertThat(payload.getTargetName()).isEqualTo("So NN&MT");
        assertThat(payload.getActingUsername()).isEqualTo("vanthu01");
        assertThat(payload.getOpinion()).isEqualTo("Chuyen cho don vi ban");
        assertThat(payload.getCode()).isEqualTo("123/QD");
        assertThat(payload.getPromulgationDate()).isEqualTo("01/09/2026");
        assertThat(payload.getDeadlineDate()).isEqualTo("15/09/2026");
        assertThat(payload.getUnitInCharge()).isEqualTo("Bo Tai nguyen va Moi truong");
        assertThat(payload.getUnitInChargeIdentifier()).isEqualTo("BTNMT");
        // Bo sung 08/09/2026 (3, nguoi dung: "thieu files nua... doc chung voi don vi chuyen xu ly
        // luon"): forward NGUYEN VEN tu GetDocumentFileRefsUseCase, khong tu suy doan/copy gi them.
        assertThat(payload.getFiles()).hasSize(1);
        assertThat(payload.getFiles().get(0).getObjectKey()).isEqualTo("documents/2026/09/08/origin-doc/aaa.pdf");
        assertThat(payload.getFiles().get(0).getBucketName()).isEqualTo("td-webapi-files");
        assertThat(payload.getFiles().get(0).getOriginalFilename()).isEqualTo("to_trinh.pdf");
    }

    /** {@code unit_process} khớp Organization CÙNG tenant hiện tại — không publish (xử lý nội bộ đủ). */
    @Test
    void execute_unitProcessResolvesToSameTenant_shouldNotPublishForwardUnitMessage() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(currentTenantProvider.currentTenantId()).thenReturn(Optional.of("h05_01"));
        var organization = new Organization("PHONG_A", "Phong A", null, null,
            null, 0, null, 0, null, null, null, null, null, null, null, null, null, null);
        organization.updateLinkedTenant("h05_01"); // CUNG tenant.
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("PHONG_A")).thenReturn(Optional.of(organization));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUnitProcess("PHONG_A");

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(outboxPublisherService);
    }

    /** Không có {@code unit_process} nào (chỉ giao user_process cá nhân) — không publish, không tra org. */
    @Test
    void execute_withoutUnitProcess_shouldNotAttemptForwardUnitLookup() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(organizationRepository, outboxPublisherService);
    }

    /**
     * Bổ sung 08/09/2026 (yêu cầu người dùng: "muốn phản hồi trạng thái 'Đã đến' và các trạng thái
     * như chuyển xử lý... cho đơn vị 'Chuyển xử lý' vào quá trình xử lý" — xem javadoc
     * {@code ChuyenXuLyVbdenUseCase#publishStatusBackToOriginIfForwardedCopy}): văn bản ĐANG được xử
     * lý có {@code origin_document_id}/{@code origin_tenant_id} (bản sao nhận qua
     * {@code vbden.forward_unit}) → PHẢI publish {@code vbden.forward_unit.status} về tenant gốc.
     */
    @Test
    void execute_onForwardedCopy_shouldPublishStatusBackToOriginTenant() {
        BusinessDocument parent = newDocument("vbden", """
            {"origin_document_id":"11111111-1111-1111-1111-111111111111","origin_tenant_id":"h05_01",
             "identifier_creator":"H05.02"}
            """);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu02"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        var org = new Organization("H05.02", "So NN&MT", null, null,
            null, 0, null, 0, null, null, null, null, null, null, null, null, null, null);
        when(organizationRepository.findByIdentifierAndDeletedOnIsNull("H05.02")).thenReturn(Optional.of(org));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("chuyenvien01");
        request.setOpinion("Giao chuyen vien xu ly");

        assertThat(useCase.execute(request).isSuccess()).isTrue();

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(outboxPublisherService).enqueue(eq("vbden"), eq(parent.getId()), captor.capture());
        var message = captor.getValue();
        assertThat(message.taskType()).isEqualTo("vbden.forward_unit.status");
        var unit = message.units().get(0);
        assertThat(unit.tenantId()).isEqualTo("h05_01");
        var payload = (VbdenForwardUnitStatusMessagePayload) unit.payload();
        assertThat(payload.getOriginDocumentId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(payload.getAction()).isEqualTo("chuyenxuly");
        assertThat(payload.getActionLabel()).isEqualTo("Chuyển xử lý");
        assertThat(payload.getReportingIdentifier()).isEqualTo("H05.02");
        assertThat(payload.getReportingName()).isEqualTo("So NN&MT");
        assertThat(payload.getActingUsername()).isEqualTo("vanthu02");
        assertThat(payload.getOpinion()).isEqualTo("Giao chuyen vien xu ly");
        assertThat(payload.getNewAssigneesLabel()).isEqualTo("chuyenvien01");
    }

    /** Văn bản BÌNH THƯỜNG (không có origin_document_id/origin_tenant_id) — không echo gì cả. */
    @Test
    void execute_onNormalDocument_shouldNotPublishStatusBack() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(outboxPublisherService, organizationRepository);
    }

    /**
     * Bổ sung 07/09/2026 (yêu cầu người dùng: hoàn thiện broker phản hồi trạng thái "phân xử lý" —
     * xem javadoc {@code PublishVbdiReplyStatusUseCase}): có ít nhất 1 người MỚI được giao chủ trì
     * (newAssignees) phải publish STATUS_04 "Phân công xử lý" — dùng {@code dynamic_fields} MỚI
     * NHẤT (sau khi {@code updateParentDocument} chạy) của văn bản CHA.
     */
    @Test
    void execute_withNewAssignees_shouldPublishReplyStatusPhanCongXuLy() {
        BusinessDocument parent = newDocument("vbden", "{\"send_log_id\":\"sl-1\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<com.td.application.vbdi.PublishVbdiReplyStatusRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.PublishVbdiReplyStatusRequest.class);
        verify(publishVbdiReplyStatusUseCase).execute(captor.capture());
        assertThat(captor.getValue().getStatusCode()).isEqualTo("04");
        assertThat(captor.getValue().getStatusLabel()).isEqualTo("Phân công xử lý");
        assertThat(captor.getValue().getActorDisplayName()).isEqualTo("vanthu01");
        assertThat(captor.getValue().getDynamicFields()).containsEntry("send_log_id", "sl-1");
    }

    /**
     * Bổ sung 07/09/2026 (2, "làm nốt" STATUS_05 theo yêu cầu người dùng) — đối chiếu
     * {@code ChuyenXuLy.java:240-260} thật: {@code status.equals("0")} (LẦN ĐẦU phân công) phải
     * publish THÊM STATUS_05 "Đang xử lý" TRƯỚC STATUS_04, kèm {@code statusDetails} = danh sách
     * người mới được giao (";"-nối) trên chính STATUS_04.
     */
    @Test
    void execute_firstAssignmentFromStatusMoi_shouldAlsoPublishReplyStatusDangXuLy() {
        BusinessDocument parent = newDocument("vbden", "{\"send_log_id\":\"sl-1\"}", VbdenStatusService.STATUS_MOI);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana;tranvanb");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<com.td.application.vbdi.PublishVbdiReplyStatusRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.PublishVbdiReplyStatusRequest.class);
        verify(publishVbdiReplyStatusUseCase, org.mockito.Mockito.times(2)).execute(captor.capture());
        var requests = captor.getAllValues();
        assertThat(requests.get(0).getStatusCode()).isEqualTo("05");
        assertThat(requests.get(0).getStatusLabel()).isEqualTo("Đang xử lý");
        assertThat(requests.get(1).getStatusCode()).isEqualTo("04");
        assertThat(requests.get(1).getStatusLabel()).isEqualTo("Phân công xử lý");
        assertThat(requests.get(1).getStatusDetails()).isEqualTo("nguyenvana; tranvanb");
    }

    /** Đang phân công lại (status KHÁC "0") — KHÔNG phát lại STATUS_05, chỉ có STATUS_04 như cũ. */
    @Test
    void execute_reassignmentFromNonMoiStatus_shouldNotPublishReplyStatusDangXuLyAgain() {
        BusinessDocument parent = newDocument("vbden", "{\"send_log_id\":\"sl-1\"}", VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        ArgumentCaptor<com.td.application.vbdi.PublishVbdiReplyStatusRequest> captor =
            ArgumentCaptor.forClass(com.td.application.vbdi.PublishVbdiReplyStatusRequest.class);
        verify(publishVbdiReplyStatusUseCase, org.mockito.Mockito.times(1)).execute(captor.capture());
        assertThat(captor.getValue().getStatusCode()).isEqualTo("04");
    }

    /** Không có người MỚI nào được giao chủ trì (vd chỉ ghi ý kiến/phối hợp) — KHÔNG publish phản hồi. */
    @Test
    void execute_withoutNewAssignees_shouldNotPublishReplyStatus() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenphoihop");
        request.setUserCoordinate("nguyenvana");

        useCase.execute(request);

        verify(publishVbdiReplyStatusUseCase, org.mockito.Mockito.never()).execute(any());
    }

    // Bugfix 02/09/2026 (cung bao loi tren): co gui creatorName/position -> ghi dung 2 field hien
    // thi nay len ban ghi con, KHONG thay the "nguoi_thuc_hien" (van la username tu server).
    @Test
    void execute_withCreatorNameAndPosition_shouldWriteDisplayFieldsOntoChildProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");
        request.setCreatorName("Văn thư Cục");
        request.setPosition("Văn thư");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        Map<String, String> childFields = captor.getValue().getFields();
        assertThat(childFields).containsEntry("nguoi_thuc_hien", "vanthu01");
        assertThat(childFields).containsEntry("creator", "Văn thư Cục");
        assertThat(childFields).containsEntry("position", "Văn thư");
    }

    // Bo sung 02/09/2026 (2 file du lieu mau Domino that nguoi dung cung cap -
    // docs/views_domino/data_vbden_quatrinhxuly_mau*.txt): Domino LUON ghi "identifier" (identifier
    // theo cap cua nguoi thuc hien) tren vbden_res - tab "Xem lien quan" loc theo field nay, truoc day
    // KHONG BAO GIO duoc Java ghi. Resolve server-side qua ResolveIdentifierByLevelUseCase (khong
    // nhan tu request).
    @Test
    void execute_withResolvableIdentifier_shouldWriteIdentifierOntoChildProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.08"));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("identifier", "G10.08");
    }

    @Test
    void execute_withUnresolvableIdentifier_shouldNotWriteIdentifierField() {
        // Vd: chuyen vien thuong (khong co "level" lanh dao) - dung hanh vi Domino that (field
        // "identifier" rong voi tac gia la chuyen vien, xem data_vbden_quatrinhxuly_mau_2.txt dong 2/3).
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("thuhuyen"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("identifier");
    }

    // Bo sung 03/09/2026, SUA LAI 16/09/2026 (2): tich luy "author" gio uy quyen cho
    // AuthorAclFieldsService dung chung (logic day du - kem SET_AUTHOR_FIELDS/identifier-chain - da
    // co test rieng AuthorAclFieldsServiceTest) - o day CHI xac nhan use case nay CO GOI dung, kem
    // dung field map (da merge newAssignees vao user_process) + dung tap field "user_*" lien quan.
    @Test
    void execute_shouldDelegateAuthorAccumulationToSharedService() {
        BusinessDocument parent = newDocument("vbden", "{\"author\":[\"nguoicu\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana;tranvanb");

        useCase.execute(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> fieldsCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Set<String>> touchedCaptor = ArgumentCaptor.forClass(java.util.Set.class);
        verify(authorAclFieldsService).applyOnUpdate(eq("vbden"), fieldsCaptor.capture(), touchedCaptor.capture());
        assertThat(fieldsCaptor.getValue().get("user_process")).asList()
            .containsExactlyInAnyOrder("nguyenvana", "tranvanb");
        assertThat(touchedCaptor.getValue()).contains("user_process", "user_coordinate", "user_to_read");
    }

    // Bo sung 02/09/2026 (bao loi nguoi dung: tab "Qua trinh xu ly" thieu dong "-Chi dao:"): co gui
    // userAssignerObj -> ghi dung field "user_assigner_obj" len ban ghi con, dung ten key ma
    // QuatrinhxulyVBden.jsx da doc san.
    @Test
    void execute_withUserAssignerObj_shouldWriteOntoChildProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");
        request.setUserAssignerObj("[{\"name\":\"Lê Phú Hà\",\"username\":\"haphule\",\"position\":\"Phó Giám đốc\"}]");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        Map<String, String> childFields = captor.getValue().getFields();
        assertThat(childFields.get("user_assigner_obj")).isEqualTo(request.getUserAssignerObj());
    }

    @Test
    void execute_withoutUserAssignerObj_shouldNotWriteFieldOntoChildProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("user_assigner_obj");
    }

    @Test
    void execute_shouldRemoveActingUserAndAddNewAssigneeToUserProcess() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"vanthu01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana;tranvanb");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("user_process")).asList().containsExactlyInAnyOrder("nguyenvana", "tranvanb");
    }

    // Bo sung 22/08/2026 (bugfix nghiem trong, cung dang D.14/R4): user_process luu dang SCALAR
    // TEXT (";"-joined) - truong hop nay xay ra sau khi DonViNguoiNhanXuLy.jsx (FE) duoc sua de
    // TiepNhan/Luu cung ghi user_process (truoc day chi ChuyenXuLy ghi, luon dang List). Neu
    // toStringList khong tolerant, se doc nham RONG va mat het du lieu chu tri da co.
    @Test
    void execute_withUserProcessStoredAsScalarString_shouldStillReadExistingAssignees() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":\"leaderA;leaderB\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("leaderA"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        // leaderA (nguoi thuc hien) bi loai, leaderB (van con trong danh sach cu) PHAI duoc giu lai,
        // khong bi mat du lieu do doc nham RONG.
        assertThat(updatedFields.get("user_process")).asList().containsExactlyInAnyOrder("leaderB", "nguyenvana");
    }

    @Test
    void execute_shouldAppendChuyenXuLyHistoryToParent() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanphoi");
        request.setUserProcess("nguyenvana");
        request.setOpinion("Xin y kien");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat((List<?>) updatedFields.get("chuyenxuly_obj")).hasSize(1);
        // 27/09/2026: dung hop dong Domino TDUlties.updateChuyenXuLyHistory (FE doc user_creator/timeupdate).
        Map<?, ?> entry = (Map<?, ?>) ((List<?>) updatedFields.get("chuyenxuly_obj")).get(0);
        assertThat(entry.get("user_creator")).isEqualTo("vanthu01");
        assertThat(entry.get("user_process")).isEqualTo("nguyenvana");
        assertThat(entry.get("timeupdate").toString()).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
        assertThat(entry.get("opinion")).isEqualTo("Xin y kien");
        assertThat(entry.containsKey("nguoi_chuyen")).isFalse();
        assertThat(entry.containsKey("thoi_gian")).isFalse();

        verify(documentLogService).logUpdate(eq(parent.getId()), any(), any());
        verify(documentCacheService).evict(parent.getId());
    }

    @Test
    void execute_shouldFillUnitAndUserFromObjWhenPlainFieldsBlank() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("leaderA"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcessObj("[{\"username\":\"demo3\",\"identifier\":\"H05.01.01\"}]");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        Map<?, ?> entry = (Map<?, ?>) ((List<?>) updatedFields.get("chuyenxuly_obj")).get(0);
        assertThat(entry.get("user_creator")).isEqualTo("leaderA");
        assertThat(entry.get("user_process")).isEqualTo("demo3;");
        assertThat(entry.get("unit_process")).isEqualTo("H05.01.01;");
    }

    @Test
    void execute_withCoordinateOnlyAction_shouldNotAppendChuyenXuLyHistory() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("leaderA"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenphoihop");
        request.setUserCoordinate("demo4");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("chuyenxuly_obj")).isNull();
    }

    // R8 (22/08/2026): moi nguoi moi duoc giao chu tri (newAssignees) phai duoc ghi 1 node vao
    // so do xu ly, gan duoi actingUsername.
    @Test
    void execute_withNewAssignees_shouldRecordProcessDiagramNodeForEachAssignee() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"2026-08-25\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        UUID processingRecordId = UUID.randomUUID();
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(processingRecordId));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana;tranvanb");
        request.setOpinion("Giao xu ly");

        useCase.execute(request);

        var captor = ArgumentCaptor.forClass(com.td.application.processdiagram.DiagramNodeRequest.class);
        verify(processDiagramService, org.mockito.Mockito.times(2))
            .addNode(eq(parent.getId()), eq("vbden"), eq("vanthu01"), captor.capture());
        List<com.td.application.processdiagram.DiagramNodeRequest> nodes = captor.getAllValues();
        assertThat(nodes).extracting(com.td.application.processdiagram.DiagramNodeRequest::getUsername)
            .containsExactlyInAnyOrder("nguyenvana", "tranvanb");
        assertThat(nodes.get(0).getQuytrinhid()).isEqualTo(processingRecordId.toString());
        assertThat(nodes.get(0).getUsergui()).isEqualTo("vanthu01");
        assertThat(nodes.get(0).getHanxuly()).isEqualTo("2026-08-25");
        assertThat(nodes.get(0).getNoidung()).isEqualTo("Giao xu ly");
    }

    // Bo sung 03/09/2026, sua lai LAN 2 cung ngay (nguoi dung mo ta lai chi tiet luong thuc te - FE
    // chi mo khoa chon "Don vi/nguoi xu ly" SAU KHI da chon "Lanh dao phan cong", xem
    // SubFormPhanVanBan.jsx): 1 lan chuyen xu ly kem CA userProcess (chu tri) LAN userAssignerObj
    // (chi dao) phai la 1 CHUOI TUAN TU van thu -> chi dao -> chu tri, KHONG phai anh em cung cha.
    @Test
    void execute_withUserAssignerObj_shouldEnsureActingUserNodeAndChainChuTriUnderChiDao() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("khtqkien");
        request.setUserAssignerObj("[{\"id\":\"lpha\",\"name\":\"Lê Phú Hà\",\"username\":\"lpha\",\"position\":\"Cục trưởng\"}]");
        request.setCreatorName("Văn thư Cục");
        request.setPosition("Văn thư");

        useCase.execute(request);

        verify(processDiagramService).ensureNode(parent.getId(), "vbden", "vanthu01", "Văn thư Cục", "Văn thư");
        // van thu -> chi dao (lpha): gan duoi actingUsername.
        var chiDaoCaptor = ArgumentCaptor.forClass(com.td.application.processdiagram.DiagramNodeRequest.class);
        verify(processDiagramService).addNode(eq(parent.getId()), eq("vbden"), eq("vanthu01"), chiDaoCaptor.capture());
        assertThat(chiDaoCaptor.getValue().getUsername()).isEqualTo("lpha");
        // chi dao (lpha) -> chu tri (khtqkien): gan duoi "lpha", KHONG phai duoi "vanthu01".
        var chuTriCaptor = ArgumentCaptor.forClass(com.td.application.processdiagram.DiagramNodeRequest.class);
        verify(processDiagramService).addNode(eq(parent.getId()), eq("vbden"), eq("lpha"), chuTriCaptor.capture());
        assertThat(chuTriCaptor.getValue().getUsername()).isEqualTo("khtqkien");
    }

    // Bo sung 04/09/2026 (bao loi nguoi dung, kem anh chup man hinh: chon 1 DON VI lam chu tri -
    // so do the hien THANG username nguoi dai dien "demo1" thay vi TEN DON VI "Phong Cong nghe").
    // Node so do phai hien TEN DON VI (name), nguoi dai dien chi luu vao "nguoidaidien" (FE hien o
    // tooltip, xem tree_model/sodoxuly.jsx#Parent) - danh tinh node (username) van la nguoi dai dien
    // (dung de FE biet ai thuc su duoc giao, khop dung user_process ma SubFormChuyenXuLy.jsx gui).
    @Test
    void execute_withUnitSelectedAsChuTri_shouldShowUnitNameOnNodeAndRepresentativeAsNguoidaidien() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        UUID processingRecordId = UUID.randomUUID();
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(processingRecordId));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("demo1");
        request.setUserProcessObj(
            "[{\"id\":\"0319e523-2d75-48c4-b204-b90b65a39387\",\"name\":\"Phòng Công nghệ\","
                + "\"clerical\":\"0\",\"identifier\":\"H05.01.01\",\"receiver\":\"Demo1\","
                + "\"user_receiver\":\"demo1\"}]");

        useCase.execute(request);

        var captor = ArgumentCaptor.forClass(com.td.application.processdiagram.DiagramNodeRequest.class);
        verify(processDiagramService).addNode(eq(parent.getId()), eq("vbden"), eq("vanthu01"), captor.capture());
        var node = captor.getValue();
        assertThat(node.getUsername()).isEqualTo("demo1");
        assertThat(node.getName()).isEqualTo("Phòng Công nghệ");
        assertThat(node.getNguoidaidien()).isEqualTo("Demo1");
    }

    // Khong co _obj tuong ung (vd goi API truc tiep, khong qua FE) -> fallback ve chinh username,
    // giu nguyen hanh vi cu, khong crash.
    @Test
    void execute_withoutUserProcessObj_shouldFallbackToUsernameAsNodeName() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("demo1");

        useCase.execute(request);

        var captor = ArgumentCaptor.forClass(com.td.application.processdiagram.DiagramNodeRequest.class);
        verify(processDiagramService).addNode(eq(parent.getId()), eq("vbden"), eq("vanthu01"), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("demo1");
        assertThat(captor.getValue().getNguoidaidien()).isNull();
    }

    @Test
    void execute_withOnlyUserAssignerObjAndNoChuTri_shouldStillRecordChiDaoNode() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserAssignerObj("[{\"id\":\"lpha\",\"name\":\"Lê Phú Hà\",\"username\":\"lpha\"}]");

        useCase.execute(request);

        var captor = ArgumentCaptor.forClass(com.td.application.processdiagram.DiagramNodeRequest.class);
        verify(processDiagramService).addNode(eq(parent.getId()), eq("vbden"), eq("vanthu01"), captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("lpha");
    }

    // R8: khong co nguoi moi duoc giao chu tri (vd chi doi coordinate) -> khong ghi so do gi ca.
    @Test
    void execute_withoutNewAssignees_shouldNotRecordProcessDiagramNode() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenphoihop");
        request.setUserCoordinate("nguyenvana");

        useCase.execute(request);

        verify(processDiagramService, org.mockito.Mockito.never()).addNode(any(), any(), any(), any());
    }

    // R8: loi khi ghi so do KHONG duoc lam hong giao dich ChuyenXuLy chinh (bao ve boi try/catch).
    @Test
    void execute_whenProcessDiagramServiceThrows_shouldStillSucceed() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(processDiagramService.addNode(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("loi gia lap"));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
    }

    // Bo sung 22/08/2026 (bao loi FE "Chuyen van ban" mat deadline_date khi cutover khoi Domino):
    // deadlineDate co gia tri -> ghi de len deadline_date hien co, luu nguyen chuoi tho (khong parse).
    @Test
    void execute_withDeadlineDate_shouldUpdateDeadlineDateField() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"20/08/2026\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");
        request.setDeadlineDate("25/08/2026");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("deadline_date")).isEqualTo("25/08/2026");
    }

    @Test
    void execute_withoutDeadlineDate_shouldKeepExistingDeadlineDateUnchanged() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"20/08/2026\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("deadline_date")).isEqualTo("20/08/2026");
    }

    // Bo sung 22/08/2026 (bao loi FE "Chuyen van ban" mat hien thi ten/chuc vu sau chuyen xu ly):
    // userProcessObj/userCoordinateObj/userToReadObj co gia tri -> parse JSON that, ghi vao
    // process_obj/coordinate_obj/to_read_obj.
    @Test
    void execute_withRichObjFields_shouldParseAndStoreAsJsonObjects() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");
        request.setUserProcessObj("[{\"username\":\"nguyenvana\",\"name\":\"Nguyễn Văn A\"}]");
        request.setUserCoordinateObj("[{\"username\":\"tranthib\",\"name\":\"Trần Thị B\"}]");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat((List<?>) updatedFields.get("process_obj")).hasSize(1);
        assertThat((List<?>) updatedFields.get("coordinate_obj")).hasSize(1);
        assertThat(updatedFields.get("to_read_obj")).isNull();
    }

    // Bo sung 01/09/2026 (chuan bi cho "Thu hoi" - N11, xem javadoc lop): 3 field _obj CON PHAI duoc
    // ghi len BAN GHI CON (vbden_res) - dung ten field Domino that (user_process_obj, khac
    // process_obj cua van ban cha) - de ThuHoiVanBanUseCase doc lai dung.
    @Test
    void execute_withRichObjFields_shouldAlsoWriteRawFieldNamesOntoChildProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");
        request.setUserProcessObj("[{\"username\":\"nguyenvana\",\"name\":\"Nguyễn Văn A\"}]");
        request.setUserCoordinateObj("[{\"username\":\"tranthib\",\"name\":\"Trần Thị B\"}]");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        Map<String, String> childFields = captor.getValue().getFields();
        assertThat(childFields.get("user_process_obj")).isEqualTo(request.getUserProcessObj());
        assertThat(childFields.get("user_coordinate_obj")).isEqualTo(request.getUserCoordinateObj());
        assertThat(childFields).doesNotContainKey("user_to_read_obj");
    }

    @Test
    void execute_withoutRichObjFields_shouldNotTouchExistingObjFields() {
        BusinessDocument parent = newDocument("vbden",
            "{\"process_obj\":[{\"username\":\"old\"}]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat((List<?>) updatedFields.get("process_obj")).hasSize(1);
    }

    @Test
    void execute_withChuyenPhoiHop_shouldUpdateUserCoordinateAndUnitCoordinate() {
        BusinessDocument parent = newDocument("vbden", "{\"user_coordinate\":[\"vanthu01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenphoihop");
        request.setUserCoordinate("nguyenvana");
        request.setUnitCoordinate("PHONG_TH");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("user_coordinate")).asList().containsExactlyInAnyOrder("nguyenvana");
        assertThat(updatedFields.get("unit_coordinate")).asList().containsExactly("PHONG_TH");
        assertThat(updatedFields.get("user_coordinated_movement")).asList().containsExactly("vanthu01");
    }

    @Test
    void execute_withChuyenXemDeBiet_shouldUpdateUserToReadAndUnitToRead() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxemdebiet");
        request.setUserToRead("nguyenvana;tranvanb");
        request.setUnitToRead("PHONG_TH");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("user_to_read")).asList().containsExactlyInAnyOrder("nguyenvana", "tranvanb");
        assertThat(updatedFields.get("unit_to_read")).asList().containsExactly("PHONG_TH");
    }

    @Test
    void execute_withUserTrack_shouldAppendToUserTrackWithoutRemovingActingUser() {
        BusinessDocument parent = newDocument("vbden", "{\"user_track\":[\"vanthu01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserTrack("nguyenvana");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        // Domino updateTheodoi() chi them, khong loai nguoi thuc hien khoi user_track.
        assertThat(updatedFields.get("user_track")).asList().containsExactlyInAnyOrder("vanthu01", "nguyenvana");
    }

    @Test
    void execute_withUserManage_shouldAppendToUserManageWithoutRemovingActingUser() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserManage("nguyenvana");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("user_manage")).asList().containsExactly("nguyenvana");
    }

    @Test
    void execute_withPhanPhoi_shouldMarkActingUserAsDistributed() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanphoi");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("user_distributed")).asList().containsExactly("vanthu01");
        assertThat(updatedFields.get("user_delivered")).isNull();
    }

    @Test
    void execute_withPhanXuLy_shouldMarkActingUserAsDelivered() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("phanxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("user_delivered")).asList().containsExactly("vanthu01");
        assertThat(updatedFields.get("user_distributed")).isNull();
    }

    // Sua loi Slice 1: "status" workflow (0-4) cua vbden la COT TINH document.status, khong phai key
    // trong dynamic_fields (xem ghi chu ChuyenXuLyVbdenUseCase.updateParentDocument) - parent phai
    // duoc dung voi status qua constructor, va assertion phai doc parent.getStatus() sau khi execute
    // (khong con qua updatedFields.get("status") nhu truoc, vi ho da khong con ghi vao do nua).

    // Bugfix 31/08/2026 (xem javadoc parseDate): du lieu that "dd/MM/yyyy" (khong phai ISO nhu truoc
    // day gia dinh nham - da xac nhan qua doi chieu FE/DynamicFieldValueConverter).
    @Test
    void execute_withStatusMoiAndOverdueDeadline_shouldRecomputeStatusToQuaHan() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"01/01/2020\"}", "0");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DANG_XU_LY);
    }

    @Test
    void execute_withStatusMoiAndFutureDeadline_shouldRecomputeStatusToConHan() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"01/01/2099\"}", "0");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
    }

    @Test
    void execute_withStatusNotMoi_shouldNotRecomputeStatus() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"01/01/2020\"}", "2");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        useCase.execute(request);

        // status da khac "0" tu truoc -> khong doan lai, giu nguyen (dung ChuyenXuLy.java:240-247).
        assertThat(parent.getStatus()).isEqualTo("2");
    }

    // Bugfix 04/09/2026 (bao loi nguoi dung: tab "Dang xu ly" - dangxuly, status IN ('1','2') - luon
    // rong voi du lieu that trong tenant, xac nhan qua truy van DB truc tiep: 17/19 van ban vbden ket
    // status="0" mai mai, deadline_date rong het). Domino that (ChuyenXuLy.java:240-247) KHONG bo qua
    // khi thieu deadline_date - tinhSoNgayConLai that (TDDocumentUlties.java:2208-2215) tra ve 0 (khong
    // throw) khi deadline_date rong/loi -> status van chuyen "0"->"2" (con han, dang xu ly) ngay, GIONG
    // HET bugfix da xac nhan/ap dung o KetThucXuLyUseCase#recomputeStatus. Test nay TRUOC DAY assert
    // sai hanh vi (status van la "0") - da sua lai dung Domino that.
    @Test
    void execute_withStatusMoiButMissingDeadline_shouldRecomputeStatusToConHanNotSkip() {
        BusinessDocument parent = newDocument("vbden", "{}", "0");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
    }

    @Test
    void execute_withStatusMoiAndUnparseableDeadline_shouldRecomputeStatusToConHanNotSkip() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"not-a-date\"}", "0");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new ChuyenXuLyVbdenRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyenxuly");
        request.setUserProcess("nguyenvana");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
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
