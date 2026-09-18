package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AuthorAclFieldsService;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.sharedcore.UserOrganizationProfileDto;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N15 (03/09/2026 — "Cập nhật kết quả xử lý") — xem javadoc {@link CapNhatKetQuaXuLyUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CapNhatKetQuaXuLyUseCaseTest {

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
    private AppUserRepository appUserRepository;
    @Mock
    private GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    @Mock
    private AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private final VbdenStatusService vbdenStatusService = new VbdenStatusService();
    private CapNhatKetQuaXuLyUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CapNhatKetQuaXuLyUseCase(
            documentRepository, addVbdenResDocumentUseCase, dynamicDocumentFieldsService,
            currentUserProvider, documentLogService, documentCacheService, vbdenStatusService,
            resolveIdentifierByLevelUseCase, appUserRepository, getUserOrganizationProfileUseCase,
            authorAclFieldsService);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbdenResDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
    }

    @Test
    void execute_withUnknownParent_shouldReturnFailure() {
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parentId);

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withWrongDocumentType_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden_res", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutCurrentUser_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withoutActingAppUserProfile_shouldReturnFailure() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.empty());

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        assertThat(useCase.execute(request).isSuccess()).isFalse();
    }

    @Test
    void execute_withCandidateInSameDonVi_shouldMoveFromUserProcessToUserProcessed() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvanb\"],\"user_processing\":[]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 3);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "3");
        stubProfile(candidate, "G10.01.02;G10.01;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        // level="3" cua van thu kich hoat nhanh force-clear (xem test rieng ben duoi) - o day chi xac
        // nhan buoc 1 (di chuyen ung vien) da chay dung TRUOC khi force-clear xoa sach user_process.
        assertThat(updated.get("user_processed")).isEqualTo(List.of("nguyenvanb"));
    }

    @Test
    void execute_withCandidateInDifferentDonVi_shouldNotMove() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvanb\"],\"user_processing\":[]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "5");
        stubProfile(candidate, "G20.02;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("nguyenvanb"));
        assertThat(updated.get("user_processed")).isEqualTo(List.of());
    }

    @Test
    void execute_withIdentifierCreatorMatchAndNotInternal_shouldMoveCandidate() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvanb\"],\"identifier_creator\":\"G10.01\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "5");
        // Khac han don vi (identifiers khong chua "G10.01;") - CHI khop nho nhanh identifier_creator.
        stubProfile(candidate, "G20.02;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_processed")).isEqualTo(List.of("nguyenvanb"));
    }

    @Test
    void execute_withIdentifierCreatorMatchButInternalDocument_shouldNotMoveCandidate() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvanb\"],\"identifier_creator\":\"G10.01\",\"internal\":\"1\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "5");
        stubProfile(candidate, "G20.02;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of("nguyenvanb"));
    }

    @Test
    void execute_withActingUserLevel3AndNotInternal_shouldForceClearAndOverrideStatus() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvanb\"],\"deadline_date\":\"01/01/2099\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 3);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "3");
        stubProfile(candidate, "G20.02;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of());
        assertThat(updated.get("user_processing")).isEqualTo(List.of());
        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY);
        // Bugfix 14/09/2026 (bao loi nguoi dung: van ban bien mat khoi MOI danh sach - tai khoan
        // demo2, van ban aea34349-...): "nguyenvanb" KHONG cung don vi voi van thu (G20.02 != G10.01)
        // nen vong lap tren KHONG dua vao user_processed - truoc day forceClear xoa sach user_process
        // ma KHONG bu lai, lam "nguyenvanb" bien mat khoi CA 6 field/moi view ca nhan. Gio phai con
        // trong user_processed du khac don vi voi van thu.
        assertThat(updated.get("user_processed")).isEqualTo(List.of("nguyenvanb"));
    }

    @Test
    void execute_withActingUserLevel5_shouldNotForceClear() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvanb\"],\"deadline_date\":\"01/01/2099\"}", "2");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "5");
        stubProfile(candidate, "G20.02;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        // Khong khop don vi (khac G10.01) nen KHONG bi loai khoi user_process - va khong force-clear.
        assertThat(updated.get("user_process")).isEqualTo(List.of("nguyenvanb"));
    }

    // Bugfix 04/09/2026 (cung phat hien voi KetThucXuLyUseCase, bao loi nguoi dung) - Domino
    // tinhSoNgayConLai() that: BAT KY LOI NAO (thieu deadline_date) -> tra ve 0, KHONG bo qua buoc
    // ghi status. Truoc day recomputeStatus() SAI: bo qua hoan toan khi thieu deadline_date.
    @Test
    void execute_withoutDeadlineDateAndCandidateMoved_shouldRecomputeStatusToDungHanDaXuLy() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvanb\"]}", "2");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "5");
        stubProfile(candidate, "G10.01.02;G10.01;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_processed")).isEqualTo(List.of("nguyenvanb"));
        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY);
    }

    // Bo sung 14/09/2026 (yeu cau nguoi dung "lam luon chuc nang Gan van ban tra loi o modal nay
    // luon") - resObj luu tren ban ghi vbden_res + promulgationDate cua PHAN TU CUOI lam moc thay
    // @Today khi tinh remainingDays (dung Domino processResObj() that). Dung level=5 (khong force-
    // clear) de test rieng recomputeStatus(), tranh lan sang nhanh forceStatusByDeadline.
    @Test
    void execute_withResObj_shouldPersistOnProcessingRecordAndUseLastPromulgationDateAsReference() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvanb\"],\"user_processing\":[],\"deadline_date\":\"10/01/2100\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "5");
        stubProfile(candidate, "G10.01.02;G10.01;", "5");

        var ref1 = new VbdenReplyDocumentRef();
        ref1.setCode("VBDI-001");
        ref1.setPromulgationDate("01/01/2050");
        var ref2 = new VbdenReplyDocumentRef();
        ref2.setCode("VBDI-002");
        // Sau deadline (10/01/2100) - phan tu CUOI nay phai la moc duoc dung, khong phai ref1.
        ref2.setPromulgationDate("15/01/2100");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());
        request.setResObj(List.of(ref1, ref2));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_QUA_HAN_DA_XU_LY);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        String resObjJson = captor.getValue().getFields().get("res_obj");
        assertThat(resObjJson).contains("VBDI-001").contains("VBDI-002");
    }

    @Test
    void execute_withoutResObj_shouldNotIncludeResObjFieldOnProcessingRecord() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        assertThat(captor.getValue().getFields()).doesNotContainKey("res_obj");
    }

    // Bugfix 04/09/2026 - nhanh forceStatusByDeadline (level 1/3, khong noi bo) cung phai mac dinh
    // remainingDays=0 khi thieu deadline_date, khong bo qua (Domino dong 279 goi tinhSoNgayConLai truc
    // tiep, bat loi -> 0, van tiep tuc ghi status "4").
    @Test
    void execute_withActingUserLevel3AndNotInternalAndWithoutDeadlineDate_shouldForceClearAndSetStatusDungHan() {
        BusinessDocument parent = newDocument("vbden", "{\"user_process\":[\"nguyenvanb\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 3);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "3");
        stubProfile(candidate, "G20.02;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_process")).isEqualTo(List.of());
        assertThat(updated.get("user_processing")).isEqualTo(List.of());
        assertThat(parent.getStatus()).isEqualTo(VbdenStatusService.STATUS_DUNG_HAN_DA_XU_LY);
        assertThat(updated.get("user_processed")).isEqualTo(List.of("nguyenvanb"));
    }

    // Bugfix 14/09/2026: candidate DA CO SAN trong user_processed (vd. tu 1 lan chay khac / dong bo
    // du lieu) khong bi nhan doi - forceClear phai idempotent, dung tien le buoc 1 (dong 166-168).
    @Test
    void execute_withCandidateAlreadyInUserProcessedAndForceClear_shouldNotDuplicate() {
        BusinessDocument parent = newDocument("vbden",
            "{\"user_process\":[\"nguyenvanb\"],\"user_processed\":[\"nguyenvanb\"]}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 1);
        AppUser candidate = newAppUser("nguyenvanb", null, null);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvanb")).thenReturn(Optional.of(candidate));
        stubProfile(vanThu, "G10.01;", "1");
        stubProfile(candidate, "G20.02;", "5");

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());

        useCase.execute(request);

        Map<String, Object> updated = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        assertThat(updated.get("user_processed")).isEqualTo(List.of("nguyenvanb"));
    }

    @Test
    void execute_shouldCreateProcessingRecordWithActionOpinionAndIdentifier() {
        BusinessDocument parent = newDocument("vbden", "{}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        AppUser vanThu = newAppUser("vanthu01", "G10.01", 5);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(vanThu));
        stubProfile(vanThu, "G10.01;", "5");
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("G10.01"));

        var request = new CapNhatKetQuaXuLyRequest();
        request.setParentid(parent.getId());
        request.setOpinion("Da xu ly xong");
        request.setCreatorName("Van Thu Cuc");
        request.setPosition("Van thu");

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbdenResDocumentUseCase).execute(captor.capture());
        var childRequest = captor.getValue();
        assertThat(childRequest.getDocumentType()).isEqualTo("vbden_res");
        assertThat(childRequest.getFields()).containsEntry("parentid", parent.getId().toString());
        assertThat(childRequest.getFields()).containsEntry("action", "vanthuketthucxuly");
        assertThat(childRequest.getFields()).containsEntry("nguoi_thuc_hien", "vanthu01");
        assertThat(childRequest.getFields()).containsEntry("opinion", "Da xu ly xong");
        assertThat(childRequest.getFields()).containsEntry("creator", "Van Thu Cuc");
        assertThat(childRequest.getFields()).containsEntry("position", "Van thu");
        assertThat(childRequest.getFields()).containsEntry("identifier", "G10.01");
    }

    private void stubProfile(AppUser user, String identifiers, String level) {
        var dto = new UserOrganizationProfileDto();
        dto.setIdentifiers(identifiers);
        dto.setLevel(level);
        lenient().when(getUserOrganizationProfileUseCase.buildProfileForUser(user)).thenReturn(dto);
    }

    private AppUser newAppUser(String username, String identifier, Integer level) {
        return new AppUser(
            "sub-" + username, username, username, null, null, null, true,
            level, 0, null, null, null, null, null, null, identifier, false, null);
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
