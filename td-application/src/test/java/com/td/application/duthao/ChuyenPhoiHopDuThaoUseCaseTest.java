package com.td.application.duthao;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserDelegationTestSupport;
import com.td.domain.duthao.DuThaoDocument;
import com.td.domain.sharedcore.UserDelegation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
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

/** 19/10/2026 — port Domino ChuyenPhoiHopDuThao, xem {@link ChuyenPhoiHopDuThaoUseCase}. */
@ExtendWith(MockitoExtension.class)
class ChuyenPhoiHopDuThaoUseCaseTest {

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
    private AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private ChuyenPhoiHopDuThaoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = newUseCase(new ArrayList<>());
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("cva"));
        lenient().when(duThaoDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addDraftProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    private ChuyenPhoiHopDuThaoUseCase newUseCase(List<UserDelegation> delegations) {
        return new ChuyenPhoiHopDuThaoUseCase(
            duThaoDocumentRepository, addDraftProcessDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, authorAclFieldsService,
            new DraftProcessActorFields(org.mockito.Mockito.mock(com.td.application.sharedcore.AppUserRepository.class)),
            new DuThaoCoordinatorDelegations(UserDelegationTestSupport.resolverWith(delegations)));
    }

    @Test
    void execute_shouldMergeNewCoordinatorsMoveActorToCoordinatedAndCreateProcessRecord() {
        DuThaoDocument draft = newDraft("{\"coordinator\":\"CV A\",\"user_coordinator\":\"cva;cvx\","
            + "\"coordinator_obj\":\"[{\\\"id\\\":\\\"1\\\",\\\"name\\\":\\\"CV A\\\",\\\"username\\\":\\\"cva\\\"}]\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var request = request(draft.getId(), "CV B; CV C", "cvb;cvc;cvx",
            "[{\"id\":\"2\",\"name\":\"CV B\",\"username\":\"cvb\"},{\"id\":\"1\",\"name\":\"CV A\",\"username\":\"cva\"}]");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(fields).containsEntry("user_coordinator", "cvx;cvb;cvc");
        assertThat(fields).containsEntry("user_coordinated", "cva");
        assertThat(fields).containsEntry("coordinator", "CV A; CV B; CV C");
        assertThat(String.valueOf(fields.get("coordinator_obj"))).contains("\"id\":\"2\"");
        assertThat(String.valueOf(fields.get("coordinator_obj")).split("\"id\":\"1\"")).hasSize(2); // không trùng

        ArgumentCaptor<AddDynamicDocumentRequest> processCaptor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDraftProcessDocumentUseCase).execute(processCaptor.capture());
        assertThat(processCaptor.getValue().getFields())
            .containsEntry("action_type", "chuyenphoihop")
            .containsEntry("opinion", "Chuyển phối hợp")
            .containsEntry("receiver", "CV B; CV C")
            .containsEntry("parentid", draft.getId().toString());
    }

    @Test
    void execute_actorNotInWaitingList_shouldFailWithoutSaving() {
        DuThaoDocument draft = newDraft("{\"user_coordinator\":\"cvx\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var result = useCase.execute(request(draft.getId(), "CV B", "cvb", "[]"));

        assertThat(result.isSuccess()).isFalse();
        verify(duThaoDocumentRepository, never()).save(any());
    }

    @Test
    void execute_withVerifiedDelegation_shouldRecordDelegatorInCoordinatedMovement() {
        var delegation = new UserDelegation(UUID.randomUUID(), "truongphonga", UUID.randomUUID(), "phophongb",
            "Pho phong B", "Phó Trưởng phòng", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "duthao", "");
        ChuyenPhoiHopDuThaoUseCase delegationUseCase = newUseCase(new ArrayList<>(List.of(delegation)));
        DuThaoDocument draft = newDraft("{\"user_coordinator\":\"cva\"}");
        when(duThaoDocumentRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var result = delegationUseCase.execute(request(draft.getId(), "Truong phong A", "phophongb",
            "[{\"id\":\"9\",\"name\":\"Truong phong A\",\"username\":\"phophongb\",\"delegated_from_username\":\"truongphonga\"}]"));

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(draft.getDynamicFieldsJson());
        assertThat(fields).containsEntry("user_coordinator", "phophongb");
        assertThat(fields).containsEntry("user_coordinated_movement", "truongphonga");
    }

    @Test
    void execute_withoutReceiver_shouldFail() {
        var result = useCase.execute(request(UUID.randomUUID(), "", " ", "[]"));

        assertThat(result.isSuccess()).isFalse();
    }

    private ChuyenPhoiHopDuThaoRequest request(UUID id, String names, String usernames, String obj) {
        var request = new ChuyenPhoiHopDuThaoRequest();
        request.setId(id);
        request.setCoordinator(names);
        request.setUserCoordinator(usernames);
        request.setCoordinatorObj(obj);
        return request;
    }

    private DuThaoDocument newDraft(String dynamicFieldsJson) {
        DuThaoDocument document = new DuThaoDocument("Du thao", "duthao", "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
