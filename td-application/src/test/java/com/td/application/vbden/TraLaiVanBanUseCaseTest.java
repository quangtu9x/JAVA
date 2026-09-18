package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.processdiagram.ProcessDiagramService;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 14/09/2026 ("modal Trả lại văn bản -> chọn và đồng ý -> đang gọi api cũ
 * qlvb/vbden/TraLaiVanBan?databasetype=vbden -> port và cutoverFE") — xem javadoc
 * {@link TraLaiVanBanUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class TraLaiVanBanUseCaseTest {

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
    private ProcessDiagramService processDiagramService;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TraLaiVanBanUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TraLaiVanBanUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, resolveIdentifierByLevelUseCase,
            processDiagramService, authorAclFieldsService);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withMissingUserProcess_shouldReturnFailure() {
        var request = new TraLaiVanBanRequest();
        request.setParentid(UUID.randomUUID());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new TraLaiVanBanRequest();
        request.setParentid(parentId);
        request.setUserProcess("lpha");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new TraLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setUserProcess("lpha");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedDocument_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        parent.markAsDeleted(null);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new TraLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setUserProcess("lpha");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new TraLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setUserProcess("lpha");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    // Domino TraLaiVanBan(): loai actingUsername khoi user_process, them nguoi duoc chon vao
    // user_returned - KHONG dieu kien (khac TaoDuThaoVBdenUseCase).
    @Test
    void execute_shouldRemoveActingUsernameFromUserProcessAndAddSelectedUserToUserReturned() {
        BusinessDocument parent = newDocument("vbden", "{"
            + "\"user_process\":[\"demo2\",\"nguyenvanb\"],"
            + "\"user_returned\":[]"
            + "}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo2"));

        var request = new TraLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setUserProcess("lpha");
        request.setNameProcess("Le Phu Ha");
        request.setOpinion("Xin xem lai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("nguyenvanb"));
        assertThat(updated.get("user_returned")).isEqualTo(List.of("lpha"));
        verify(processDiagramService).removeNode(parent.getId(), "vbden", "demo2");
    }

    @Test
    void execute_userReturnedAlreadyContainsSelectedUser_shouldNotDuplicate() {
        BusinessDocument parent = newDocument("vbden", "{"
            + "\"user_process\":[\"demo2\"],"
            + "\"user_returned\":[\"lpha\"]"
            + "}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo2"));

        var request = new TraLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setUserProcess("lpha");

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_returned")).isEqualTo(List.of("lpha"));
    }

    @Test
    void execute_shouldCreateProcessingRecordWithActionOpinionAndSnapshot() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"demo2\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo2"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.01"));

        var request = new TraLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setUserProcess("lpha");
        request.setNameProcess("Le Phu Ha");
        request.setOpinion("Xin xem lai");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "tralai");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "demo2");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Xin xem lai");
        assertThat(childRequest.getFields()).containsEntry("identifier", "H05.01");
        String snapshot = childRequest.getFields().get("user_process_obj");
        assertThat(snapshot).contains("\"username\":\"lpha\"").contains("\"name\":\"Le Phu Ha\"");
    }

    @Test
    void execute_diagramRemovalThrows_shouldStillSucceed() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"demo2\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo2"));
        lenient().when(processDiagramService.removeNode(any(), any(), any()))
            .thenThrow(new RuntimeException("boom"));

        var request = new TraLaiVanBanRequest();
        request.setParentid(parent.getId());
        request.setUserProcess("lpha");

        assertThat(useCase.execute(request).isSuccess()).isTrue();
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
