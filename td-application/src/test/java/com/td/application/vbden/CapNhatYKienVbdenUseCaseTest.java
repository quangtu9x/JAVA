package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.DocumentRepository;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.domain.documents.BusinessDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 14/09/2026 ("modal Cập nhật kết quả xử lý văn bản -> Đồng ý chưa cutoverFE (gọi
 * qlvb/vbden/CapNhatVanBanDen cũ)") — xem javadoc {@link CapNhatYKienVbdenUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CapNhatYKienVbdenUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AddVbdenResDocumentUseCase addVbdenResDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;

    private CapNhatYKienVbdenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatYKienVbdenUseCase(
            documentRepository, addVbdenResDocumentUseCase, currentUserProvider, resolveIdentifierByLevelUseCase);
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
    }

    @Test
    void execute_withNullRequest_shouldReturnFailure() {
        assertThat(useCase.execute(null).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutParentid_shouldReturnFailure() {
        assertThat(useCase.execute(new CapNhatYKienVbdenRequest()).isSuccess()).isFalse();
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new CapNhatYKienVbdenRequest();
        request.setParentid(parentId);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
        verify(addVbdenResDocumentUseCase, never()).execute(any());
    }

    @Test
    void execute_withDeletedDocument_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden");
        parent.markAsDeleted(null);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new CapNhatYKienVbdenRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new CapNhatYKienVbdenRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new CapNhatYKienVbdenRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    // Domino capnhatykien(): switch-case + than ham deu bi comment out - CHI tao ban ghi audit, KHONG
    // dong tho document repository.save() nao ca (khong mutate parent).
    @Test
    void execute_shouldOnlyCreateProcessingRecordWithoutMutatingParent() {
        BusinessDocument parent = newDocument("vbden");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.01"));

        var request = new CapNhatYKienVbdenRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Dong y voi noi dung nay");
        request.setCreatorName("Nguyen Van A");
        request.setPosition("Chuyen vien");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "capnhatykien");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "nguyenvana");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Dong y voi noi dung nay");
        assertThat(childRequest.getFields()).containsEntry("creator", "Nguyen Van A");
        assertThat(childRequest.getFields()).containsEntry("position", "Chuyen vien");
        assertThat(childRequest.getFields()).containsEntry("identifier", "G10.01");
    }

    @Test
    void execute_withoutCreatorName_shouldFallBackToActingUsername() {
        BusinessDocument parent = newDocument("vbden");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new CapNhatYKienVbdenRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("creator", "nguyenvana");
    }

    @Test
    void execute_withResObj_shouldPersistResObjOnProcessingRecord() {
        BusinessDocument parent = newDocument("vbden");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var ref = new VbdenReplyDocumentRef();
        ref.setId(UUID.randomUUID());
        ref.setCode("VBDI-001");
        ref.setPromulgationDate("01/01/2100");

        var request = new CapNhatYKienVbdenRequest();
        request.setParentid(parent.getId());
        request.setResObj(List.of(ref));

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields().get("res_obj")).contains("VBDI-001");
    }

    @Test
    void execute_withoutResObj_shouldNotIncludeResObjField() {
        BusinessDocument parent = newDocument("vbden");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new CapNhatYKienVbdenRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("res_obj");
    }

    private BusinessDocument newDocument(String documentType) {
        return new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
    }
}
