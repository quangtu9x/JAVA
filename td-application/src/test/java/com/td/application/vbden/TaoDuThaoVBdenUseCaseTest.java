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
 * N19 (04/09/2026 — "Tạo mới văn bản dự thảo") — xem javadoc {@link TaoDuThaoVBdenUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class TaoDuThaoVBdenUseCaseTest {

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
    private TaoDuThaoVBdenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TaoDuThaoVBdenUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, resolveIdentifierByLevelUseCase,
            authorAclFieldsService);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parentId);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedDocument_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        parent.markAsDeleted(null);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    // Domino taoduthao_hoso_phieutrinh(): user_process RONG -> KHONG mutate gi ca (khong luu, khong
    // doi user_processing) - nhung van tao ban ghi vbden_res (preamble CreateOrUpdateDocument chay
    // TRUOC dieu kien nay, khong bi anh huong).
    @Test
    void execute_withEmptyUserProcess_shouldNotMutateParentButStillCreateProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());
        verify(addVbdenResDocumentUseCase).execute(any());
    }

    // Domino taoduthao_hoso_phieutrinh() dong 309-332: chuyen actingUsername tu user_process/
    // user_returned sang user_processing.
    @Test
    void execute_withActingUsernameInUserProcess_shouldMoveToUserProcessing() {
        BusinessDocument parent = newDocument("vbden", "{"
            + "\"user_process\":[\"vanthu01\",\"nguyenvanb\"],"
            + "\"user_returned\":[\"vanthu01\"]"
            + "}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("nguyenvanb"));
        assertThat(updated.get("user_returned")).isEqualTo(List.of());
        assertThat(updated.get("user_processing")).isEqualTo(List.of("vanthu01"));
    }

    // Domino: user_process khong rong nhung KHONG chua actingUsername -> van mutate (dieu kien la
    // "field co gia tri hay khong", khong phai "actingUsername co trong do hay khong") - remove la
    // no-op, van them vao user_processing.
    @Test
    void execute_withNonEmptyUserProcessNotContainingActingUsername_shouldStillAddToUserProcessing() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvanb\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("nguyenvanb"));
        assertThat(updated.get("user_processing")).isEqualTo(List.of("vanthu01"));
    }

    @Test
    void execute_withActingUsernameAlreadyInUserProcessing_shouldNotDuplicate() {
        BusinessDocument parent = newDocument("vbden", "{"
            + "\"user_process\":[\"vanthu01\"],"
            + "\"user_processing\":[\"vanthu01\"]"
            + "}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_processing")).isEqualTo(List.of("vanthu01"));
    }

    @Test
    void execute_shouldCreateProcessingRecordWithActionOpinionCreatorAndPosition() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"vanthu01\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.01"));

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Soan du thao tra loi");
        request.setCreatorName("Nguyen Van Thu");
        request.setPosition("Van thu");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "taoduthao");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Soan du thao tra loi");
        assertThat(childRequest.getFields()).containsEntry("creator", "Nguyen Van Thu");
        assertThat(childRequest.getFields()).containsEntry("position", "Van thu");
        assertThat(childRequest.getFields()).containsEntry("identifier", "G10.01");
    }

    @Test
    void execute_withoutCreatorName_shouldFallBackToActingUsername() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("creator", "vanthu01");
    }

    @Test
    void execute_withResObj_shouldEmbedResObjJsonOnProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        UUID draftId = UUID.randomUUID();

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());
        request.setResObjId(draftId);
        request.setResObjDbpath("qlvb_duthao.nsf");
        request.setResObjCode("Du thao 01");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        String resObj = captor.getValue().getFields().get("res_obj");
        assertThat(resObj).contains(draftId.toString()).contains("qlvb_duthao.nsf").contains("Du thao 01");
    }

    @Test
    void execute_withoutResObj_shouldNotIncludeResObjField() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new TaoDuThaoVBdenRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("res_obj");
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
