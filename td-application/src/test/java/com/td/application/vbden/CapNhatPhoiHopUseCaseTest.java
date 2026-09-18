package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
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
 * N20 (07/09/2026 — "Cập nhật kết quả phối hợp xử lý") — xem javadoc {@link CapNhatPhoiHopUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CapNhatPhoiHopUseCaseTest {

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
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private CapNhatPhoiHopUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatPhoiHopUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService,
            resolveIdentifierByLevelUseCase, authorAclFieldsService);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parentId);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedDocument_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        parent.markAsDeleted(null);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    // Domino capnhatphoihop(): user_coordinate KHONG rong -> loai actingUsername, them vao
    // user_coordinated - KHONG dung user_process/user_processing/status (khac KetThucXuLy).
    @Test
    void execute_withUserCoordinateNotEmpty_shouldMoveActingUsernameToCoordinated() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_coordinate\":[\"phoihop01\",\"phoihop02\"],\"user_process\":[\"chutri01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("phoihop01"));

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_coordinate")).isEqualTo(List.of("phoihop02"));
        assertThat(updated.get("user_coordinated")).isEqualTo(List.of("phoihop01"));
        // KHONG dung status/user_process - phai giu nguyen y het truoc do.
        assertThat(updated.get("user_process")).isEqualTo(List.of("chutri01"));
        verify(documentRepository).save(any());
    }

    @Test
    void execute_withUserCoordinateAlreadyCoordinated_shouldNotDuplicate() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_coordinate\":[\"phoihop01\"],\"user_coordinated\":[\"phoihop01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("phoihop01"));

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_coordinated")).isEqualTo(List.of("phoihop01"));
    }

    // Domino: khoan dung ca dang scalar ";"-noi (D.14) - KHONG chi hieu List<String>.
    @Test
    void execute_withUserCoordinateAsScalarString_shouldStillWork() {
        BusinessDocument parent = newDocument("vbden", "{\"user_coordinate\":\";phoihop01;phoihop02;\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("phoihop01"));

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_coordinate")).isEqualTo(List.of("phoihop02"));
        assertThat(updated.get("user_coordinated")).isEqualTo(List.of("phoihop01"));
    }

    // Domino: user_coordinate RONG -> khong dung gi ca (khong save van ban cha) - nhung van thanh
    // cong vi ban ghi qua trinh xu ly van duoc tao (xem javadoc lop).
    @Test
    void execute_withEmptyUserCoordinate_shouldNotMutateParentButStillSucceed() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("phoihop01"));

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());
        verify(addVbdenResDocumentUseCase).execute(any());
    }

    @Test
    void execute_shouldAlwaysCreateProcessingRecordWithActionOpinionCreatorAndPosition() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("phoihop01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.01"));

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Da phoi hop xong");
        request.setCreatorName("Nguyen Van A");
        request.setPosition("Chuyen vien");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "capnhatphoihop");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "phoihop01");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Da phoi hop xong");
        assertThat(childRequest.getFields()).containsEntry("creator", "Nguyen Van A");
        assertThat(childRequest.getFields()).containsEntry("position", "Chuyen vien");
        assertThat(childRequest.getFields()).containsEntry("identifier", "G10.01");
    }

    @Test
    void execute_withoutCreatorName_shouldFallBackToActingUsername() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("phoihop01"));

        var request = new CapNhatPhoiHopRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("creator", "phoihop01");
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
