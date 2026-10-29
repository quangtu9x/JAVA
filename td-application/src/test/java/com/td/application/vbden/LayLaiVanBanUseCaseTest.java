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
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N18 (04/09/2026 — "Lấy lại VB") — xem javadoc {@link LayLaiVanBanUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class LayLaiVanBanUseCaseTest {

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
    private AppUserRepository appUserRepository;
    @Mock
    private GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    // Bo sung 08/09/2026 (giai doan 2 vbden.forward_unit - xem javadoc LayLaiVanBanUseCase): dung
    // instance THAT (khong mock) voi OutboxPublisherService mock ben duoi.
    @Mock
    private OutboxPublisherService outboxPublisherService;
    @Mock
    private AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final VbdenStatusService vbdenStatusService = new VbdenStatusService();
    private PublishForwardUnitRevokeUseCase publishForwardUnitRevokeUseCase;
    private LayLaiVanBanUseCase useCase;

    @BeforeEach
    void setUp() {
        publishForwardUnitRevokeUseCase = new PublishForwardUnitRevokeUseCase(outboxPublisherService);
        useCase = new LayLaiVanBanUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, vbdenStatusService,
            processDiagramService, resolveIdentifierByLevelUseCase, appUserRepository,
            getUserOrganizationProfileUseCase, publishForwardUnitRevokeUseCase, authorAclFieldsService, com.td.application.realtime.RealtimeNotifierTestSupport.noop());
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new LayLaiVanBanRequest();
        request.setParentid(parentId);
        request.setOpinion("Ly do");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Ly do");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Ly do");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutActingAppUserProfile_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.empty());

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Ly do");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withCandidateInSameDonVi_shouldRevokeFromAllFourFields() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvanb\"],\"user_coordinate\":[\"nguyenvanb\"],"
                + "\"user_to_read\":[\"nguyenvanb\"],\"user_track\":[\"nguyenvanb\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "5");
        stubProfile(candidate, "G10.01.02;G10.01;", "8");

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai xu ly");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("vanthu01"));
        assertThat(updated.get("user_coordinate")).isEqualTo(List.of());
        assertThat(updated.get("user_to_read")).isEqualTo(List.of());
        assertThat(updated.get("user_track")).isEqualTo(List.of());
        assertThat(updated.get("user_recalled")).isEqualTo(List.of("vanthu01"));
        verify(processDiagramService).removeNode(parent.getId(), "vbden", "nguyenvanb");
    }

    @Test
    void execute_withCandidateInDifferentDonVi_shouldNotRevoke() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvanb\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "5");
        stubProfile(candidate, "G20.02;", "5");

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai xu ly");

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("nguyenvanb", "vanthu01"));
        verify(processDiagramService, never()).removeNode(any(), any(), any());
    }

    @Test
    void execute_withActingUserLevel1_shouldReclaimAllCandidatesUnconditionally() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvanb\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 1);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "1");

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai xu ly");

        useCase.execute(request);

        // Level "1" -> thu hoi TAT CA khong dieu kien - khong can tra AppUser cua "nguyenvanb".
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("vanthu01"));
        verify(processDiagramService).removeNode(parent.getId(), "vbden", "nguyenvanb");
    }

    @Test
    void execute_withIdentifierCreatorMatchingActingUser_shouldResetAssignerFields() {
        BusinessDocument parent = newDocument("vbden",
            "{\"identifier_creator\":\"G10.01\",\"assigner\":\"Nguyen Van A\","
                + "\"user_assigner\":\"nguyenvana\",\"process_obj\":[{\"name\":\"x\"}]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "5");

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai xu ly");

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("assigner")).isEqualTo("");
        assertThat(updated.get("user_assigner")).isEqualTo("");
        assertThat(updated.get("process_obj")).isEqualTo("");
    }

    @Test
    void execute_withIdentifierCreatorNotMatchingActingUser_shouldNotResetAssignerFields() {
        BusinessDocument parent = newDocument("vbden",
            "{\"identifier_creator\":\"G20.02\",\"assigner\":\"Nguyen Van A\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "5");

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai xu ly");

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("assigner")).isEqualTo("Nguyen Van A");
    }

    @Test
    void execute_withOverdueDeadline_shouldRecomputeStatusToQuaHan() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"01/01/2020\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "5");

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai xu ly");

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DANG_XU_LY);
    }

    @Test
    void execute_withoutDeadlineDate_shouldDefaultToConHan() {
        BusinessDocument parent = newDocument("vbden", "{}", "3");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "5");

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai xu ly");

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
    }

    @Test
    void execute_shouldCreateProcessingRecordWithActionOpinionAndIdentifier() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "5");
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.01"));

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai xu ly");
        request.setCreatorName("Van Thu Cuc");
        request.setPosition("Van thu");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "laylaivanban");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Lay lai xu ly");
        assertThat(childRequest.getFields()).containsEntry("creator", "Van Thu Cuc");
        assertThat(childRequest.getFields()).containsEntry("position", "Van thu");
        assertThat(childRequest.getFields()).containsEntry("identifier", "G10.01");
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
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "5");

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai vi gui nham");

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
        assertThat(payload.getOpinion()).isEqualTo("Lay lai vi gui nham");
        assertThat(payload.getReason()).isEqualTo("laylaivanban");

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        List<Map<String, Object>> forwardedUnits = (List<Map<String, Object>>) updated.get("forwarded_units");
        assertThat(forwardedUnits).hasSize(1);
        assertThat(forwardedUnits.get(0).get("revoked")).isEqualTo(true);
    }

    /** Không có {@code forwarded_units} nào — không lỗi, không publish gì. */
    @Test
    void execute_withoutForwardedUnits_shouldNotPublishAnything() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "5");

        var request = new LayLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Lay lai xu ly");

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(outboxPublisherService);
    }

    private void stubProfile(AppUser user, String identifiers, String level) {
        var dto = new UserOrganizationProfileDto();
        dto.setIdentifiers(identifiers);
        dto.setLevel(level);
        lenient().when(getUserOrganizationProfileUseCase.buildProfileForUser(user)).thenReturn(dto);
    }

    private AppUser newAppUser(String username, String identifier, Integer level) {
        return new AppUser(
            "sub-" + username, username, username, null, null, null, true,
            level, 0, null, null, null, null, null, null, identifier, false, null);
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
