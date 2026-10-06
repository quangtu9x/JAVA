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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N10 (01/09/2026 — "Xử lý lại") — xem javadoc {@link XuLyLaiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class XuLyLaiUseCaseTest {

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
    @Mock
    private com.td.application.sharedcore.UserFullNameResolver userFullNameResolver;
    @Mock
    private com.td.application.sharedcore.GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final VbdenStatusService vbdenStatusService = new VbdenStatusService();
    private XuLyLaiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new XuLyLaiUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, vbdenStatusService,
            resolveIdentifierByLevelUseCase, authorAclFieldsService, userFullNameResolver,
            getUserOrganizationProfileUseCase);
        var profile = new com.td.application.sharedcore.UserOrganizationProfileDto();
        profile.setPosition("Chuyên viên");
        lenient().when(getUserOrganizationProfileUseCase.execute(any())).thenReturn(profile);
        lenient().when(userFullNameResolver.resolveForWrite(any(), any()))
            .thenAnswer(invocation -> "Tên " + invocation.getArgument(0));
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new XuLyLaiRequest();
        request.setParentid(parentId);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withDeletedDocument_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        parent.markAsDeleted(null);
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldMoveUsernameFromUserProcessedToUserProcess() {
        BusinessDocument parent = newDocument("vbden", "{\"user_processed\":[\"vanthu01\",\"nguyenvanb\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Ghi sai y kien, can xu ly lai");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updatedFields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updatedFields.get("user_processed")).isEqualTo(List.of("nguyenvanb"));
        assertThat(updatedFields.get("user_process")).isEqualTo(List.of("vanthu01"));
    }

    @Test
    void execute_withOverdueDeadline_shouldRecomputeStatusToQuaHan() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"01/01/2020\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DANG_XU_LY);
    }

    @Test
    void execute_withFutureDeadline_shouldRecomputeStatusToConHan() {
        BusinessDocument parent = newDocument("vbden", "{\"deadline_date\":\"01/01/2099\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
    }

    // Bugfix 04/09/2026 (cung phat hien voi KetThucXuLyUseCase, bao loi nguoi dung) - Domino
    // tinhSoNgayConLai() that: BAT KY LOI NAO (thieu deadline_date) -> tra ve 0, KHONG bo qua buoc
    // ghi status (xem javadoc XuLyLaiUseCase#recomputeStatus). Truoc day use case nay SAI: bo qua
    // hoan toan viec tinh status khi thieu deadline_date, giu nguyen status cu.
    @Test
    void execute_withoutDeadlineDate_shouldRecomputeStatusToConHanDangXuLy() {
        BusinessDocument parent = newDocument("vbden", "{}", "3");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_CON_HAN_DANG_XU_LY);
    }

    @Test
    void execute_shouldCreateProcessingRecordWithActionXuLyLaiAndOpinion() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Ghi sai y kien, can xu ly lai");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        AddDynamicDocumentRequest childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "xulylai");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Ghi sai y kien, can xu ly lai");
        // 05/10/2026: tên đầy đủ + chức danh (từ hồ sơ khi FE không gửi).
        assertThat(childRequest.getFields()).containsEntry("creator", "Tên vanthu01");
        assertThat(childRequest.getFields()).containsEntry("position", "Chuyên viên");
    }

    @Test
    void execute_withPositionFromClient_shouldPreferClientPosition() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());
        request.setPosition("Trưởng phòng");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("position", "Trưởng phòng");
    }

    // Bo sung 03/09/2026 (cung gap voi ChuyenXuLyVbdenUseCase - xem javadoc lop do).
    @Test
    void execute_withResolvableIdentifier_shouldWriteIdentifierOntoChildProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.08"));

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).containsEntry("identifier", "G10.08");
    }

    @Test
    void execute_withUnresolvableIdentifier_shouldNotWriteIdentifierField() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        var request = new XuLyLaiRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("identifier");
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
