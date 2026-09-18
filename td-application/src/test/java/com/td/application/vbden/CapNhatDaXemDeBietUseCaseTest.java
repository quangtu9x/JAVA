package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
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
 * 15/09/2026 ("Đánh dấu đã xem để biết") — xem javadoc {@link CapNhatDaXemDeBietUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CapNhatDaXemDeBietUseCaseTest {

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
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private CapNhatDaXemDeBietUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatDaXemDeBietUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, authorAclFieldsService);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new CapNhatDaXemDeBietRequest();
        request.setParentid(parentId);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new CapNhatDaXemDeBietRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedDocument_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        parent.markAsDeleted(null);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new CapNhatDaXemDeBietRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new CapNhatDaXemDeBietRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    // Domino daxemdebiet(): user_to_read KHONG rong -> loai actingUsername, them vao user_to_readed -
    // KHONG dung field nao khac (khac CapNhatKetQuaXuLy/KetThucXuLy).
    @Test
    void execute_withUserToReadNotEmpty_shouldMoveActingUsernameToReaded() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_to_read\":[\"xembiet01\",\"xembiet02\"],\"user_process\":[\"chutri01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xembiet01"));

        var request = new CapNhatDaXemDeBietRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_to_read")).isEqualTo(List.of("xembiet02"));
        assertThat(updated.get("user_to_readed")).isEqualTo(List.of("xembiet01"));
        assertThat(updated.get("user_process")).isEqualTo(List.of("chutri01"));
        verify(documentRepository).save(any());
    }

    @Test
    void execute_withUserToReadAlreadyReaded_shouldNotDuplicate() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_to_read\":[\"xembiet01\"],\"user_to_readed\":[\"xembiet01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xembiet01"));

        var request = new CapNhatDaXemDeBietRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_to_readed")).isEqualTo(List.of("xembiet01"));
    }

    // Domino: khoan dung ca dang scalar ";"-noi (D.14) - KHONG chi hieu List<String>.
    @Test
    void execute_withUserToReadAsScalarString_shouldStillWork() {
        BusinessDocument parent = newDocument("vbden", "{\"user_to_read\":\";xembiet01;xembiet02;\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xembiet01"));

        var request = new CapNhatDaXemDeBietRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_to_read")).isEqualTo(List.of("xembiet02"));
        assertThat(updated.get("user_to_readed")).isEqualTo(List.of("xembiet01"));
    }

    // Domino: user_to_read RONG -> khong dung gi ca (khong save van ban cha) - nhung van thanh cong vi
    // ban ghi qua trinh xu ly van duoc tao (xem javadoc lop).
    @Test
    void execute_withEmptyUserToRead_shouldNotMutateParentButStillSucceed() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xembiet01"));

        var request = new CapNhatDaXemDeBietRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());
        verify(addVbdenResDocumentUseCase).execute(any());
    }

    @Test
    void execute_shouldAlwaysCreateProcessingRecordWithActionAndActingUsername() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xembiet01"));

        var request = new CapNhatDaXemDeBietRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "capnhatdaxemdebiet");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "xembiet01");
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
