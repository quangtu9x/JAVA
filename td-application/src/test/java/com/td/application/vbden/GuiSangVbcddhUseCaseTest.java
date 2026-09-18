package com.td.application.vbden;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.AddDynamicDocumentRequest;
import com.td.application.documents.AddDynamicDocumentUseCase;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.application.vbcddh.AddVbcddhDynamicDocumentUseCase;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-211 (VBDen — Xử lý & Chuyển tiếp) — DoD (4): chuyển văn bản đến thành văn bản chỉ đạo điều
 * hành qua {@link GuiSangVbcddhUseCase}. Sửa 17/09/2026 (đóng gap "chưa replicate TaoNhiemVu()") —
 * xem javadoc lớp đó.
 */
@ExtendWith(MockitoExtension.class)
class GuiSangVbcddhUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AddDynamicDocumentUseCase addDynamicDocumentUseCase;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private AddVbcddhDynamicDocumentUseCase addVbcddhDynamicDocumentUseCase;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private GuiSangVbcddhUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GuiSangVbcddhUseCase(
            documentRepository, addDynamicDocumentUseCase, dynamicDocumentFieldsService, documentLogService,
            documentCacheService, addVbcddhDynamicDocumentUseCase, currentUserProvider, appUserRepository,
            getUserOrganizationProfileUseCase);
        lenient().when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(addVbcddhDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
    }

    @Test
    void execute_withUnknownSource_shouldReturnFailure() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new GuiSangVbcddhRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNonVbdenSource_shouldReturnFailure() {
        BusinessDocument source = newDocument("vbdi", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));

        var request = new GuiSangVbcddhRequest();
        request.setId(source.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldCreateVbcddhDocumentWithCopiedFields() {
        BusinessDocument source = newDocument("vbden", "{\"unit_in_charge\":\"So Noi Vu\",\"code\":\"123/VBDEN\"}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbcddhId = UUID.randomUUID();
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbcddhId));

        var request = new GuiSangVbcddhRequest();
        request.setId(source.getId());
        request.setThoiHan("2026-08-10");
        request.setYKien("Can xu ly khan");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(vbcddhId);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addDynamicDocumentUseCase).execute(captor.capture());
        AddDynamicDocumentRequest captured = captor.getValue();
        assertThat(captured.getDocumentType()).isEqualTo("vbcddh");
        assertThat(captured.getFields()).containsEntry("idvbden", source.getId().toString());
        assertThat(captured.getFields()).containsEntry("unit_in_charge", "So Noi Vu");
        assertThat(captured.getFields()).containsEntry("code", "123/VBDEN");
        assertThat(captured.getFields()).containsEntry("hanxuly_date", "2026-08-10");
        assertThat(captured.getFields()).containsEntry("ykien", "Can xu ly khan");
        // Khong con luu tam list_chu_tri/list_phoi_hop len document CHA - da thay bang nhiem vu THAT.
        assertThat(captured.getFields()).doesNotContainKey("list_chu_tri");
        assertThat(captured.getFields()).doesNotContainKey("list_phoi_hop");
    }

    @Test
    void execute_shouldMarkSourceAsConvertedToVbcddh() {
        BusinessDocument source = newDocument("vbden", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UUID vbcddhId = UUID.randomUUID();
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbcddhId));

        var request = new GuiSangVbcddhRequest();
        request.setId(source.getId());

        useCase.execute(request);

        Map<String, Object> updatedSourceFields = dynamicDocumentFieldsService.parseDynamicFields(source.getDynamicFieldsJson());
        assertThat(updatedSourceFields).containsEntry("assignment_of_directive_document", "1");
        assertThat(updatedSourceFields).containsEntry("idvbcd", vbcddhId.toString());

        verify(documentLogService).logUpdate(any(), any(), any());
        verify(documentCacheService).evict(source.getId());
    }

    @Test
    void execute_withEmptyListChuTri_shouldNotCreateAnyNhiemVu() {
        BusinessDocument source = newDocument("vbden", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        var request = new GuiSangVbcddhRequest();
        request.setId(source.getId());

        useCase.execute(request);

        verify(addVbcddhDynamicDocumentUseCase, never()).execute(any());
    }

    // Domino TaoNhiemVu(): 1 document nhiemvu / 1 phan tu listChuTri, idvbden tro ve van ban den goc
    // - day la field khoa ma bugfix ViewQueryService#VIEW_VBCDDH_NV_BY_IDVANBAN (16/09/2026) doc.
    @Test
    void execute_withListChuTri_shouldCreateOneNhiemVuPerAssignment() {
        BusinessDocument source = newDocument("vbden",
            "{\"code\":\"123/VBDEN\",\"promulgation_date\":\"01/09/2026\",\"document_type\":\"Cong van\","
                + "\"unit_in_charge\":\"So Noi Vu\",\"sector\":\"Noi vu\"}");
        source.update("Van ban chi dao", null, null, null, null, null, null);
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        AppUser actingUser = newUser("vanthu01", "Nguyen Van Thu");
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(actingUser));
        UserOrganizationProfileDto profile = new UserOrganizationProfileDto();
        profile.setDepartmentName("Phong Hanh chinh");
        profile.setDepartmentId("dept-1");
        profile.setAgencyId("agency-1");
        when(getUserOrganizationProfileUseCase.buildProfileForUser(actingUser)).thenReturn(profile);

        var donVi = new NhiemVuCdDhAssigneeDto();
        donVi.setName("Phong A");
        donVi.setIdentifier("H05.01");
        var canNhan = new NhiemVuCdDhAssigneeDto();
        canNhan.setName("Tran Van B");
        canNhan.setUsername("tranvanb");

        var request = new GuiSangVbcddhRequest();
        request.setId(source.getId());
        request.setThoiHan("10/09/2026");
        request.setListChuTri(List.of(donVi, canNhan));

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbcddhDynamicDocumentUseCase, times(2)).execute(captor.capture());
        List<AddDynamicDocumentRequest> created = captor.getAllValues();

        var donViFields = created.get(0).getFields();
        assertThat(created.get(0).getDocumentType()).isEqualTo("vbcddh");
        assertThat(donViFields).containsEntry("form", "nhiemvu");
        assertThat(donViFields).containsEntry("tinhtrang", "Chưa thực hiện");
        assertThat(donViFields).containsEntry("idvbden", source.getId().toString());
        assertThat(donViFields).containsEntry("user_creator", "vanthu01");
        assertThat(donViFields).containsEntry("creator", "Nguyen Van Thu");
        assertThat(donViFields).containsEntry("nguoitheodoihide", "vanthu01");
        assertThat(donViFields).containsEntry("nguoiphutrachhide", "vanthu01");
        assertThat(donViFields).containsEntry("unit_creator", "Phong Hanh chinh");
        assertThat(donViFields).containsEntry("unit_creator_id", "dept-1");
        assertThat(donViFields).containsEntry("coquanid", "agency-1");
        assertThat(donViFields).containsEntry("sokyhieu", "123/VBDEN");
        assertThat(donViFields).containsEntry("ngayky_date", "01/09/2026");
        assertThat(donViFields).containsEntry("hanxuly_date", "10/09/2026");
        assertThat(donViFields).containsEntry("loaivanban", "Cong van");
        assertThat(donViFields).containsEntry("unit_in_charge", "So Noi Vu");
        assertThat(donViFields).containsEntry("linhvuc", "Noi vu");
        assertThat(donViFields).containsEntry("trichyeu", "Van ban chi dao");
        assertThat(donViFields).containsEntry("noidung", "Van ban chi dao");
        assertThat(donViFields).containsEntry("user_nguoichuyencddh", ";vanthu01;");
        assertThat(donViFields).containsEntry("donvithuchien", "Phong A");
        assertThat(donViFields).containsEntry("donvithuchienhide", "H05.01");
        assertThat(donViFields).doesNotContainKey("chuyenvienthuchien");

        var canNhanFields = created.get(1).getFields();
        assertThat(canNhanFields).containsEntry("chuyenvienthuchien", "Tran Van B");
        assertThat(canNhanFields).containsEntry("chuyenvienthuchienhide", "tranvanb");
        assertThat(canNhanFields).doesNotContainKey("donvithuchien");
    }

    // Domino dong 880-884: assignment giao DON VI nhung co "receiver"/"userReceiver" (dai dien cu
    // the cua don vi do nhan viec) -> GHI DE chuyenvienthuchien(hide) bang receiver/userReceiver.
    @Test
    void execute_withDonViAssignmentAndReceiver_shouldOverrideChuyenVienThucHien() {
        BusinessDocument source = newDocument("vbden", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var donViVoiDaiDien = new NhiemVuCdDhAssigneeDto();
        donViVoiDaiDien.setName("Phong A");
        donViVoiDaiDien.setIdentifier("H05.01");
        donViVoiDaiDien.setReceiver("Le Thi C");
        donViVoiDaiDien.setUserReceiver("lethic");

        var request = new GuiSangVbcddhRequest();
        request.setId(source.getId());
        request.setListChuTri(List.of(donViVoiDaiDien));

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbcddhDynamicDocumentUseCase).execute(captor.capture());
        Map<String, String> fields = captor.getValue().getFields();
        assertThat(fields).containsEntry("donvithuchien", "Phong A");
        assertThat(fields).containsEntry("donvithuchienhide", "H05.01");
        assertThat(fields).containsEntry("chuyenvienthuchien", "Le Thi C");
        assertThat(fields).containsEntry("chuyenvienthuchienhide", "lethic");
    }

    // Domino dong 694-787: listPhoiHop gom 1 LAN, ap dung GIONG HET len MOI nhiem vu tao ra.
    @Test
    void execute_withListPhoiHop_shouldApplySameAggregationToEveryNhiemVu() {
        BusinessDocument source = newDocument("vbden", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var chuTri1 = new NhiemVuCdDhAssigneeDto();
        chuTri1.setName("Phong A");
        chuTri1.setIdentifier("H05.01");
        var chuTri2 = new NhiemVuCdDhAssigneeDto();
        chuTri2.setName("Phong B");
        chuTri2.setIdentifier("H05.02");

        var phoiHopDonVi = new NhiemVuCdDhAssigneeDto();
        phoiHopDonVi.setName("Phong C");
        phoiHopDonVi.setIdentifier("H05.03");
        var phoiHopCaNhan = new NhiemVuCdDhAssigneeDto();
        phoiHopCaNhan.setName("Pham Van D");
        phoiHopCaNhan.setUsername("phamvand");

        var request = new GuiSangVbcddhRequest();
        request.setId(source.getId());
        request.setListChuTri(List.of(chuTri1, chuTri2));
        request.setListPhoiHop(List.of(phoiHopDonVi, phoiHopCaNhan));

        useCase.execute(request);

        ArgumentCaptor<AddDynamicDocumentRequest> captor = ArgumentCaptor.forClass(AddDynamicDocumentRequest.class);
        verify(addVbcddhDynamicDocumentUseCase, times(2)).execute(captor.capture());
        for (AddDynamicDocumentRequest created : captor.getAllValues()) {
            assertThat(created.getFields()).containsEntry("donviphoihop", "Phong C");
            assertThat(created.getFields()).containsEntry("donviphoihophide", "H05.03");
            assertThat(created.getFields()).containsEntry("chuyenvienphoihop", "Pham Van D");
            assertThat(created.getFields()).containsEntry("chuyenvienphoihophide", "phamvand");
        }
    }

    @Test
    void execute_whenNhiemVuCreationFails_shouldNotFailOverallResult() {
        BusinessDocument source = newDocument("vbden", "{}");
        when(documentRepository.findById(source.getId())).thenReturn(Optional.of(source));
        UUID vbcddhId = UUID.randomUUID();
        when(addDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(vbcddhId));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(addVbcddhDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("loi gia lap"));

        var assignee = new NhiemVuCdDhAssigneeDto();
        assignee.setName("Phong A");
        assignee.setIdentifier("H05.01");

        var request = new GuiSangVbcddhRequest();
        request.setId(source.getId());
        request.setListChuTri(List.of(assignee));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(vbcddhId);
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Van ban", documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    private AppUser newUser(String username, String fullName) {
        return new AppUser("kc-" + username, username, fullName, null, null, null, true,
            null, 0, null, null, null, null, null, null, null, false, null);
    }
}
