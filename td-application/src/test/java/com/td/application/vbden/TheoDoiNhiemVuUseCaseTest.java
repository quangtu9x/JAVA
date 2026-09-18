package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N20 (07/09/2026 — "Văn bản cần theo dõi") — xem javadoc {@link TheoDoiNhiemVuUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class TheoDoiNhiemVuUseCaseTest {

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
    private AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TheoDoiNhiemVuUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TheoDoiNhiemVuUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, appUserRepository);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parentId);
        request.setAction("theodoi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("theodoi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("theodoi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutAppUserProfile_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("chuyenvien01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("chuyenvien01")).thenReturn(Optional.empty());

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("theodoi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_theodoi_shouldAddIdentifierToTasks() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01"))
            .thenReturn(Optional.of(newAppUser("H05.01")));

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("theodoi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("tasks")).isEqualTo(List.of("H05.01"));
        verify(documentRepository).save(any());
    }

    @Test
    void execute_theodoi_withAlreadyTracked_shouldNotDuplicateOrSave() {
        BusinessDocument parent = newDocument("vbden", "{\"tasks\":[\"H05.01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01"))
            .thenReturn(Optional.of(newAppUser("H05.01")));

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("theodoi");

        useCase.execute(request);

        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_huytheodoi_shouldRemoveIdentifierFromTasks() {
        BusinessDocument parent = newDocument("vbden", "{\"tasks\":[\"H05.01\",\"G13\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01"))
            .thenReturn(Optional.of(newAppUser("H05.01")));

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("huytheodoi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("tasks")).isEqualTo(List.of("G13"));
    }

    @Test
    void execute_huytheodoi_withNotTracked_shouldNotSave() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01"))
            .thenReturn(Optional.of(newAppUser("H05.01")));

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("huytheodoi");

        useCase.execute(request);

        verify(documentRepository, never()).save(any());
    }

    // Domino: chuyentheodoi() la ham RONG - port GIU NGUYEN no-op, khong mutate parent.
    @Test
    void execute_chuyentheodoi_shouldNotMutateParent() {
        BusinessDocument parent = newDocument("vbden", "{\"tasks\":[\"H05.01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01"))
            .thenReturn(Optional.of(newAppUser("H05.01")));

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("chuyentheodoi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());
        verify(addVbdenResDocumentUseCase).execute(any());
    }

    // Domino: identifier rong (chuyen vien thuong) -> addToFieldListStringHideVBden/
    // removeToFieldListStringHideVBden early-return, KHONG mutate gi, van thanh cong.
    @Test
    void execute_withBlankIdentifier_shouldNotMutateParentButStillSucceed() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("chuyenvien01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("chuyenvien01"))
            .thenReturn(Optional.of(newAppUser("")));

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("theodoi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_shouldAlwaysCreateProcessingRecordWithActionAndActingUsername() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01"))
            .thenReturn(Optional.of(newAppUser("H05.01")));

        var request = new TheoDoiNhiemVuRequest();
        request.setParentid(parent.getId());
        request.setAction("theodoi");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "theodoi");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
    }

    private AppUser newAppUser(String identifier) {
        return new AppUser(
            "sub-test-user", "test-user", "test-user", null, null, null, true,
            null, 0, null, null, null, null, null, null, identifier, false, null);
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
