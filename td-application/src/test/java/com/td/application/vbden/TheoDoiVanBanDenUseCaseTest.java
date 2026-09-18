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
 * 16/09/2026 (bugfix "route vbden/xuly/theodoi -> Bỏ theo dõi -> port sang JAVA và cutoverFE" bị làm
 * NHẦM sang {@link TheoDoiNhiemVuUseCase} ở lần port trước) — xem javadoc
 * {@link TheoDoiVanBanDenUseCase} về sự khác biệt {@code user_track}/{@code user_tracked} (use case
 * này) vs {@code tasks} ({@link TheoDoiNhiemVuUseCase}).
 */
@ExtendWith(MockitoExtension.class)
class TheoDoiVanBanDenUseCaseTest {

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
    private TheoDoiVanBanDenUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TheoDoiVanBanDenUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, authorAclFieldsService);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new TheoDoiVanBanDenRequest();
        request.setParentid(parentId);
        request.setAction("huytheodoi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new TheoDoiVanBanDenRequest();
        request.setParentid(parent.getId());
        request.setAction("huytheodoi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new TheoDoiVanBanDenRequest();
        request.setParentid(parent.getId());
        request.setAction("huytheodoi");

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    // Domino huytheodoi(): user_track KHONG rong -> loai actingUsername khoi user_track, them vao
    // user_tracked. Day la field/khoa MA view "theodoi"/"datheodoi" that su loc theo.
    @Test
    void execute_huyTheoDoi_withUserTrackNotEmpty_shouldMoveActingUsernameToTracked() {
        BusinessDocument parent = newDocument("vbden", "{\"user_track\":[\"lpha\",\"other\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));

        var request = new TheoDoiVanBanDenRequest();
        request.setParentid(parent.getId());
        request.setAction("huytheodoi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_track")).isEqualTo(List.of("other"));
        assertThat(updated.get("user_tracked")).isEqualTo(List.of("lpha"));
        verify(documentRepository).save(any());
    }

    // Domino: user_track RONG -> khong dung gi (khong save van ban cha) - nhung van thanh cong vi ban
    // ghi qua trinh xu ly van duoc tao.
    @Test
    void execute_huyTheoDoi_withEmptyUserTrack_shouldNotMutateParentButStillSucceed() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));

        var request = new TheoDoiVanBanDenRequest();
        request.setParentid(parent.getId());
        request.setAction("huytheodoi");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());
        verify(addVbdenResDocumentUseCase).execute(any());
    }

    // Dinh chinh 16/09/2026 (khac Domino goc, CO CHU DICH - nguoi dung xac nhan qua AskUserQuestion):
    // theodoilai() them vao user_track VA loai khoi user_tracked - van ban khong con nam ca 2 tab.
    @Test
    void execute_theoDoiLai_withUserTrackedNotEmpty_shouldMoveActingUsernameBackToTrack() {
        BusinessDocument parent = newDocument("vbden", "{\"user_tracked\":[\"lpha\",\"other\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));

        var request = new TheoDoiVanBanDenRequest();
        request.setParentid(parent.getId());
        request.setAction("theodoilai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_track")).isEqualTo(List.of("lpha"));
        assertThat(updated.get("user_tracked")).isEqualTo(List.of("other"));
    }

    @Test
    void execute_theoDoiLai_withEmptyUserTracked_shouldNotMutateParentButStillSucceed() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));

        var request = new TheoDoiVanBanDenRequest();
        request.setParentid(parent.getId());
        request.setAction("theodoilai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_shouldAlwaysCreateProcessingRecordWithActionAndActingUsername() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("lpha"));

        var request = new TheoDoiVanBanDenRequest();
        request.setParentid(parent.getId());
        request.setAction("huytheodoi");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "huytheodoi");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "lpha");
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }
}
