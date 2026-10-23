package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DuThaoDocument;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-230 (DuThao Core) — DoD (2): xin ý kiến phối hợp qua {@link XinYKienPhoiHopUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class XinYKienPhoiHopUseCaseTest {

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private AddDraftProcessDocumentUseCase addDraftProcessDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private XinYKienPhoiHopUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new XinYKienPhoiHopUseCase(
            duThaoDocumentRepository, addDraftProcessDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, authorAclFieldsService,
            new DraftProcessActorFields(org.mockito.Mockito.mock(com.td.application.sharedcore.AppUserRepository.class)));
    }

    @Test
    void execute_withUnknownDraft_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new XinYKienPhoiHopRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonDuThaoDocument_shouldReturnFailure() {
        DuThaoDocument document = newDraft("vbdi", "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new XinYKienPhoiHopRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldAddNewUsersToCoordinatorList() {
        DuThaoDocument draft = newDraft("duthao", "{}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguoikhoitao"));

        var request = new XinYKienPhoiHopRequest();
        request.setId(draft.getId());
        request.setNguoiPhoiHop(List.of("nguoivana", "nguoivanb"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(fields.get("user_coordinator")).asList().containsExactlyInAnyOrder("nguoivana", "nguoivanb");
        assertThat(fields.get("user_coordinated")).asList().isEmpty();
    }

    @Test
    void execute_whenActingUserIsInCoordinatorList_shouldMoveToCoordinated() {
        DuThaoDocument draft = newDraft("duthao", "{\"user_coordinator\":[\"nguyenvana\",\"tranvanb\"]}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new XinYKienPhoiHopRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(fields.get("user_coordinator")).asList().containsExactly("tranvanb");
        assertThat(fields.get("user_coordinated")).asList().containsExactly("nguyenvana");
        verify(documentLogService).logUpdate(any(), any(), any());
    }

    // 26/09/2026: FE ghi user_coordinator dang chuoi scalar "a;b" qua Luu (DB that tenant skhcn) -
    // truoc day bi coi la rong (khong phan hoi duoc, con ghi de thanh []).
    @Test
    void execute_whenCoordinatorStoredAsDelimitedString_shouldMoveUserAndKeepStringShape() {
        DuThaoDocument draft = newDraft("duthao", "{\"user_coordinator\":\"nguyenvana;tranvanb\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        UUID childId = UUID.randomUUID();
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(childId));

        var request = new XinYKienPhoiHopRequest();
        request.setId(draft.getId());

        var result = useCase.execute(request);

        assertThat(result.getData()).isEqualTo(childId);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(fields.get("user_coordinator")).isEqualTo("tranvanb");
        assertThat(fields.get("user_coordinated")).isEqualTo("nguyenvana");
        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("opinion", "Đã phối hợp");
    }

    @Test
    void execute_whenOpinionRecordFails_shouldReturnFailure() {
        DuThaoDocument draft = newDraft("duthao", "{\"user_coordinator\":\"nguyenvana\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.failure("loi"));

        var request = new XinYKienPhoiHopRequest();
        request.setId(draft.getId());
        request.setOpinion("Y kien");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withOpinionAndResponse_shouldCreateOpinionRecord() {
        DuThaoDocument draft = newDraft("duthao", "{\"user_coordinator\":[\"nguyenvana\"]}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        UUID childId = UUID.randomUUID();
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(childId));

        var request = new XinYKienPhoiHopRequest();
        request.setId(draft.getId());
        request.setOpinion("Dong y voi noi dung du thao");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(childId);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(captor.capture());
        // Bang ghi y kien phoi hop CHINH LA draft_process (xem javadoc use case): documentType khong
        // do caller dat - AddDraftProcessDocumentUseCase tu ep 'draft_process'; field theo chuan
        // draft_process (parentid/action/receiver_username/opinion/creator).
        assertThat(captor.getValue().getFields()).containsEntry("parentid", draft.getId().toString());
        assertThat(captor.getValue().getFields()).containsEntry("action_type", "ykienphoihop");
        assertThat(captor.getValue().getFields()).containsEntry("receiver_username", "nguyenvana");
        assertThat(captor.getValue().getFields()).containsEntry("opinion", "Dong y voi noi dung du thao");
        assertThat(captor.getValue().getFields()).containsEntry("creator", "nguyenvana");
    }

    @Test
    void execute_withOpinionButNotResponding_shouldNotCreateOpinionRecord() {
        DuThaoDocument draft = newDraft("duthao", "{}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguoikhoitao"));

        var request = new XinYKienPhoiHopRequest();
        request.setId(draft.getId());
        request.setNguoiPhoiHop(List.of("nguoivana"));
        request.setOpinion("Y kien khong hop le vi khong phai nguoi phan hoi");

        useCase.execute(request);

        verify(addDraftProcessDocumentUseCase, never()).execute(any());
    }

    // 20/10/2026: người đã cho ý kiến (user_coordinated) cho thêm ý kiến lần 2 -> ghi thêm vết, danh sách giữ nguyên.
    @Test
    void execute_secondOpinionFromCoordinatedUser_shouldCreateAnotherOpinionRecord() {
        DuThaoDocument draft = newDraft("duthao", "{\"user_coordinator\":\"tranvanb\",\"user_coordinated\":\"nguyenvana\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        UUID childId = UUID.randomUUID();
        when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(childId));

        var request = new XinYKienPhoiHopRequest();
        request.setId(draft.getId());
        request.setOpinion("Bo sung y kien");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(childId);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(fields).containsEntry("user_coordinator", "tranvanb");
        assertThat(fields).containsEntry("user_coordinated", "nguyenvana");
    }

    @Test
    void execute_opinionWhenDraftPendingSignature_shouldFail() {
        DuThaoDocument draft = new DuThaoDocument("Du thao", "duthao", DuThaoStatus.PENDING_SIGNATURE, null, null, null, null);
        draft.replaceDynamicFields("{\"user_coordinator\":\"nguyenvana\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));

        var request = new XinYKienPhoiHopRequest();
        request.setId(draft.getId());
        request.setOpinion("Y kien muon");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        verify(addDraftProcessDocumentUseCase, never()).execute(any());
        verify(duThaoDocumentRepository, never()).save(any());
    }

    private DuThaoDocument newDraft(String documentType, String dynamicFieldsJson) {
        DuThaoDocument document = new DuThaoDocument("Du thao", documentType, "DRAFTING", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
