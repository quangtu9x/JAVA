package com.td.application.viewquery;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.duthao.DraftProcessDocumentRepository;
import com.td.application.duthao.DuThaoDocumentRepository;
import com.td.application.duthao.DuThaoStatus;
import com.td.application.hosocv.HoSoCVDocumentRepository;
import com.td.application.hosocv.HoSoCVStatus;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.ListDocumentFilesByProcessUseCase;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.ListFilesByProcessRequest;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.ResolveIdentifierByLevelUseCase;
import com.td.application.sharedcore.SearchAppUsersRequest;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.application.vbden.VbdenStatusService;
import com.td.application.vbdi.VbdiDocumentOutFilter;
import com.td.application.vbdi.VbdiDocumentRepository;
import com.td.application.vbdi.VbdiStatus;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.duthao.DraftProcessDocument;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.vbcddh.VbcddhDocument;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-102 (View Query Engine) — Definition of Done: endpoint dispatch theo viewname, filter theo
 * keys, phân trang, cho cả 3 view tối thiểu: vwDocument_ChoXuLy, vwuserbyusername, vwuserbyparentid.
 */
@ExtendWith(MockitoExtension.class)
class ViewQueryServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private com.td.application.hosoluutru.HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    @Mock
    private com.td.application.hosoluutru.HoSoLuuTruPhieuYeuCauDocumentRepository hoSoLuuTruPhieuYeuCauDocumentRepository;
    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private com.td.application.vbden.XinYKienDocumentRepository xinYKienDocumentRepository;
    @Mock
    private com.td.application.vbden.VbdenResDocumentRepository vbdenResDocumentRepository;
    @Mock
    private com.td.application.vbcddh.VbcddhDocumentRepository vbcddhDocumentRepository;
    @Mock
    private com.td.application.phieutrinhtd.PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ResolveIdentifierByLevelUseCase resolveIdentifierByLevelUseCase;
    @Mock
    private GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    @Mock
    private DraftProcessDocumentRepository draftProcessDocumentRepository;
    @Mock
    private ListDocumentFilesByProcessUseCase listDocumentFilesByProcessUseCase;
    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;

    private ViewQueryService service;

    @BeforeEach
    void setUp() {
        service = new ViewQueryService(
            documentRepository, duThaoDocumentRepository, hoSoCVDocumentRepository,
            hoSoLuuTruDocumentRepository, hoSoLuuTruPhieuYeuCauDocumentRepository, vbdiDocumentRepository,
            xinYKienDocumentRepository, vbdenResDocumentRepository, vbcddhDocumentRepository,
            phieuTrinhTdDocumentRepository,
            new DynamicDocumentFieldsService(),
            appUserRepository, currentUserProvider, resolveIdentifierByLevelUseCase, getUserOrganizationProfileUseCase,
            draftProcessDocumentRepository, listDocumentFilesByProcessUseCase, listDocumentFilesUseCase);
        // DT-007 (27/08/2026): moi vet draft_process ghep them "files" trong response, loc theo
        // cap (parentDraftId, processId) - stub mac dinh trang rong de cac test khong lien quan
        // file khong bi NPE (da co guard parentid khong hop le nen phan lon test se khong goi toi
        // stub nay); test rieng execute_duthaoVwGroupProcessByDraftId_* override lai khi can kiem
        // tra "files".
        lenient().when(listDocumentFilesByProcessUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 20, 0L, 0, true, true));
        // Bugfix (bao loi nguoi dung "danh sach chua hien thi duoc tep dinh kem"): danh sach chinh
        // (toFilteredDocumentFields(DuThaoDocument,...)) gio co the goi listDocumentFilesUseCase khi
        // "items" yeu cau "files"/"getall" - stub mac dinh trang rong de cac test khong quan tam file
        // khong bi NPE; test rieng kiem tra "files" o danh sach se override lai.
        lenient().when(listDocumentFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 20, 0L, 0, true, true));
        // vwuserbyparentid (25/08/2026) goi buildProfileForUser() KHONG DIEU KIEN cho moi user tra
        // ve - stub mac dinh de cac test khong quan tam breadcrumb khong bi NPE; xem test rieng
        // execute_userByParentId_shouldIncludeOrganizationProfileFields.
        lenient().when(getUserOrganizationProfileUseCase.buildProfileForUser(any()))
            .thenReturn(new UserOrganizationProfileDto());
    }

    @Test
    void execute_withUnsupportedViewname_shouldThrow() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwKhongTonTai");

        assertThatThrownBy(() -> service.execute("vbden", request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vwKhongTonTai");
    }

    @Test
    void execute_choXuLy_shouldReturnPendingDocumentsForUser() {
        BusinessDocument document = newDocument("Van ban cho xu ly", "vbden", "{\"received_date\":\"2026-08-04\"}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(documentRepository.findPendingForUser(eq("vbden"), eq("username_test"), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban cho xu ly");
        assertThat(result.getItems().get(0)).containsEntry("received_date", "2026-08-04");
    }

    // Bo sung 04/09/2026 (yeu cau nguoi dung: "khi gan tep dinh kem luu luon thong tin files vao
    // dynamic_fields de khong phai doc lai DB de map, neu khong co moi doc lai" - xem javadoc
    // VbdenFilesJsonbSync/toFilteredDocumentFields(BusinessDocument,...)): dynamic_fields DA CO san
    // "files" (VbdenFilesJsonbSync ghi tu truoc) -> dung THANG, KHONG goi lai listDocumentFilesUseCase.
    @Test
    void execute_choXuLy_withFilesAlreadyCachedInDynamicFields_shouldNotQueryFileMetadata() {
        BusinessDocument document = newDocument("Van ban cho xu ly", "vbden",
            "{\"files\":[{\"name\":\"cached.docx\",\"size\":10,\"type\":\"docx\"}]}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(documentRepository.findPendingForUser(eq("vbden"), eq("username_test"), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        var result = service.execute("vbden", request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.getItems().get(0).get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).containsEntry("name", "cached.docx");
        verify(listDocumentFilesUseCase, never()).execute(any());
    }

    // dynamic_fields CHUA co "files" (van ban cu, tao truoc khi co VbdenFilesJsonbSync, hoac dong bo
    // thieu) -> fallback doc lai DB qua listDocumentFilesUseCase.
    @Test
    void execute_choXuLy_withoutCachedFiles_shouldFallbackToQueryingFileMetadata() {
        BusinessDocument document = newDocument("Van ban cho xu ly", "vbden", "{}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(documentRepository.findPendingForUser(eq("vbden"), eq("username_test"), any())).thenReturn(page);
        FileDto fileDto = FileDto.builder().originalFileName("moi_doc.docx").fileSize(2048L).build();
        when(listDocumentFilesUseCase.execute(document.getId())).thenReturn(
            new PaginationResponse<>(List.of(fileDto), 0, 20, 1L, 1, true, true));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        var result = service.execute("vbden", request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.getItems().get(0).get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).containsEntry("name", "moi_doc.docx");
        verify(listDocumentFilesUseCase).execute(document.getId());
    }

    // "items" khong yeu cau "files" -> khong can goi listDocumentFilesUseCase (toi uu, dung
    // wantsField() da dung chung voi nhanh duthao).
    @Test
    void execute_choXuLy_withoutFilesInItemsFilter_shouldNotQueryFileMetadata() {
        BusinessDocument document = newDocument("Van ban cho xu ly", "vbden", "{}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(documentRepository.findPendingForUser(eq("vbden"), eq("username_test"), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setItems("title;subject");

        service.execute("vbden", request);

        verify(listDocumentFilesUseCase, never()).execute(any());
    }

    // Slice 7: FE gui viewname="chutri" (khong phai "vwdocument_choxuly") cho tab "Cho xu ly" mac
    // dinh (choxuly.jsx) - phai alias dung sang cung query, khong duoc rơi vao default -> throw.
    @Test
    void execute_chutri_shouldAliasToSameQueryAsChoXuLy() {
        BusinessDocument document = newDocument("Van ban chutri", "vbden", "{}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(documentRepository.findPendingForUser(eq("vbden"), eq("username_test"), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chutri");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban chutri");
    }

    // Slice 7 (tim kiem/loc): keyword+columns -> goi overload co keyword; khong duoc goi ban 3-tham-so.
    @Test
    void execute_choXuLy_withKeyword_shouldCallKeywordOverload() {
        BusinessDocument document = newDocument("Van ban co tu khoa", "vbden", "{}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(documentRepository.findPendingForUser(
                eq("vbden"), eq("username_test"), eq("cong van"), eq(List.of("subject", "code")), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");
        request.setKeyword("cong van");
        request.setColumns(List.of("subject", "code"));

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        verify(documentRepository, never()).findPendingForUser(anyString(), anyString(), any());
    }

    // keyword co gia tri nhung columns rong -> KHONG loc (tranh doan field mac dinh thay FE).
    @Test
    void execute_choXuLy_withKeywordButNoColumns_shouldIgnoreKeywordAndCallPlainOverload() {
        BusinessDocument document = newDocument("Van ban A", "vbden", "{}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(documentRepository.findPendingForUser(eq("vbden"), eq("username_test"), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setKeyword("cong van");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_choXuLy_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwdocument_choxuly");
        request.setKeys(List.of());

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        assertThat(result.getItems()).isEmpty();
        verify(documentRepository, never()).findPendingForUser(anyString(), anyString(), any());
    }

    @Test
    void execute_choXuLy_shouldFilterFieldsByItems() {
        BusinessDocument document = newDocument("Van ban A", "vbden", "{\"received_date\":\"2026-08-04\",\"so_tien_number\":100.0}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(documentRepository.findPendingForUser(any(), any(), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setItems("title;received_date");

        var result = service.execute("vbden", request);

        // Bugfix 24/08/2026 (xem javadoc ItemsFilter#filter): "id" la intrinsic (nhu UNID Domino),
        // luon con lai du "items" khong liet ke ten no - cap nhat assertion cho dung hanh vi MOI.
        assertThat(result.getItems().get(0)).containsOnlyKeys("id", "title", "received_date");
    }

    @Test
    void execute_daXuLy_shouldReturnProcessedDocumentsForUser() {
        BusinessDocument document = newDocument("Van ban da xu ly", "vbden", "{\"received_date\":\"2026-08-04\"}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(documentRepository.findProcessedForUser(eq("vbden"), eq("username_test"), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_DaXuLy");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban da xu ly");
    }

    @Test
    void execute_daXuLy_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwdocument_daxuly");
        request.setKeys(List.of());

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findProcessedForUser(anyString(), anyString(), any());
    }

    @Test
    void execute_qtxlByParentId_shouldReturnChildDocuments() {
        UUID parentId = UUID.randomUUID();
        var document = newVbdenResDocument("Buoc xu ly 1", "{\"parentid\":\"" + parentId + "\"}");
        var page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdenResDocumentRepository.findByParentId(eq(parentId.toString()), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("QTXL_by_parentid");
        request.setKeys(List.of(parentId.toString()));
        request.setItems("getall");

        var result = service.execute("vbden_res", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Buoc xu ly 1");
    }

    @Test
    void execute_qtxlByParentId_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("qtxl_by_parentid");
        request.setKeys(List.of());

        var result = service.execute("vbden_res", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdenResDocumentRepository, never()).findByParentId(anyString(), any());
    }

    @Test
    void execute_userByUsername_found_shouldReturnSingleItemPage() {
        AppUser user = newUser("alice", "Alice Nguyen", "alice@example.com");
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("alice")).thenReturn(Optional.of(user));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserbyusername");
        request.setKeys(List.of("alice"));

        var result = service.execute("qlvb", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("username", "alice");
        assertThat(result.getItems().get(0)).containsEntry("full_name", "Alice Nguyen");
    }

    @Test
    void execute_userByUsername_notFound_shouldReturnEmptyPage() {
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("unknown")).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserbyusername");
        request.setKeys(List.of("unknown"));

        var result = service.execute("qlvb", request);

        assertThat(result.getTotalItems()).isZero();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void execute_userByParentId_shouldSearchByOrganizationId() {
        UUID organizationId = UUID.randomUUID();
        AppUser user = newUser("bob", "Bob Tran", "bob@example.com");
        when(appUserRepository.search(any(), any())).thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserbyparentid");
        request.setKeys(List.of(organizationId.toString()));

        var result = service.execute("qlvb", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("username", "bob");

        ArgumentCaptor<SearchAppUsersRequest> captor = ArgumentCaptor.forClass(SearchAppUsersRequest.class);
        verify(appUserRepository).search(captor.capture(), any());
        assertThat(captor.getValue().getOrganizationId()).isEqualTo(organizationId);
    }

    // 25/08/2026 (ItemsActiveList.jsx - "Danh sách người dùng" theo tổ chức đã chọn trong
    // TreeComponent): FE đọc field kiểu Domino cũ (name/position/unit/department/agency/
    // agency_level/caplanhdao/level), KHÔNG phải full_name/organization_id thô — xác nhận
    // vwuserbyparentid tái dùng ĐÚNG GetUserOrganizationProfileUseCase#buildProfileForUser.
    @Test
    void execute_userByParentId_shouldIncludeOrganizationProfileFields() {
        UUID organizationId = UUID.randomUUID();
        AppUser user = newUser("bob", "Bob Tran", "bob@example.com");
        when(appUserRepository.search(any(), any())).thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

        UserOrganizationProfileDto profile = new UserOrganizationProfileDto();
        profile.setPosition("Chuyên viên");
        profile.setCapLanhDao("khong");
        profile.setLevel("3");
        profile.setAgencyLevelName("Tỉnh Thanh Hóa");
        profile.setAgencyLevelId("agency-level-id");
        profile.setAgencyName("Sở Khoa học và Công nghệ");
        profile.setAgencyId("agency-id");
        profile.setUnitName("Phòng Quản lý công nghệ");
        profile.setUnitId("unit-id");
        profile.setDepartmentName("");
        profile.setDepartmentId("");
        when(getUserOrganizationProfileUseCase.buildProfileForUser(user)).thenReturn(profile);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserbyparentid");
        request.setKeys(List.of(organizationId.toString()));

        var result = service.execute("qlvb", request);

        assertThat(result.getItems().get(0))
            .containsEntry("name", "Bob Tran")
            .containsEntry("position", "Chuyên viên")
            .containsEntry("caplanhdao", "khong")
            .containsEntry("level", "3")
            .containsEntry("agency_level", "Tỉnh Thanh Hóa")
            .containsEntry("agency", "Sở Khoa học và Công nghệ")
            .containsEntry("agency_id", "agency-id")
            .containsEntry("unit", "Phòng Quản lý công nghệ")
            .containsEntry("unit_id", "unit-id")
            .containsEntry("department", "");
    }

    // "Người ký" (Slice 1 dự thảo, 24/08/2026) — chỉ trả về user CÓ capLanhDao, loại "khong"/rỗng.
    @Test
    void execute_userLeaderByParentId_shouldOnlyReturnLeaders() {
        UUID organizationId = UUID.randomUUID();
        AppUser leader = newLeaderUser("bacpv", "Pham Viet Bac", 1, "phogiamdoc");
        AppUser notLeader = newLeaderUser("nhanvien01", "Nguyen Van A", 2, "khong");
        AppUser blankCapLanhDao = newLeaderUser("nhanvien02", "Tran Thi B", 3, "");
        when(appUserRepository.search(any(), any()))
            .thenReturn(new PageImpl<>(List.of(leader, notLeader, blankCapLanhDao), PageRequest.of(0, 200), 3));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserleaderbyparentid");
        request.setKeys(List.of(organizationId.toString()));
        request.setItems("name;username;sort_order;position;caplanhdao;delegation_obj");

        var result = service.execute("qlvb", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0))
            .containsEntry("name", "Pham Viet Bac")
            .containsEntry("username", "bacpv")
            .containsEntry("caplanhdao", "phogiamdoc");

        ArgumentCaptor<SearchAppUsersRequest> captor = ArgumentCaptor.forClass(SearchAppUsersRequest.class);
        verify(appUserRepository).search(captor.capture(), any());
        assertThat(captor.getValue().getOrganizationId()).isEqualTo(organizationId);
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void execute_userLeaderByParentId_shouldSortBySortOrderAndReadDynamicFields() {
        UUID organizationId = UUID.randomUUID();
        AppUser second = newLeaderUser("truongphong2", "Truong Phong 2", 20, "truongphong");
        AppUser first = newLeaderUser("truongphong1", "Truong Phong 1", 10, "truongphong")
            .replaceDynamicFields("{\"position\":\"Trưởng phòng\",\"delegation_obj\":\"{\\\"user\\\":\\\"gd\\\"}\"}");
        when(appUserRepository.search(any(), any()))
            .thenReturn(new PageImpl<>(List.of(second, first), PageRequest.of(0, 200), 2));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserleaderbyparentid");
        request.setKeys(List.of(organizationId.toString()));
        request.setItems("getall");

        var result = service.execute("qlvb", request);

        assertThat(result.getItems()).hasSize(2);
        // sort_order tang dan - "first" (sort_order=10) phai dung truoc "second" (sort_order=20).
        assertThat(result.getItems().get(0)).containsEntry("username", "truongphong1");
        assertThat(result.getItems().get(0)).containsEntry("position", "Trưởng phòng");
        assertThat(result.getItems().get(1)).containsEntry("username", "truongphong2");
        assertThat(result.getItems().get(1)).containsEntry("position", "");
    }

    @Test
    void execute_userLeaderByParentId_withoutOrganizationId_shouldReturnEmpty() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserleaderbyparentid");
        request.setKeys(List.of("not-a-uuid"));

        var result = service.execute("qlvb", request);

        assertThat(result.getTotalItems()).isZero();
        assertThat(result.getItems()).isEmpty();
        verify(appUserRepository, never()).search(any(), any());
    }

    @Test
    void execute_userByParentId_withInvalidUuid_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserbyparentid");
        request.setKeys(List.of("not-a-uuid"));

        var result = service.execute("qlvb", request);

        assertThat(result.getItems()).isEmpty();
        verify(appUserRepository, never()).search(any(), any());
    }

    @Test
    void execute_vandiChoBanHanh_shouldReturnDocumentsWithPendingSignatureStatus() {
        VbdiDocument document = new VbdiDocument("Van ban da trinh ky", "vbdi", "PENDING_SIGNATURE", null, null, null, null);
        document.replaceDynamicFields("{}");
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByStatus(eq("vbdi"), eq("PENDING_SIGNATURE"), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwvandi_chobanhanh");
        request.setItems("getall");

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban da trinh ky");
    }

    @Test
    void execute_vandiDaBanHanh_shouldReturnDocumentsWithIssuedStatus() {
        VbdiDocument document = new VbdiDocument("Van ban da ban hanh", "vbdi", "ISSUED", null, null, null, null);
        document.replaceDynamicFields("{\"document_number\":\"5/2026-VBD\"}");
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByStatus(eq("vbdi"), eq("ISSUED"), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwvandi_dabanhanh");
        request.setItems("getall");

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("document_number", "5/2026-VBD");
    }

    // Mo rong BL-240 (bugfix): "creator" -> "user_creator" (dung field TaoHoSoCVUseCase that su
    // stamp) + doi sang HoSoCVDocumentRepository (bang rieng hosocv_documents).
    @Test
    void execute_hosocvToanBo_shouldReturnDocumentsByUserCreatorFieldValue() {
        var document = newHoSoCVDocument("Ho so xu ly don thu", "{\"user_creator\":\"nguyenvana\"}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(hoSoCVDocumentRepository.findByAnyFieldContains(eq("hosocv"), eq(List.of("user_creator")), eq("nguyenvana"), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_All");
        request.setKeys(List.of("nguyenvana"));
        request.setItems("getall");

        var result = service.execute("hosocv", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Ho so xu ly don thu");
    }

    @Test
    void execute_hosocvToanBo_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuser_all");
        request.setKeys(List.of());

        var result = service.execute("hosocv", request);

        assertThat(result.getTotalItems()).isZero();
        verify(hoSoCVDocumentRepository, never()).findByAnyFieldContains(anyString(), any(), anyString(), any());
    }

    // 09/09/2026 (cutover dashboard tu Domino qlvb/search?databasetype=hosocv - bo qua Elasticsearch,
    // xac nhan BE_THANHHOA scope THEO CA NHAN, khong org-scope - xem javadoc VIEW_HOSOCV_TOAN_BO):
    // "search" khong rong -> phai di qua findByVbdenOrgView (KHONG phai findByAnyFieldContains), khoa
    // ca nhan (user_creator) van giu nguyen, hop nhat du 13 tieu chi cua buildtimkiemnangcao() that.
    @Test
    void execute_hosocvToanBo_withAdvancedSearch_shouldQueryViaOrgViewWithMergedFilter() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(hoSoCVDocumentRepository.findByVbdenOrgView(eq("hosocv"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuser_all");
        request.setKeys(List.of("$username$"));
        Map<String, Object> search = new java.util.LinkedHashMap<>();
        search.put("process_handler", "Nguyen Van B");
        search.put("approved_by", "Tran Thi C");
        search.put("status", "1");
        search.put("trinhlanhdaoduyet", "2");
        search.put("storaged", "0");
        search.put("category", "Danh muc A");
        search.put("name", "HS-2026-001");
        search.put("title", "Ho so xu ly don thu");
        search.put("first_topic", "De muc lon A");
        search.put("second_topic", "De muc nho B");
        search.put("create_date", List.of("01/08/2026", "31/08/2026"));
        search.put("end_date", List.of("05/08/2026", "20/08/2026"));
        search.put("storaged_date", List.of("01/09/2026", "10/09/2026"));
        request.setSearch(search);

        service.execute("hosocv", request);

        var filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_creator");
        assertThat(filter.getOrgValue()).isEqualTo("nguyenvana");
        assertThat(filter.getIncludeFieldValues())
            .containsEntry("process_handler", List.of("Nguyen Van B"))
            .containsEntry("approved_by", List.of("Tran Thi C"))
            .containsEntry("status", List.of("1"))
            .containsEntry("trinhlanhdaoduyet", List.of("2"))
            .containsEntry("storaged", List.of("0"))
            .containsEntry("category", List.of("Danh muc A"));
        assertThat(filter.getContainsAnyFieldValues())
            .containsEntry("name", List.of("HS-2026-001"))
            .containsEntry("title", List.of("Ho so xu ly don thu"))
            .containsEntry("first_topic", List.of("De muc lon A"))
            .containsEntry("second_topic", List.of("De muc nho B"));
        assertThat(filter.getMinCreatedOn()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(filter.getMaxCreatedOn()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(filter.getMinEndDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(filter.getMaxEndDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(filter.getMinStoragedDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(filter.getMaxStoragedDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        verify(hoSoCVDocumentRepository, never()).findByAnyFieldContains(anyString(), any(), anyString(), any());
    }

    @Test
    void execute_hosocvToanBo_withoutAdvancedSearch_shouldStillUsePlainPersonalQueue() {
        var document = newHoSoCVDocument("Ho so xu ly don thu", "{\"user_creator\":\"nguyenvana\"}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(hoSoCVDocumentRepository.findByAnyFieldContains(eq("hosocv"), eq(List.of("user_creator")), eq("nguyenvana"), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuser_all");
        request.setKeys(List.of("nguyenvana"));

        service.execute("hosocv", request);

        verify(hoSoCVDocumentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // Bugfix 08/09/2026 (nguoi dung bao "GetView hosocv voi keys=[\"$username$\"] khong ra du lieu"):
    // ItemsList.jsx (hosocv) mac dinh viewkey=["$username$"] cho hau het view ca nhan (toanbo,
    // xulychinh, storaged_*, trinh_*, nguoiduyet_choduyet...) - macro nay PHAI duoc server tu thay
    // bang username phien dang nhap (CurrentUserProvider), dung cong thuc da dung cho vbdi
    // (resolveUsernameToken) - truoc fix nay, cac case hosocv dung THANG "key" nen tim "$username$"
    // nhu 1 gia tri that, khong bao gio khop du lieu.
    @Test
    void execute_hosocvToanBo_withUsernameTokenMacro_shouldResolveViaCurrentUserProvider() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        var document = newHoSoCVDocument("Ho so xu ly don thu", "{\"user_creator\":\"nguyenvana\"}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(hoSoCVDocumentRepository.findByAnyFieldContains(eq("hosocv"), eq(List.of("user_creator")), eq("nguyenvana"), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_All");
        request.setKeys(List.of("$username$"));
        request.setItems("getall");

        var result = service.execute("hosocv", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        verify(hoSoCVDocumentRepository).findByAnyFieldContains(eq("hosocv"), eq(List.of("user_creator")), eq("nguyenvana"), any());
    }

    @Test
    void execute_hosocvChuTriDangXuLy_withUsernameTokenMacro_shouldResolveViaCurrentUserProvider() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("chuyenvien01"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(hoSoCVDocumentRepository.findByVbdenOrgView(eq("hosocv"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("hoso_chutri_dangxuly");
        request.setKeys(List.of("$username$"));

        service.execute("hosocv", request);

        assertThat(filterCaptor.getValue().getOrgValue()).isEqualTo("chuyenvien01");
    }

    // Mo rong BL-240 - vai view moi tieu bieu (khong lap lai het ~26 view, chi khoa hanh vi predicate
    // chinh: khoa ca nhan don, khoa + status tinh, khoa + field dong, list-member, coordinated
    // "_chua"/"_da", trash, due-date).

    @Test
    void execute_hosocvChuTriDangXuLy_shouldFilterByProcessHandlerAndStaticStatus() {
        var document = newHoSoCVDocument("Ho so dang xu ly", "{\"user_process_handler\":\"chuyenvien01\"}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(hoSoCVDocumentRepository.findByVbdenOrgView(eq("hosocv"), filterCaptor.capture(), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("hoso_chutri_dangxuly");
        request.setKeys(List.of("chuyenvien01"));

        var result = service.execute("hosocv", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_process_handler");
        assertThat(filter.getOrgValue()).isEqualTo("chuyenvien01");
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of(HoSoCVStatus.DANG_XU_LY));
    }

    @Test
    void execute_hosocvProcessHandlerStoraged2_shouldFilterByDynamicStoragedField() {
        var document = newHoSoCVDocument("Ho so da chuyen", "{\"storaged\":\"2\"}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(hoSoCVDocumentRepository.findByVbdenOrgView(eq("hosocv"), filterCaptor.capture(), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Process_Handler_Storaged_2");
        request.setKeys(List.of("chuyenvien01"));

        service.execute("hosocv", request);

        assertThat(filterCaptor.getValue().getIncludeFieldValues()).containsEntry("storaged", List.of("2"));
    }

    @Test
    void execute_hosocvCoordinator_shouldUseListMemberMatchNotPlainContains() {
        var document = newHoSoCVDocument("Ho so phoi hop", "{\"user_coordinator\":\"chuyenvien01;chuyenvien02\"}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(hoSoCVDocumentRepository.findByVbdenOrgView(eq("hosocv"), filterCaptor.capture(), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Coordinator");
        request.setKeys(List.of("chuyenvien01"));

        var result = service.execute("hosocv", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(filterCaptor.getValue().getOrgListFieldNames()).containsExactly("user_coordinator");
    }

    @Test
    void execute_hosocvCoordinatedStatus_chuaSuffix_shouldExcludeUserCoordinated() {
        var document = newHoSoCVDocument("Ho so chua phoi hop", "{}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(hoSoCVDocumentRepository.findByListFieldMemberExcludingOther(
                eq("hosocv"), eq("user_coordinator"), eq("user_coordinated"), eq("chuyenvien01"), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Coordinated_Status");
        request.setKeys(List.of("chuyenvien01_chua"));

        var result = service.execute("hosocv", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_hosocvCoordinatedStatus_daSuffix_shouldMatchUserCoordinatedOnly() {
        var document = newHoSoCVDocument("Ho so da phoi hop", "{}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(hoSoCVDocumentRepository.findByListFieldMemberExcludingOther(
                eq("hosocv"), eq("user_coordinated"), eq(null), eq("chuyenvien01"), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Coordinated_Status");
        request.setKeys(List.of("chuyenvien01_da"));

        var result = service.execute("hosocv", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_hosocvTrash_shouldReturnDeletedDocuments() {
        var document = newHoSoCVDocument("Ho so da xoa", "{}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(hoSoCVDocumentRepository.findDeleted(eq("hosocv"), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwTrash");

        var result = service.execute("hosocv", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_hosocvDenHanNopLuuTru_shouldQueryDueDateWithStoragedFilter() {
        var document = newHoSoCVDocument("Ho so den han", "{}");
        Page<com.td.domain.hosocv.HoSoCVDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(hoSoCVDocumentRepository.findByFieldValueDueDate(
                eq("hosocv"), eq("user_process_handler"), eq("chuyenvien01"), eq("storage_deadline"),
                eq(false), eq(Map.of("storaged", "0")), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Process_Handler_Den_Han_Nop_Luu_Tru");
        request.setKeys(List.of("chuyenvien01"));

        var result = service.execute("hosocv", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_shouldClampPageSizeAndPageNumber() {
        when(documentRepository.findPendingForUser(any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setPageNumber(-5);
        request.setPageSize(9999);

        service.execute("vbden", request);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(documentRepository).findPendingForUser(any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    /**
     * Khớp quy ước legacy Domino (1-based, {@code TDUlties.java}: {@code startIdx = (pageNumber -
     * 1) * pageSize}) và hợp đồng đã ghi trong {@code docs/migration/06-cross-reference-fe-be-dynamic.md}
     * §4.6 ("FE không gửi pageNumber/pageSize → BE mặc định 1/20"). {@code pageNumber=1} phải trả
     * về TRANG ĐẦU TIÊN (Spring Data page index 0), không phải trang thứ hai.
     */
    @Test
    void execute_pageNumberOne_shouldMapToFirstSpringDataPage() {
        when(documentRepository.findPendingForUser(any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setPageNumber(1);

        service.execute("vbden", request);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(documentRepository).findPendingForUser(any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    }

    @Test
    void execute_pageNumberTwo_shouldMapToSecondSpringDataPage() {
        when(documentRepository.findPendingForUser(any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setPageNumber(2);

        service.execute("vbden", request);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(documentRepository).findPendingForUser(any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
    }

    @Test
    void execute_shouldEchoBackOneBasedPageNumberInResponse() {
        BusinessDocument document = newDocument("Van ban", "vbden", "{}");
        when(documentRepository.findPendingForUser(any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(1, 20), 25));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocument_ChoXuLy");
        request.setKeys(List.of("username_test"));
        request.setPageNumber(2);

        var result = service.execute("vbden", request);

        assertThat(result.getPageNumber()).isEqualTo(2);
    }

    // N03/N13 (VBDen - hang doi/han xu ly theo to chuc): Slice 4, doc dung tu
    // docs/views_domino/view_vanbanden.dxl. Identifier luon resolve tu user dang dang nhap
    // (CurrentUserProvider), khong tu request.getKeys() (an ninh - xem javadoc queryVbdenOrgView).

    @Test
    void execute_captrengiao_shouldExcludeInternalDocuments() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        BusinessDocument document = newDocument("Van ban cap tren giao", "vbden", "{\"unit_process\":[\"PHONG_TH\"]}");
        Page<BusinessDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any())).thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("captrengiao");
        request.setKeys(List.of("KHAC_HOAN_TOAN")); // client gui gi cung bi bo qua
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban cap tren giao");
        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("unit_process", "unit_coordinate", "unit_to_read");
        assertThat(filter.getOrgValue()).isEqualTo("PHONG_TH");
        assertThat(filter.getExcludeFieldValues()).containsEntry("status", List.of("3", "4"));
        assertThat(filter.getExcludeFieldValues()).containsEntry("internal", List.of("1"));
    }

    // Slice 7 (tim kiem/loc): keyword/columns tren view org-scoped song thang vao VbdenOrgViewFilter.
    @Test
    void execute_captrengiao_withKeyword_shouldPropagateKeywordIntoFilter() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("captrengiao");
        request.setKeyword("cong van A");
        request.setColumns(List.of("subject", "code"));

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getKeyword()).isEqualTo("cong van A");
        assertThat(filter.getKeywordColumns()).containsExactly("subject", "code");
    }

    // Bo sung 14/09/2026 (port route "vbden/thungrac/thungrac" - chuyen GetViewColl ve dung chung
    // GetView, xem javadoc VIEW_VBDEN_TRASH). Van ban goc ("vbden") join qua parentid tren ban ghi
    // "so" ("vbden_so", da soft-delete) - dung queryVbdenOrgView deletedOnly=true, is_revoke!="1".
    @Test
    void execute_vbdenTrash_shouldQueryDeletedVbdenSoWithIsRevokeExcludedAndJoinParent() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));

        BusinessDocument parent = newDocument("Cong van goc", "vbden", "{}");
        BusinessDocument entry = newDocument("So", "vbden_so",
            "{\"parentid\":\"" + parent.getId() + "\",\"document_number\":\"7\","
                + "\"identifier_creator\":\"PHONG_TH\"}");
        when(documentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("sovanban_bixoa");
        request.setKeys(List.of("KHAC_HOAN_TOAN")); // client gui gi cung bi bo qua

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        Map<String, Object> item = result.getItems().get(0);
        assertThat(item).containsEntry("title", "Cong van goc"); // tu van ban goc (join)
        assertThat(item).containsEntry("document_number", "7"); // tu ban ghi so (khong bi de)
        assertThat(item).containsEntry("id", entry.getId()); // id = ban ghi so, khong phai van ban goc
        assertThat(item).containsEntry("databasetype", "vbden");

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.isDeletedOnly()).isTrue();
        assertThat(filter.getOrgFieldNames()).containsExactly("identifier_creator");
        assertThat(filter.getOrgValue()).isEqualTo("PHONG_TH");
        assertThat(filter.getExcludeFieldValues()).containsEntry("is_revoke", List.of("1"));
    }

    @Test
    void execute_vbdenTrash_parentAlreadyGone_shouldReturnEntryFieldsWithoutThrowing() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));

        BusinessDocument entry = newDocument("So", "vbden_so",
            "{\"parentid\":\"" + UUID.randomUUID() + "\",\"document_number\":\"9\"}");
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("sovanban_bixoa");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        Map<String, Object> item = result.getItems().get(0);
        assertThat(item).containsEntry("document_number", "9");
        assertThat(item).containsEntry("id", entry.getId());
        assertThat(item).doesNotContainKey("title");
    }

    @Test
    void execute_vbdenTrash_whenCurrentUserHasNoIdentifier_shouldReturnEmptyPageWithoutQuerying() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("sovanban_bixoa");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // Bo sung 14/09/2026 (2, yeu cau nguoi dung "kiem tra 2 view anh em, neu dung thi port luon") -
    // xem javadoc VIEW_VBDEN_TRASH_REVOKED: KHONG dung deletedOnly (VbdiRevokeTaskHandler khong dung
    // deleted_on) - loc "reply"+"is_revoke" deu ="1" (AND, includeFieldValues nhieu key).
    @Test
    void execute_vbdenTrashRevoked_shouldFilterByReplyAndIsRevokeWithoutDeletedOnly() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        BusinessDocument entry = newDocument("So", "vbden_so", "{\"document_number\":\"3\"}");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("sovanban_bithuhoi");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.isDeletedOnly()).isFalse();
        assertThat(filter.getIncludeFieldValues())
            .containsEntry("reply", List.of("1"))
            .containsEntry("is_revoke", List.of("1"));
        assertThat(filter.getOrgFieldNames()).containsExactly("identifier_creator");
        assertThat(filter.getOrgValue()).isEqualTo("PHONG_TH");
    }

    // Bo sung 14/09/2026 (2): "sovanban_phieuyeucau_bixoa" - loc deletedOnly=true VA
    // request_form="true" (2 dieu kien cung luc, dung dxl that). Danh sach se rong tren du lieu that
    // (chua co use case nao set request_form) nhung co che truy van phai dung.
    @Test
    void execute_vbdenTrashRequestForm_shouldFilterByDeletedOnlyAndRequestForm() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("sovanban_phieuyeucau_bixoa");

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.isDeletedOnly()).isTrue();
        assertThat(filter.getIncludeFieldValues()).containsEntry("request_form", List.of("true"));
    }

    // dxl: captrengiao (internal!="1") vs dennoibo (internal="1", chi khoa unit_process) - PHAI
    // KHAC NHAU (Slice 2 truoc day sai khi dung chung 1 query).
    @Test
    void execute_dennoibo_shouldRequireInternalEqualsOneAndKeyOnlyByUnitProcess() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dennoibo");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("unit_process");
        assertThat(filter.getExcludeFieldValues()).containsEntry("status", List.of("3", "4"));
        assertThat(filter.getIncludeFieldValues()).containsEntry("internal", List.of("1"));
    }

    @Test
    void execute_quamangnoibo_shouldQueryByContactUnitIdentifierAndDocumentInternetOne() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        BusinessDocument document = newDocument("Van ban qua mang noi bo", "vbden", "{\"contact_unit_identifier\":[\"PHONG_TH\"]}");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("quamangnoibo");
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban qua mang noi bo");
        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("contact_unit_identifier");
        assertThat(filter.getExcludeFieldValues()).containsEntry("reply", List.of("1"));
        assertThat(filter.getIncludeFieldValues()).containsEntry("document_internet", List.of("1"));
    }

    // dxl 1430-1466: giong quamangnoibo nhung document_internet="2".
    @Test
    void execute_lienthong_shouldQueryByDocumentInternetTwo() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("lienthong");

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getIncludeFieldValues()).containsEntry("document_internet", List.of("2"));
    }

    // dxl 2434-2470: dendientu = hop cua quamangnoibo (document_internet=1) va lienthong (=2) - GIO
    // DA CAI DUOC (Slice 2 truoc day tuong "khong co bang chung song" - sai, chi la khong co code
    // Java custom, view van ton tai that trong file DXL).
    @Test
    void execute_dendientu_shouldQueryByDocumentInternetOneOrTwo() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dendientu");

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getIncludeFieldValues()).containsEntry("document_internet", List.of("1", "2"));
    }

    // 31/08/2026 (port nốt vbden - docs/analysis/02-checklist-port-vbden-con-lai.md muc 1.4): dxl
    // 1339-1372 - "denquamang" (route rieng /vbden/xuly/denquamang) - UNION 4 field, khong dieu kien
    // document_internet/status (khac quamangnoibo/lienthong/dendientu).
    @Test
    void execute_denquamang_shouldQueryByFourOrgFieldsAndExcludeReply() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("denquamang");

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getOrgFieldNames())
            .containsExactly("contact_unit_identifier", "unit_process", "unit_coordinate", "unit_to_read");
        assertThat(filterCaptor.getValue().getExcludeFieldValues()).containsEntry("reply", List.of("1"));
    }

    // 09/09/2026 (port view "vwDocumentIn_Internet_Refuse", route FE /vbden/xuly/tuchoidenquamang):
    // dxl 828-858 - Form="DocumentIn"|"DocumentIn_Deleted" & status="5" & is_document_refuse="1",
    // khoa UNION 2 field (contact_unit_identifier;identifier) - xem javadoc TuChoiTiepNhanVbdenUseCase.
    @Test
    void execute_tuchoidenquamang_shouldQueryByTwoOrgFieldsAndStatus5AndRefuseFlag() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("tuchoidenquamang");

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getOrgFieldNames())
            .containsExactly("contact_unit_identifier", "identifier");
        assertThat(filterCaptor.getValue().getIncludeFieldValues())
            .containsEntry("status", List.of(VbdenStatusService.STATUS_TU_CHOI_TIEP_NHAN))
            .containsEntry("is_document_refuse", List.of("1"));
    }

    // 31/08/2026 (port trang landing mac dinh "toanbovanban" - docs/analysis/02-checklist-port-vbden-
    // con-lai.md): dxl 860-892, khoa UNION 7 field theo identifier_creator (KHAC 8-field
    // *_BY_IDENTIFIER_CREATOR dung boi daxuly_quahan/dunghan_cuatochuc - view do co them
    // "unit_to_read_manage").
    @Test
    void execute_toanbovanban_withoutReceivedDateRange_shouldQueryBySevenOrgFieldsAndNoDateFilter() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("toanbovanban");

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getOrgFieldNames()).containsExactly(
            "identifier_creator", "unit_process", "unit_process_registered", "unit_coordinate",
            "unit_coordinate_registered", "unit_to_read", "unit_to_read_registered");
        assertThat(filterCaptor.getValue().getMinReceivedDate()).isNull();
        assertThat(filterCaptor.getValue().getMaxReceivedDate()).isNull();
    }

    @Test
    void execute_toanbovanban_withReceivedDateRange_shouldParseDdMmYyyyIntoFilter() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("toanbovanban");
        request.setReceivedDateFrom("01/08/2026");
        request.setReceivedDateTo("31/08/2026");

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getMinReceivedDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(filterCaptor.getValue().getMaxReceivedDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void execute_toanbovanban_withMalformedReceivedDate_shouldIgnoreWithoutThrowing() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        when(documentRepository.findByVbdenOrgView(eq("vbden"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("toanbovanban");
        request.setReceivedDateFrom("2026-08-01"); // sai dinh dang (ISO thay vi dd/MM/yyyy)

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
    }

    // 09/09/2026 (cutover dashboard tu Domino qlvb/tichhop/TimKiemVanBanDenEs - bo qua Elasticsearch):
    // "toanbovanban" + "search" (dataAdvanceSearchAll day du 8 field cua path mac dinh CollapseVBden.jsx)
    // phai hop nhat CA org-scope (7-field toanbovanban) LAN dieu kien nang cao vao 1 filter duy nhat.
    @Test
    void execute_toanbovanban_withAdvancedSearch_shouldMergeOrgScopeAndAdvancedFilter() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("toanbovanban");
        Map<String, Object> search = new java.util.LinkedHashMap<>();
        search.put("status", "2");
        search.put("document_type", "Công văn");
        search.put("priority", "Hỏa tốc");
        search.put("sector", "Khoa học công nghệ");
        search.put("unit_in_charge", "UBND tinh");
        search.put("unit_process", "H56.09");
        search.put("user_process", "nguyenvana;tranthib");
        search.put("received_date", List.of("01/08/2026", "31/08/2026"));
        request.setSearch(search);

        service.execute("vbden", request);

        var filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly(
            "identifier_creator", "unit_process", "unit_process_registered", "unit_coordinate",
            "unit_coordinate_registered", "unit_to_read", "unit_to_read_registered");
        assertThat(filter.getIncludeFieldValues())
            .containsEntry("status", List.of("2"))
            .containsEntry("document_type", List.of("Công văn"))
            .containsEntry("priority", List.of("Hỏa tốc"))
            .containsEntry("sector", List.of("Khoa học công nghệ"));
        assertThat(filter.getContainsAnyFieldValues())
            .containsEntry("unit_in_charge", List.of("UBND tinh"))
            .containsEntry("unit_process", List.of("H56.09"))
            .containsEntry("user_process", List.of("nguyenvana", "tranthib"));
        assertThat(filter.getMinReceivedDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(filter.getMaxReceivedDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    // search.received_date la 2 nguon ngay nhan doc lap voi receivedDateFrom/To (man "Toan bo van ban"
    // thuong) - uu tien search.received_date khi FE gui CA HAI (dashboard "tim kiem nang cao" luon gui
    // qua search, khong dung receivedDateFrom/To truc tiep).
    @Test
    void execute_toanbovanban_withBothReceivedDateSources_shouldPreferSearchReceivedDate() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("toanbovanban");
        request.setReceivedDateFrom("01/01/2026");
        request.setReceivedDateTo("31/01/2026");
        Map<String, Object> search = new java.util.LinkedHashMap<>();
        search.put("received_date", List.of("01/08/2026", "31/08/2026"));
        request.setSearch(search);

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getMinReceivedDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(filterCaptor.getValue().getMaxReceivedDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    // N08 (31/08/2026) - xem javadoc XINYKIEN_DOCUMENT_TYPE o ViewQueryService. 4 test dai dien: 1
    // nhom "cho" (user_process), 1 nhom "da" (user_processed), 1 bien the *bch (co ky/opinion flag
    // rieng), 1 truong hop NGOAI LE dachuyenchoykien (khong co dieu kien co).
    @Test
    void execute_chochoykien_shouldQueryXinYkienDocumentTypeByUserProcessAndTvubFlag() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(xinYKienDocumentRepository.findByVbdenOrgView(eq("vbden_xinykien"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chochoykien");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getOrgFieldNames()).containsExactly("user_process");
        assertThat(filterCaptor.getValue().getOrgValue()).isEqualTo("username_test");
        assertThat(filterCaptor.getValue().getIncludeFieldValues()).containsEntry("is_tvub_opinion", List.of("true", "1"));
    }

    @Test
    void execute_dachoykienbch_shouldQueryXinYkienDocumentTypeByUserProcessedAndBchFlag() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(xinYKienDocumentRepository.findByVbdenOrgView(eq("vbden_xinykien"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dachoykienbch");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getOrgFieldNames()).containsExactly("user_processed");
        assertThat(filterCaptor.getValue().getIncludeFieldValues()).containsEntry("is_bch_opinion", List.of("true", "1"));
    }

    @Test
    void execute_dachuyenchoykien_shouldQueryXinYkienDocumentTypeByUserDeliveredWithoutOpinionFlag() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(xinYKienDocumentRepository.findByVbdenOrgView(eq("vbden_xinykien"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dachuyenchoykien");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getOrgFieldNames()).containsExactly("user_delivered");
        assertThat(filterCaptor.getValue().getIncludeFieldValues()).isNullOrEmpty();
    }

    @Test
    void execute_dachuyenchoykienttdu_shouldQueryXinYkienDocumentTypeByUserDeliveredAndTtduFlag() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(xinYKienDocumentRepository.findByVbdenOrgView(eq("vbden_xinykien"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dachuyenchoykienttdu");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        assertThat(filterCaptor.getValue().getOrgFieldNames()).containsExactly("user_delivered");
        assertThat(filterCaptor.getValue().getIncludeFieldValues()).containsEntry("is_ttdu_opinion", List.of("true", "1"));
    }

    @Test
    void execute_chochoykien_withoutUsername_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chochoykien");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(xinYKienDocumentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    @Test
    void execute_captrengiao_whenCurrentUserHasNoIdentifier_shouldReturnEmptyPageWithoutQuerying() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("captrengiao");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    @Test
    void execute_captrengiao_whenCurrentUserNotFound_shouldReturnEmptyPageWithoutQuerying() {
        // Mac dinh Mockito tra Optional.empty() cho method chua stub tra ve Optional - khong can
        // stub currentUserProvider truc tiep nua vi resolveCurrentIdentifier() gio di qua
        // resolveIdentifierByLevelUseCase (mock rieng), khong goi currentUserProvider truc tiep.
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("captrengiao");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // N13 (VBDen - han xu ly theo to chuc): Slice 4, doc dung tu docs/views_domino/view_vanbanden.dxl.

    @Test
    void execute_phaixuly_cuatochuc_shouldExcludeCompletedStatusesAndRequireDeadlineOrDocumentReply() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        BusinessDocument document = newDocument("Van ban con phai xu ly", "vbden", "{}");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("phaixuly_cuatochuc");
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban con phai xu ly");
        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly(
            "identifier", "unit_process", "unit_process_registered", "unit_coordinate",
            "unit_coordinate_registered", "unit_to_read", "unit_to_read_registered", "unit_to_read_manage");
        assertThat(filter.getExcludeFieldValues()).containsEntry("status", List.of("3", "4"));
        assertThat(filter.isRequireDeadlineOrDocumentReply()).isTrue();
    }

    // dxl 923-949: deadline_date < today (KHONG phai status="1" nhu suy doan Slice 3).
    @Test
    void execute_quahan_cuatochuc_shouldQueryByDeadlineBeforeTodayExcludingGiayMoi() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("quahan_cuatochuc");

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getExcludeFieldValues()).containsEntry("status", List.of("3", "4"));
        assertThat(filter.getExcludeFieldValues()).containsEntry("document_type", List.of("Giấy mời"));
        assertThat(filter.getMinDeadline()).isNull();
        assertThat(filter.getMaxDeadline()).isEqualTo(LocalDate.now().minusDays(1));
    }

    // dxl 980-1007: status="3", khoa = identifier_creator (khac 4 view "identifier" o tren).
    @Test
    void execute_daxulyQuahan_cuatochuc_shouldQueryByIdentifierCreatorAndStatus3() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("daxuly_quahan_cuatochuc");

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames().get(0)).isEqualTo("identifier_creator");
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of("3"));
    }

    @Test
    void execute_daxulyDunghan_cuatochuc_shouldQueryByIdentifierCreatorAndStatus4() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("daxuly_dunghan_cuatochuc");

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames().get(0)).isEqualTo("identifier_creator");
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of("4"));
    }

    // dxl 1009-1039: 0<=(deadline_date-today)<=2 (KHONG phai <=3 khong can duoi nhu suy doan Slice 3).
    @Test
    void execute_saptoihan_cuatochuc_shouldQueryByDeadlineFromTodayToTwoDaysLater() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("saptoihan_cuatochuc");

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getMinDeadline()).isEqualTo(LocalDate.now());
        assertThat(filter.getMaxDeadline()).isEqualTo(LocalDate.now().plusDays(2));
        assertThat(filter.getExcludeFieldValues()).containsEntry("document_type", List.of("Giấy mời"));
    }

    @Test
    void execute_denhan_cuatochuc_shouldQueryByDeadlineExactlyToday() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("denhan_cuatochuc");

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getMinDeadline()).isEqualTo(LocalDate.now());
        assertThat(filter.getMaxDeadline()).isEqualTo(LocalDate.now());
    }

    @Test
    void execute_quahan_cuatochuc_whenCurrentUserHasNoIdentifier_shouldReturnEmptyPageWithoutQuerying() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("quahan_cuatochuc");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // Slice 6 (N05/N12/N22/N28) - doc tu docs/views_domino/view_vanbanden.dxl.

    // dxl 253-278: user_process="" & identifier_creator!="" & status!="3" & status!="4".
    @Test
    void execute_chophanxuly_shouldRequireEmptyUserProcessAndIdentifierCreatorKey() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        BusinessDocument document = newDocument("Van ban cho phan xu ly", "vbden", "{}");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chophanxuly");
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("identifier_creator");
        assertThat(filter.getExcludeFieldValues()).containsEntry("status", List.of("3", "4"));
        assertThat(filter.getRequireEmptyFieldName()).isEqualTo("user_process");
    }

    @Test
    void execute_chophanxuly_whenCurrentUserHasNoIdentifier_shouldReturnEmptyPageWithoutQuerying() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chophanxuly");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // dxl 1249-1276: Form="DocumentIn" (khong dieu kien khac), khoa = 8-field union theo identifier_creator.
    @Test
    void execute_cuatochuc_shouldQueryByIdentifierCreatorUnionWithoutStatusFilter() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("cuatochuc");

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames().get(0)).isEqualTo("identifier_creator");
        assertThat(filter.getIncludeFieldValues()).isNull();
        assertThat(filter.getExcludeFieldValues()).isNull();
    }

    // dxl 1278-1305: Form="DocumentIn" & (status="3"|status="4"), khoa = 8-field union theo identifier.
    @Test
    void execute_daxulyCuatochuc_shouldQueryByIdentifierUnionAndStatus3Or4() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("daxuly_cuatochuc");

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames().get(0)).isEqualTo("identifier");
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of("3", "4"));
    }

    // dxl 1503-1536: Form="DocumentIn" & unit_discard!="".
    @Test
    void execute_khongvaoso_shouldQueryByUnitDiscardKey() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        BusinessDocument document = newDocument("Van ban khong vao so", "vbden", "{}");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("khongvaoso");
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(filterCaptor.getValue().getOrgFieldNames()).containsExactly("unit_discard");
    }

    // Slice 5 (N06 bien the/N07/N10/N11 - hang doi ca nhan don gian, doc tu
    // docs/views_domino/view_vanbanden.dxl). An ninh: TRUST username tu request.getKeys() (giong
    // vwDocument_ChoXuLy da co) - khac quyet dinh cua N03/N13 (tu resolve identifier, khong tin client).

    @Test
    void execute_chophanphoi_shouldQueryByUserDistributorContainment() {
        BusinessDocument document = newDocument("Van ban cho phan phoi", "vbden", "{}");
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_distributor")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chophanphoi");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban cho phan phoi");
    }

    // 31/08/2026 (port nốt vbden - docs/analysis/02-checklist-port-vbden-con-lai.md muc 1.2):
    // "daphanphoi" (dxl 2052-2077) - cung cong thuc ca nhan nhu chophanphoi, chi khac field
    // user_distributed (da phan phoi XONG) thay vi user_distributor.
    @Test
    void execute_daphanphoi_shouldQueryByUserDistributedContainment() {
        BusinessDocument document = newDocument("Van ban da phan phoi", "vbden", "{}");
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_distributed")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("daphanphoi");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban da phan phoi");
    }

    // 09/09/2026 (port not SoLuongCanXuLy): "dennoibo_choxuly" (dxl moi
    // view_vanbanden_dennoibo_choxuly.dxl) - cung cong thuc ca nhan nhu chophanphoi, field user_receiver.
    @Test
    void execute_dennoiboChoxuly_shouldQueryByUserReceiverContainment() {
        BusinessDocument document = newDocument("Van ban den noi bo cho xu ly", "vbden", "{}");
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_receiver")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dennoibo_choxuly");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban den noi bo cho xu ly");
    }

    // 09/09/2026 (port not SoLuongCanXuLy): "phutrach_chuaxem" (dxl view_vanbanden.dxl 2559-2584) -
    // cung cong thuc ca nhan nhu chophanphoi, field user_manage.
    @Test
    void execute_phutrachChuaxem_shouldQueryByUserManageContainment() {
        BusinessDocument document = newDocument("Van ban phu trach chua xem", "vbden", "{}");
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_manage")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("phutrach_chuaxem");
        request.setKeys(List.of("username_test"));
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban phu trach chua xem");
    }

    // Slice 7 (tim kiem/loc): keyword+columns -> goi overload co keyword cua findByAnyFieldContains.
    @Test
    void execute_chophanphoi_withKeyword_shouldCallKeywordOverloadOfFindByAnyFieldContains() {
        BusinessDocument document = newDocument("Van ban cho phan phoi co tu khoa", "vbden", "{}");
        when(documentRepository.findByAnyFieldContains(
                eq("vbden"), eq(List.of("user_distributor")), eq("username_test"), eq("hop dong"), eq(List.of("subject")), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chophanphoi");
        request.setKeys(List.of("username_test"));
        request.setKeyword("hop dong");
        request.setColumns(List.of("subject"));

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        verify(documentRepository, never())
            .findByAnyFieldContains(anyString(), eq(List.of("user_distributor")), anyString(), any());
    }

    @Test
    void execute_phoihop_shouldQueryByUserCoordinateContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_coordinate")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("phoihop");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_coordinate")), eq("username_test"), any());
    }

    @Test
    void execute_daphoihop_shouldQueryByUserCoordinatedContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_coordinated")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("daphoihop");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_coordinated")), eq("username_test"), any());
    }

    @Test
    void execute_xemdebiet_shouldQueryByUserToReadContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_to_read")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("xemdebiet");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_to_read")), eq("username_test"), any());
    }

    @Test
    void execute_daxemdebiet_shouldQueryByUserToReadedContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_to_readed")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("daxemdebiet");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_to_readed")), eq("username_test"), any());
    }

    @Test
    void execute_theodoi_shouldQueryByUserTrackContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_track")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("theodoi");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_track")), eq("username_test"), any());
    }

    @Test
    void execute_datheodoi_shouldQueryByUserTrackedContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_tracked")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("datheodoi");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_tracked")), eq("username_test"), any());
    }

    // D.44 (01/09/2026, "tim kiem nang cao"): FE mac dinh (sortActive="") gui viewname NAY, khong
    // phai "theodoi" - phai co alias cung dieu kien, khong thi man mac dinh vo.
    @Test
    void execute_theodoiSortThoihan_shouldAliasToSameConditionAsTheodoi() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_track")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("theodoi_sort_thoihan");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_track")), eq("username_test"), any());
    }

    // D.44: "tim kiem nang cao" - search non-empty phai di qua overload co VbdenOrgViewFilter, khong
    // phai overload cu (khong loc them).
    @Test
    void execute_theodoi_withAdvancedSearch_shouldQueryWithAdvancedFilter() {
        ArgumentCaptor<com.td.application.documents.VbdenOrgViewFilter> filterCaptor =
            ArgumentCaptor.forClass(com.td.application.documents.VbdenOrgViewFilter.class);
        when(documentRepository.findByAnyFieldContains(
                eq("vbden"), eq(List.of("user_track")), eq("username_test"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("theodoi");
        request.setKeys(List.of("username_test"));
        Map<String, Object> search = new java.util.LinkedHashMap<>();
        search.put("status", "2");
        search.put("unit_in_charge", "UBND tinh");
        search.put("unit_process", "H56.09");
        search.put("user_process", "nguyenvana;tranthib");
        search.put("received_date", List.of("01/08/2026", "31/08/2026"));
        request.setSearch(search);

        service.execute("vbden", request);

        var filter = filterCaptor.getValue();
        assertThat(filter).isNotNull();
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of("2"));
        assertThat(filter.getContainsAnyFieldValues()).containsEntry("unit_in_charge", List.of("UBND tinh"));
        assertThat(filter.getContainsAnyFieldValues()).containsEntry("unit_process", List.of("H56.09"));
        assertThat(filter.getContainsAnyFieldValues()).containsEntry("user_process", List.of("nguyenvana", "tranthib"));
        assertThat(filter.getMinReceivedDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 1));
        assertThat(filter.getMaxReceivedDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 31));
        // findByAnyFieldContains 4-tham-so (khong co filter) KHONG duoc goi khi co search.
        verify(documentRepository, never()).findByAnyFieldContains(any(), any(), any(), any(Pageable.class));
    }

    @Test
    void execute_theodoi_withUnrecognizedSearchFields_shouldIgnoreThemAndNotFilter() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_track")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("theodoi");
        request.setKeys(List.of("username_test"));
        Map<String, Object> search = new java.util.LinkedHashMap<>();
        // 09/09/2026: "priority" GIO DA duoc nhan dien (mo rong cutover dashboard toanbovanban) - doi
        // sang field VAN con la (document_internet/delivery_method/document_register_id - chi dung o
        // path bao cao khac cua CollapseVBden.jsx, chua co bang chung can cho theodoi/toanbovanban).
        search.put("document_internet", "1");
        request.setSearch(search);

        service.execute("vbden", request);

        // Khong field nao nhan dien duoc -> buildAdvancedSearchFilter tra null -> di qua overload cu.
        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_track")), eq("username_test"), any(Pageable.class));
    }

    // N20 (07/09/2026, "vanbancantheodoi") - dxl 2178-2208: selection CHI "Form=DocumentIn", khoa qua
    // cot categorized "tasks" - key co the la identifier (tab "vanbancantheodoi") HOAC "G13" co dinh
    // (tab "vanbancuabocantheodoi" - FE tu doi lai viewname truoc khi gui, xem
    // ItemsListNhiemVuCanTheoDoi.jsx).
    @Test
    void execute_vanbancantheodoi_shouldQueryByTasksContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("tasks")), eq("H05.01"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vanbancantheodoi");
        request.setKeys(List.of("H05.01"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("tasks")), eq("H05.01"), any());
    }

    @Test
    void execute_vanbancantheodoi_withFixedG13Key_shouldQueryByTasksContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("tasks")), eq("G13"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vanbancantheodoi");
        request.setKeys(List.of("G13"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("tasks")), eq("G13"), any());
    }

    @Test
    void execute_vanbancantheodoi_withReceivedDateSearch_shouldQueryWithAdvancedFilter() {
        ArgumentCaptor<com.td.application.documents.VbdenOrgViewFilter> filterCaptor =
            ArgumentCaptor.forClass(com.td.application.documents.VbdenOrgViewFilter.class);
        when(documentRepository.findByAnyFieldContains(
                eq("vbden"), eq(List.of("tasks")), eq("H05.01"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vanbancantheodoi");
        request.setKeys(List.of("H05.01"));
        Map<String, Object> search = new java.util.LinkedHashMap<>();
        search.put("received_date", List.of("01/08/2026", "31/08/2026"));
        request.setSearch(search);

        service.execute("vbden", request);

        var filter = filterCaptor.getValue();
        assertThat(filter).isNotNull();
        assertThat(filter.getMinReceivedDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 1));
        assertThat(filter.getMaxReceivedDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 31));
    }

    @Test
    void execute_dachuyenphoihop_shouldQueryByUserCoordinatedMovementContainment() {
        when(documentRepository.findByAnyFieldContains(
                eq("vbden"), eq(List.of("user_coordinated_movement")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dachuyenphoihop");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(
            eq("vbden"), eq(List.of("user_coordinated_movement")), eq("username_test"), any());
    }

    @Test
    void execute_dachuyenxuly_shouldQueryByUserDeliveredContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_delivered")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dachuyenxuly");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_delivered")), eq("username_test"), any());
    }

    // dxl 196-221: user_processed!="" - CHINH LA vwDocument_DaXuLy da co, phai tai dung nguyen ham cu.
    @Test
    void execute_daketthucxuly_shouldReuseProcessedForUser() {
        when(documentRepository.findProcessedForUser(eq("vbden"), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("daketthucxuly");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findProcessedForUser(eq("vbden"), eq("username_test"), any());
    }

    @Test
    void execute_dangxuly_shouldQueryByUserProcessingAndStatusInProgress() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dangxuly");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_processing");
        assertThat(filter.getOrgValue()).isEqualTo("username_test");
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of("1", "2"));
    }

    @Test
    void execute_daketthucxulyquahan_shouldQueryByUserProcessedAndStatus3() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("daketthucxulyquahan");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_processed");
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of("3"));
    }

    @Test
    void execute_dachuyenxulyquahan_shouldQueryByUserDeliveredAndStatus1Or3() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dachuyenxulyquahan");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_delivered");
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of("1", "3"));
    }

    @Test
    void execute_chutriquahan_shouldQueryByUserProcessAndStatus1Or3() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chutriquahan");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_process");
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of("1", "3"));
    }

    @Test
    void execute_vanbantralai_shouldQueryByUserReturnedContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_returned")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vanbantralai");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_returned")), eq("username_test"), any());
    }

    @Test
    void execute_dathuhoi_shouldQueryByUserRecalledContainment() {
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(List.of("user_recalled")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dathuhoi");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        verify(documentRepository).findByAnyFieldContains(eq("vbden"), eq(List.of("user_recalled")), eq("username_test"), any());
    }

    // "toanbovanban_vbden_canhan" (dxl 1220-1247/2504-2530) - xem javadoc VIEW_VBDEN_CUACANHAN.

    @Test
    void execute_cuacanhan_shouldQueryByUnionOfNinePersonalFields() {
        BusinessDocument document = newDocument("Van ban toan bo ca nhan", "vbden", "{}");
        List<String> expectedFields = List.of(
            "user_process", "user_coordinate", "user_track", "user_to_read", "user_processed",
            "user_coordinateed", "user_tracked", "user_to_readed", "user_delivered");
        when(documentRepository.findByAnyFieldContains(eq("vbden"), eq(expectedFields), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("cuacanhan");
        request.setKeys(List.of("username_test"));

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban toan bo ca nhan");
    }

    @Test
    void execute_cuacanhan_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("cuacanhan");
        request.setKeys(List.of());

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByAnyFieldContains(anyString(), any(), anyString(), any());
    }

    @Test
    void execute_sapdenhanCuacanhan_shouldQueryByDeadlineFromTodayToTwoDaysLaterOnPersonalFields() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("sapdenhan_cuacanhan");
        request.setKeys(List.of("username_test"));

        service.execute("vbden", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_process", "user_coordinate", "user_processing");
        assertThat(filter.getOrgValue()).isEqualTo("username_test");
        assertThat(filter.getMinDeadline()).isEqualTo(LocalDate.now());
        assertThat(filter.getMaxDeadline()).isEqualTo(LocalDate.now().plusDays(2));
        assertThat(filter.getExcludeFieldValues()).containsEntry("document_type", List.of("Giấy mời"));
        assertThat(filter.getExcludeFieldValues()).containsEntry("status", List.of("3", "4"));
    }

    @Test
    void execute_sapdenhanCuacanhan_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("sapdenhan_cuacanhan");
        request.setKeys(List.of());

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    @Test
    void execute_phoihop_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("phoihop");
        request.setKeys(List.of());

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByAnyFieldContains(anyString(), any(), anyString(), any());
    }

    @Test
    void execute_dangxuly_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("dangxuly");
        request.setKeys(List.of());

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // Slice 10 (chutri_group - "Cho xu ly" xem theo nhom co quan ban hanh, dxl 2319-2348).

    @Test
    void execute_chutriGroup_whenNoCurrentUser_shouldReturnEmptyPageWithoutQuerying() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chutri_group");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isZero();
        verify(documentRepository, never()).findPendingForUser(anyString(), anyString(), any());
    }

    @Test
    void execute_chutriGroup_shouldGroupPendingDocumentsByUnitInChargeDescending() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        BusinessDocument docA1 = newDocument("Van ban So A - 1", "vbden", "{\"unit_in_charge\":\"So A\"}");
        BusinessDocument docA2 = newDocument("Van ban So A - 2", "vbden", "{\"unit_in_charge\":\"So A\"}");
        BusinessDocument docB1 = newDocument("Van ban So B - 1", "vbden", "{\"unit_in_charge\":\"So B\"}");
        when(documentRepository.findPendingForUser(eq("vbden"), eq("vanthu01"), any()))
            .thenReturn(new PageImpl<>(List.of(docA1, docA2, docB1), PageRequest.of(0, 2000), 3));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chutri_group");
        request.setItems("getall");

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(2); // 2 nhom, khong phai 3 document
        assertThat(result.getItems()).hasSize(2);
        // "So B" > "So A" theo thu tu chuoi giam dan (dxl: unit_in_charge sort='descending').
        assertThat(result.getItems().get(0)).containsEntry("name", "So B");
        assertThat(result.getItems().get(0)).containsEntry("count", 1);
        assertThat(result.getItems().get(1)).containsEntry("name", "So A");
        assertThat(result.getItems().get(1)).containsEntry("count", 2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groupADocs = (List<Map<String, Object>>) result.getItems().get(1).get("documents");
        assertThat(groupADocs).hasSize(2);
        assertThat(groupADocs.get(0)).containsEntry("title", "Van ban So A - 1");
    }

    @Test
    void execute_chutriGroup_shouldPaginateByGroupCountNotDocumentCount() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        BusinessDocument docA = newDocument("A", "vbden", "{\"unit_in_charge\":\"So A\"}");
        BusinessDocument docB = newDocument("B", "vbden", "{\"unit_in_charge\":\"So B\"}");
        BusinessDocument docC = newDocument("C", "vbden", "{\"unit_in_charge\":\"So C\"}");
        when(documentRepository.findPendingForUser(eq("vbden"), eq("vanthu01"), any()))
            .thenReturn(new PageImpl<>(List.of(docA, docB, docC), PageRequest.of(0, 2000), 3));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chutri_group");
        request.setPageSize(2);
        request.setPageNumber(1);

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(3);
        assertThat(result.getItems()).hasSize(2); // trang 1: 2/3 nhom (So C, So B - giam dan)
        assertThat(result.getItems().get(0)).containsEntry("name", "So C");
        assertThat(result.getItems().get(1)).containsEntry("name", "So B");
    }

    @Test
    void execute_chutriGroup_withKeyword_shouldCallKeywordOverloadBeforeGrouping() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        BusinessDocument docA = newDocument("Van ban", "vbden", "{\"unit_in_charge\":\"So A\",\"subject\":\"hop dong\"}");
        when(documentRepository.findPendingForUser(
                eq("vbden"), eq("vanthu01"), eq("hop dong"), eq(List.of("subject")), any()))
            .thenReturn(new PageImpl<>(List.of(docA), PageRequest.of(0, 2000), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("chutri_group");
        request.setKeyword("hop dong");
        request.setColumns(List.of("subject"));

        var result = service.execute("vbden", request);

        assertThat(result.getTotalItems()).isEqualTo(1);
        verify(documentRepository, never()).findPendingForUser(anyString(), anyString(), any());
    }

    // DT-002 Slice dau (tai lieu phan tich 04 muc 12) - xem javadoc
    // ViewQueryService#VIEW_DUTHAO_USER_CREATOR ve pham vi/ly do hoan cac view con lai.
    @Test
    void execute_duthaoVwUserCreator_shouldQueryByUserCreatorContainmentWithoutStatusFilter() {
        when(duThaoDocumentRepository.findByAnyFieldContains(eq("duthao"), eq(List.of("user_creator")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Creator");
        request.setKeys(List.of("username_test"));

        service.execute("duthao", request);

        verify(duThaoDocumentRepository).findByAnyFieldContains(eq("duthao"), eq(List.of("user_creator")), eq("username_test"), any());
    }

    @Test
    void execute_duthaoVwUserInvoked_shouldQueryByUserCreatorContainmentSameAsVwUserCreator() {
        when(duThaoDocumentRepository.findByAnyFieldContains(eq("duthao"), eq(List.of("user_creator")), eq("username_test"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Invoked");
        request.setKeys(List.of("username_test"));

        service.execute("duthao", request);

        verify(duThaoDocumentRepository).findByAnyFieldContains(eq("duthao"), eq(List.of("user_creator")), eq("username_test"), any());
    }

    @Test
    void execute_duthaoVwCreatorDrafting_shouldQueryByUserCreatorAndStatus0() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(duThaoDocumentRepository.findByVbdenOrgView(eq("duthao"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_Drafting");
        request.setKeys(List.of("username_test"));

        service.execute("duthao", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_creator");
        assertThat(filter.getOrgValue()).isEqualTo("username_test");
        // Bugfix 24/08/2026 (bao loi nguoi dung: "vwCreator_Drafting -> chua co du lieu"), cap nhat
        // thu 2 cung ngay (DuThaoStatus.DRAFTING nay la "0", dung ma so Domino - xem javadoc
        // DuThaoStatus): assert qua constant, khong hardcode chuoi, de khong phai sua lai test nay
        // moi khi gia tri DuThaoStatus.DRAFTING doi.
        assertThat(filter.getIncludeFieldValues()).containsEntry("status", List.of(DuThaoStatus.DRAFTING));
    }

    @Test
    void execute_duthaoVwCreatorDrafting_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_Drafting");
        request.setKeys(List.of());

        var result = service.execute("duthao", request);

        assertThat(result.getTotalItems()).isZero();
        verify(duThaoDocumentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // 09/09/2026 (cutover dashboard tu Domino qlvb/tichhop/TimKiem?databasetype=duthao - bo qua
    // Elasticsearch, xac nhan BE_THANHHOA form=draft_document GIU org-scope day du - xem javadoc
    // VIEW_DUTHAO_TOANBO_TOCHUC): identifier tu resolveCurrentIdentifier() (server-resolved, KHONG
    // dung request.getKeys() - route nay FE khong gui keys), hop nhat du 8 tieu chi CollapseVBduthao.jsx.
    @Test
    void execute_duthaoToanboTochuc_withAdvancedSearch_shouldQueryOrgScopeWithMergedFilter() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05"));
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(duThaoDocumentRepository.findByVbdenOrgView(eq("duthao"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("toanbotochuc");
        Map<String, Object> search = new java.util.LinkedHashMap<>();
        search.put("document_type", "Công văn");
        search.put("sector", "Khoa học công nghệ");
        search.put("unit_publish", "H05");
        search.put("unit_in_charge", "H05.01");
        search.put("creator", "Nguyen Van A");
        search.put("signer", "Tran Thi B");
        search.put("status", "2");
        search.put("create_date", List.of("01/08/2026", "31/08/2026"));
        request.setSearch(search);

        service.execute("duthao", request);

        var filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames())
            .containsExactly("unit_publish_identifier", "unit_identifier", "unit_in_charge_identifier");
        assertThat(filter.getOrgValue()).isEqualTo("H05");
        assertThat(filter.getIncludeFieldValues())
            .containsEntry("document_type", List.of("Công văn"))
            .containsEntry("sector", List.of("Khoa học công nghệ"))
            .containsEntry("unit_publish", List.of("H05"))
            .containsEntry("unit_in_charge", List.of("H05.01"))
            .containsEntry("creator", List.of("Nguyen Van A"))
            .containsEntry("signer", List.of("Tran Thi B"))
            .containsEntry("status", List.of("2"));
        assertThat(filter.getMinCreatedOn()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(filter.getMaxCreatedOn()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void execute_duthaoToanboTochuc_withoutResolvedIdentifier_shouldReturnEmptyPageWithoutQuerying() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("toanbotochuc");

        var result = service.execute("duthao", request);

        assertThat(result.getTotalItems()).isZero();
        verify(duThaoDocumentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // 27/08/2026 (port route FE "vbdi/xuly/chocapkyso"): CUNG ten viewname "vwCreator_Drafting"
    // nhung o module vbdi la 1 view Domino HOAN TOAN KHAC (dxl view_vanbandi.dxl dong ~276-306,
    // "Van ban di cho vao so") - dispatch phai phan nhanh dung theo dbType, xem javadoc case tai
    // ViewQueryService#execute.
    @Test
    void execute_vbdiVwCreatorDrafting_shouldQueryWaitingForRegistrationByResolvedIdentifier() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("PHONG_TH"));
        VbdiDocument document = new VbdiDocument("Van ban cho vao so", "vbdi", VbdiStatus.DRAFT, null, null, null, null);
        document.replaceDynamicFields("{}");
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findWaitingForRegistrationByIdentifier(eq("vbdi"), eq(VbdiStatus.DRAFT), eq("PHONG_TH"), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_Drafting");
        request.setItems("getall");

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban cho vao so");
        // An ninh: KHONG dung request.getKeys() do client gui - luon resolve server-side.
        verify(duThaoDocumentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // Bugfix 28/08/2026 (nguoi dung bao "chua thay hien thi du lieu", dieu tra ra van ban di tao
    // qua ChuyenThanhVanBanDiUseCase luon thieu "subject" trong dynamic_fields - du thao dong bo
    // subject vao COT title, khong vao dynamic_fields, nen ChuyenThanhVanBanDiUseCase copy nguyen
    // dynamic fields se khong bao gio co "subject"). toFilteredDocumentFields(VbdiDocument,...) phai
    // fallback ve document.getTitle() khi dynamic_fields khong co "subject" - dung nhu
    // GetVbdiDetailUseCase da lam.
    @Test
    void execute_vbdiVwCreatorDrafting_withoutSubjectField_shouldFallBackToTitle() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05.01"));
        VbdiDocument document = new VbdiDocument("Van ban moi", "vbdi", VbdiStatus.DRAFT, null, null, null, null);
        document.replaceDynamicFields("{\"unit_publish_identifier\":\"H05.01\"}");
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findWaitingForRegistrationByIdentifier(eq("vbdi"), eq(VbdiStatus.DRAFT), eq("H05.01"), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_Drafting");
        request.setItems("getall");

        var result = service.execute("vbdi", request);

        assertThat(result.getItems().get(0)).containsEntry("subject", "Van ban moi");
    }

    @Test
    void execute_vbdiVwCreatorDrafting_withUnresolvedIdentifier_shouldReturnEmptyPageWithoutQuerying() {
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_Drafting");

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never()).findWaitingForRegistrationByIdentifier(anyString(), anyString(), anyString(), any());
    }

    // 11/09/2026 (port "vbdi/thungrac/thungrac" - SUA LAI sau khi nguoi dung bao "FE van goi
    // qlvb/vanbandi/GetView" - xem javadoc ViewQueryService#VIEW_VBDI_TRASH/queryVbdiTrash /
    // VbdiDocumentRepository#findTrashedByUsernameOrDeleter). Viewname THAT FE gui la
    // "vwCreator_DocumentOut_Delete" (khong phai "thungrac" - do la ten route/actiontype, khong phai
    // dom_view_name), khoa theo USERNAME hien tai (khong phai identifier).
    @Test
    void execute_vbdiThungrac_shouldQueryTrashedByCurrentUsername() {
        AppUser user = newUser("vanthu01", "Van Thu", "vanthu01@test.vn");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(user));
        VbdiDocument document = new VbdiDocument("Van ban da xoa", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        document.replaceDynamicFields("{}");
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findTrashedByUsernameOrDeleter(eq("vbdi"), eq("vanthu01"), eq(user.getId()), any(), any(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_Delete");
        request.setItems("getall");

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban da xoa");
    }

    @Test
    void execute_vbdiThungrac_withoutCurrentUser_shouldReturnEmptyPageWithoutQuerying() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_Delete");

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never()).findTrashedByUsernameOrDeleter(
            anyString(), anyString(), any(), any(), any(), any());
    }

    // 28/08/2026 (port route FE "vbdi/xuly/cq_toanboso" + 3 route anh em cung dom_view_name
    // "vwCreator_DocumentOut_TextBook" - xem javadoc ViewQueryService#queryVbdiDocumentOutTextBook).
    @Test
    void execute_vbdiDocumentOutTextBook_shouldQueryByOwnIdentifier() {
        AppUser user = newUser("user1", "User Mot", "user1@test.vn");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("user1"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("user1")).thenReturn(Optional.of(user));
        UserOrganizationProfileDto profile = new UserOrganizationProfileDto();
        profile.setIdentifiers("H05.01;H05;G10;");
        when(getUserOrganizationProfileUseCase.buildProfileForUser(user)).thenReturn(profile);

        VbdiDocument document = new VbdiDocument("Van ban da ban hanh", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        document.replaceDynamicFields("{\"unit_publish_identifier\":\"H05\"}");
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findIssuedByPublishOrCreatorIdentifier(eq("vbdi"), eq(VbdiStatus.ISSUED), eq("H05"), any(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_TextBook");
        request.setKeys(List.of("H05"));
        request.setItems("getall");

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Van ban da ban hanh");
    }

    @Test
    void execute_vbdiDocumentOutTextBook_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_TextBook");
        request.setKeys(List.of());

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never()).findIssuedByPublishOrCreatorIdentifier(anyString(), anyString(), anyString(), any(), any());
    }

    // An ninh: identifier client gui PHAI la 1 to tien THAT cua chinh user hien tai - khong tin mu
    // request.getKeys() (rui ro spoof identifier to chuc khac) - xem javadoc isOwnIdentifier.
    @Test
    void execute_vbdiDocumentOutTextBook_withIdentifierNotOwnedByUser_shouldReturnEmptyPageWithoutQuerying() {
        AppUser user = newUser("user1", "User Mot", "user1@test.vn");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("user1"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("user1")).thenReturn(Optional.of(user));
        UserOrganizationProfileDto profile = new UserOrganizationProfileDto();
        profile.setIdentifiers("H05.01;H05;G10;");
        when(getUserOrganizationProfileUseCase.buildProfileForUser(user)).thenReturn(profile);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_TextBook");
        request.setKeys(List.of("TO_CHUC_KHAC"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never()).findIssuedByPublishOrCreatorIdentifier(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void execute_vbdiDocumentOutTextBook_withUnknownUser_shouldReturnEmptyPageWithoutQuerying() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("ghost"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("ghost")).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_TextBook");
        request.setKeys(List.of("H05"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never()).findIssuedByPublishOrCreatorIdentifier(anyString(), anyString(), anyString(), any(), any());
    }

    // Yeu cau nguoi dung 28/08/2026 ("kiem trung goi toi view vwDocumentOut_kiemtrungvb, port sang
    // JAVA" - ControllModal.jsx#kiemtrungvanban).
    @Test
    void execute_vbdiKiemTrungVanBan_shouldQueryByCompositeKey() {
        VbdiDocument document = new VbdiDocument("Cong van trung so", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        document.replaceDynamicFields("{\"document_register_id\":\"reg-1\",\"document_number\":\"601\"}");
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 10), 1);
        when(vbdiDocumentRepository.findByKiemTrungKey(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq("reg-1|601|2026"), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocumentOut_kiemtrungvb");
        request.setKeys(List.of("reg-1|601|2026"));
        request.setItems("getall");

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Cong van trung so");
    }

    @Test
    void execute_vbdiKiemTrungVanBan_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocumentOut_kiemtrungvb");
        request.setKeys(List.of());

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never()).findByKiemTrungKey(anyString(), anyString(), anyString(), any());
    }

    // 29/08/2026 (yeu cau nguoi dung "port cac tab trong vbdi/xuly/cq_toanboso, chuc nang chon so va
    // tim kiem"): xac nhan queryVbdiDocumentOutTextBook ("Toan bo") truoc do BO SOT keyword/columns -
    // gio phai chuyen dung vao VbdiDocumentOutFilter.
    @Test
    void execute_vbdiDocumentOutTextBook_withKeyword_shouldForwardKeywordToFilter() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findIssuedByPublishOrCreatorIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_TextBook");
        request.setKeys(List.of("H05"));
        request.setKeyword("601");
        request.setColumns(List.of("code_full", "subject"));

        service.execute("vbdi", request);

        assertThat(filterCaptor.getValue().getKeyword()).isEqualTo("601");
        assertThat(filterCaptor.getValue().getKeywordColumns()).containsExactly("code_full", "subject");
    }

    // 4 tab theo ngay (dxl view_vanbandi.dxl - homqua/vwCreator_DocumentOut_ToDay/trongtuan/
    // trongthang, xem javadoc ViewQueryService#queryVbdiDocumentOutByDateRange): formula dxl viet
    // chet 1 ngay cu the nhung duoc sua lai dinh ky (bang chung <modified> sat ngay export) - port
    // dung Y DINH (tuong doi theo LocalDate.now()), khong copy literal ngay trong dxl.
    @Test
    void execute_vbdiDocumentOutToday_shouldFilterPromulgationDateToTodayOnly() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findIssuedByCreatorOrUnitIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_ToDay");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        LocalDate today = LocalDate.now();
        assertThat(filterCaptor.getValue().getPromulgationDateFrom()).isEqualTo(today);
        assertThat(filterCaptor.getValue().getPromulgationDateTo()).isEqualTo(today);
    }

    @Test
    void execute_vbdiDocumentOutHomqua_shouldFilterPromulgationDateToYesterdayOnly() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findIssuedByCreatorOrUnitIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("homqua");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThat(filterCaptor.getValue().getPromulgationDateFrom()).isEqualTo(yesterday);
        assertThat(filterCaptor.getValue().getPromulgationDateTo()).isEqualTo(yesterday);
    }

    @Test
    void execute_vbdiDocumentOutTrongtuan_shouldFilterPromulgationDateToCurrentIsoWeek() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findIssuedByCreatorOrUnitIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("trongtuan");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate sunday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
        assertThat(filterCaptor.getValue().getPromulgationDateFrom()).isEqualTo(monday);
        assertThat(filterCaptor.getValue().getPromulgationDateTo()).isEqualTo(sunday);
    }

    @Test
    void execute_vbdiDocumentOutTrongthang_shouldFilterPromulgationDateToCurrentMonth() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findIssuedByCreatorOrUnitIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("trongthang");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        LocalDate today = LocalDate.now();
        assertThat(filterCaptor.getValue().getPromulgationDateFrom()).isEqualTo(today.withDayOfMonth(1));
        assertThat(filterCaptor.getValue().getPromulgationDateTo())
            .isEqualTo(today.withDayOfMonth(today.lengthOfMonth()));
    }

    @Test
    void execute_vbdiDocumentOutByDateRange_withIdentifierNotOwnedByUser_shouldReturnEmptyPageWithoutQuerying() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("trongthang");
        request.setKeys(List.of("TO_CHUC_KHAC"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never()).findIssuedByCreatorOrUnitIdentifier(anyString(), anyString(), anyString(), any(), any());
    }

    // 4 tab rieng co quan H05.13 (dxl vbncc_snv/vbbhtn_snv/vbgpld_snv/vbhanhchinh_snv) - chi xac
    // nhan dispatch dung method + identifier, KHONG assert noi dung SQL cua extraPredicateSql (hang
    // so private noi bo, xem javadoc VbdiDocumentOutFilter#extraPredicateSql) de tranh test phu
    // thuoc chi tiet cai dat.
    @Test
    void execute_vbdiSnvNcc_shouldQueryByCreatorOrUnitIdentifierWithNonBlankExtraPredicate() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        VbdiDocument document = new VbdiDocument("Van ban NCC", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findIssuedByCreatorOrUnitIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vbncc_snv");
        request.setKeys(List.of("H05"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(filterCaptor.getValue().getExtraPredicateSql()).isNotBlank();
    }

    @Test
    void execute_vbdiSnvHanhchinh_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vbhanhchinh_snv");
        request.setKeys(List.of());

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never()).findIssuedByCreatorOrUnitIdentifier(anyString(), anyString(), anyString(), any(), any());
    }

    // "Chon so van ban" (dxl vwCreator_DocumentOut_ByTextBook, xem javadoc
    // ViewQueryService#queryVbdiDocumentOutByTextBook) - 2 khoa [identifier, document_register_id]
    // dung THU TU, khac moi view khac trong lop nay (chi 1 khoa).
    @Test
    void execute_vbdiDocumentOutByTextBook_shouldQueryByIdentifierAndDocumentRegisterId() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        VbdiDocument document = new VbdiDocument("Van ban theo so", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findIssuedByPublishOrCreatorIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_ByTextBook");
        request.setKeys(List.of("H05", "REG-UNID-123"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(filterCaptor.getValue().getDocumentRegisterId()).isEqualTo("REG-UNID-123");
    }

    @Test
    void execute_vbdiDocumentOutByTextBook_withoutRegisterKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_ByTextBook");
        request.setKeys(List.of("H05"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never()).findIssuedByPublishOrCreatorIdentifier(anyString(), anyString(), anyString(), any(), any());
    }

    // 29/08/2026 (dot 2 "port toan bo vbdi/xuly/*"): vwCreator_DocumentOut_SendNoResponse (chuaden) -
    // dieu kien status OR giua cot tinh va field dong, dien dat qua extraPredicateSql, khong qua
    // findIssuedByPublishOrCreatorIdentifier (method do luon ep status=1 gia tri).
    @Test
    void execute_vbdiSendNoResponse_shouldExpressCompoundStatusViaExtraPredicate() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findByPublishOrCreatorIdentifier(
                eq("vbdi"), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_SendNoResponse");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        assertThat(filterCaptor.getValue().getExtraPredicateSql())
            .contains("send_agent").contains("send_error").contains("'5'");
    }

    // vwDocumentOut_XuLy (route "choxuly"/"xuly" - macro "$username$" nhu phoihop/xemdebiet).
    @Test
    void execute_vbdiXuLy_shouldResolveUsernameTokenAndQueryUserchuyenxuly() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("user1"));
        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        VbdiDocument document = new VbdiDocument("VB cho xu ly", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByAnyFieldContainsMember(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq(List.of("userchuyenxuly")), memberCaptor.capture(),
                any(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocumentOut_XuLy");
        request.setKeys(List.of("$username$"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(memberCaptor.getValue()).isEqualTo("user1");
    }

    // vwGroup_Reader_by_NhanDeBiet (tab "Chua xem", actiontype "nhandebiet" gui thang username that,
    // KHONG qua macro) - OR 2 field.
    @Test
    void execute_vbdiNhanDeBietUnseen_shouldQueryByTwoFieldsContainsMember() {
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldContainsMember(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("user1"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwGroup_Reader_by_NhanDeBiet");
        request.setKeys(List.of("user1"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue()).containsExactly("coordinator_hide_not_seen_rtext", "userxemdebiet");
    }

    @Test
    void execute_vbdiNhanDeBietSeen_shouldQueryByCoordinatorHideSeenField() {
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldContainsMember(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("user1"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwGroup_Reader_by_NhanDeBiet_Seen");
        request.setKeys(List.of("user1"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue()).containsExactly("coordinator_hide_seen_rtext");
    }

    // vwDocumentOut_BaoCaoChuaXem - dxl goc dung @V3UserName (user dang xem) NGAY TRONG selection ->
    // port thanh excludeMemberFieldName (NOT-CONTAINS log_seen_document) tren CUNG 1 member.
    @Test
    void execute_vbdiBaoCaoChuaXem_shouldExcludeAlreadySeenByCurrentUser() {
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldContainsMember(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("user1"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocumentOut_BaoCaoChuaXem");
        request.setKeys(List.of("user1"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue())
            .containsExactly("unit_publish_identifier", "user_creator", "userchuyenxuly", "coordinator_hide_rtext");
        assertThat(filterCaptor.getValue().getExcludeMemberFieldName()).isEqualTo("log_seen_document");
        assertThat(filterCaptor.getValue().getExtraPredicateSql()).contains("Báo cáo");
    }

    // vwCreator_Drafting_mc (chocapkysomc) - status=DRAFT (khac ISSUED), extraPredicateSql mc_madv!=''.
    @Test
    void execute_vbdiDraftingMc_shouldQueryDraftStatusWithMcMadvNonEmpty() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.DRAFT), fieldsCaptor.capture(), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_Drafting_mc");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue()).containsExactly("unit_publish_identifier", "unit_publish_identifier_other");
        assertThat(filterCaptor.getValue().getExtraPredicateSql()).contains("mc_madv");
    }

    // vwGroup_Reader_by_Signer/vwGroup_by_Draftor (nguoiky/nguoisoan) - khoa theo USERNAME (khong
    // phai identifier to chuc) - KHONG duoc goi isOwnIdentifier (se luon that bai sai voi username).
    @Test
    void execute_vbdiGroupBySigner_shouldQueryByUsernameWithoutOwnIdentifierCheck() {
        // KHONG stub currentUserProvider/appUserRepository - neu code goi nham isOwnIdentifier se
        // NullPointerException hoac tra rong sai, test se fail ro rang.
        VbdiDocument document = new VbdiDocument("VB nguoi ky", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq(List.of("user_signer")), eq("user1"), any(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwGroup_Reader_by_Signer");
        request.setKeys(List.of("user1"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        verify(vbdiDocumentRepository, never()).findByAnyFieldEqualsIdentifier(
            anyString(), anyString(), eq(List.of("user_draft")), anyString(), any(), any());
    }

    @Test
    void execute_vbdiGroupByDraftor_shouldQueryByUserDraftField() {
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq(List.of("user_draft")), eq("user1"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwGroup_by_Draftor");
        request.setKeys(List.of("user1"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
    }

    // 09/09/2026 (port not SoLuongCanXuLy): vwSigner_DocumentOut_WaitingToSend/_Sent (dxl 2814-2884) -
    // bien the "Signer" (khoa USERNAME field user_signer, KHONG qua isOwnIdentifier - cung nhom voi
    // vwGroup_Reader_by_Signer o tren), CHI khac dieu kien phu send_agent.
    @Test
    void execute_vbdiSignerDocumentOutWaitingToSend_shouldFilterBySendAgentZero() {
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq(List.of("user_signer")), eq("user1"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwSigner_DocumentOut_WaitingToSend");
        request.setKeys(List.of("user1"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        assertThat(filterCaptor.getValue().getExtraPredicateSql()).contains("send_agent").contains("'0'");
    }

    @Test
    void execute_vbdiSignerDocumentOutSent_shouldFilterBySendAgentOne() {
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq(List.of("user_signer")), eq("user1"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwSigner_DocumentOut_Sent");
        request.setKeys(List.of("user1"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        assertThat(filterCaptor.getValue().getExtraPredicateSql()).contains("send_agent").contains("'1'");
    }

    // vwGroup_Is_Unit_In_Charge_by_Identifier (donvisoan/toanbodonvisoan) - identifier TO CHUC that ->
    // VAN qua isOwnIdentifier (khac nguoiky/nguoisoan).
    @Test
    void execute_vbdiGroupUnitInCharge_shouldQueryByTwoFieldsAndValidateOwnIdentifier() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("H05"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwGroup_Is_Unit_In_Charge_by_Identifier");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue()).containsExactly("unit_in_charge_identifier", "unit_identifier");
    }

    @Test
    void execute_vbdiGroupUnitInCharge_withIdentifierNotOwnedByUser_shouldReturnEmptyPageWithoutQuerying() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwGroup_Is_Unit_In_Charge_by_Identifier");
        request.setKeys(List.of("TO_CHUC_KHAC"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never())
            .findByAnyFieldEqualsIdentifier(anyString(), anyString(), any(), anyString(), any(), any());
    }

    // vwPrivateImportant (quantrong) - KHONG dieu kien status trong formula goc -> status=null.
    @Test
    void execute_vbdiPrivateImportant_shouldQueryMarkImportantWithoutStatusFilter() {
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        VbdiDocument document = new VbdiDocument("VB quan trong", "vbdi", VbdiStatus.DRAFT, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByFieldContainsMemberAnySeparator(
                eq("vbdi"), statusCaptor.capture(), eq("MarkImportant"), eq("user1"), any(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwPrivateImportant");
        request.setKeys(List.of("user1"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(statusCaptor.getValue()).isNull();
    }

    // 16/09/2026 (modal "Cap nhat ket qua xu ly van ban -> Gan van ban tra loi") - vanbandi_by_author:
    // KHONG dieu kien status trong formula goc -> status=null, khoa boi field "author".
    @Test
    void execute_vbdiByAuthor_shouldQueryAuthorFieldWithoutStatusFilter() {
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        VbdiDocument document = new VbdiDocument("VB theo tac gia", "vbdi", VbdiStatus.DRAFT, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByFieldContainsMemberAnySeparator(
                eq("vbdi"), statusCaptor.capture(), eq("author"), eq("H05"), any(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vanbandi_by_author");
        request.setKeys(List.of("H05"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(statusCaptor.getValue()).isNull();
    }

    // vbdi_by_user_draft: KHONG dieu kien status trong formula goc -> status=null, khoa boi
    // "user_draft" - key la USERNAME (khong phai identifier).
    @Test
    void execute_vbdiByUserDraft_shouldQueryUserDraftFieldWithoutStatusFilter() {
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        VbdiDocument document = new VbdiDocument("VB dang soan", "vbdi", VbdiStatus.DRAFT, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByFieldContainsMemberAnySeparator(
                eq("vbdi"), statusCaptor.capture(), eq("user_draft"), eq("user1"), any(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vbdi_by_user_draft");
        request.setKeys(List.of("user1"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(statusCaptor.getValue()).isNull();
    }

    @Test
    void execute_vbdiByAuthor_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vanbandi_by_author");
        request.setKeys(List.of(""));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never())
            .findByFieldContainsMemberAnySeparator(anyString(), any(), anyString(), anyString(), any(), any());
    }

    /** Rut gon stub isOwnIdentifier dung lai o nhieu test tren (user1 thuoc identifiers cho truoc). */
    private void stubOwnIdentifier(String username, String identifiers) {
        AppUser user = newUser(username, "Test User", username + "@test.vn");
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of(username));
        lenient().when(appUserRepository.findByUsernameAndDeletedOnIsNull(username)).thenReturn(Optional.of(user));
        UserOrganizationProfileDto profile = new UserOrganizationProfileDto();
        profile.setIdentifiers(identifiers);
        lenient().when(getUserOrganizationProfileUseCase.buildProfileForUser(user)).thenReturn(profile);
    }

    // 29/08/2026 (yeu cau nguoi dung "port toan bo chuc nang ngoai nhung phan da lam o tren trong
    // route vbdi/xuly/", dot 1) - vwCreator_DocumentOut_All_BO (route "toanbo_all"): OR 1 field
    // (unit_publish_identifier), status=ISSUED.
    @Test
    void execute_vbdiToanboAllBo_shouldQueryByUnitPublishIdentifier() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        VbdiDocument document = new VbdiDocument("VB toanbo all", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("H05"), any(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_All_BO");
        request.setKeys(List.of("H05"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(fieldsCaptor.getValue()).containsExactly("unit_publish_identifier");
    }

    // vwCreator_DocumentOut_All (route "cq_toanbo"/"dv_toanbo"/"pb_toanbo", CUNG 1 view - chi khac
    // level identifier FE tu tinh): OR 4 field khac han findIssuedByCreatorOrUnitIdentifier.
    // Bugfix 15/09/2026: "unit_creator_identifier" bo sung de khop DUNG 4 field ma nhanh Elastic cu
    // cua FE (ItemsList.jsx#fetchData, filter.key_or) dung truoc khi bi go bo - thieu field nay se
    // hep pham vi ket qua so voi hanh vi cu.
    @Test
    void execute_vbdiDocumentOutAll_shouldQueryByPublishOrUnitOrInChargeOrCreatorIdentifier() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("H05"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_All");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue())
            .containsExactly("unit_publish_identifier", "unit_identifier", "unit_in_charge_identifier",
                "unit_creator_identifier");
    }

    @Test
    void execute_vbdiDocumentOutAll_withIdentifierNotOwnedByUser_shouldReturnEmptyPageWithoutQuerying() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_All");
        request.setKeys(List.of("TO_CHUC_KHAC"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never())
            .findByAnyFieldEqualsIdentifier(anyString(), anyString(), any(), anyString(), any(), any());
    }

    // 09/09/2026 (cutover dashboard tu Domino qlvb/tichhop/TimKiem?databasetype=vbdi - bo qua
    // Elasticsearch): khong gui "keys" (dashboard "tim kiem nang cao" khong biet identifier truoc) ->
    // phai tu resolveCurrentIdentifier() (server-resolved), VAN qua isOwnIdentifier nhu cu, VA hop
    // nhat dung 10 tieu chi cua form CollapseVBdi.jsx that (return JSX cuoi file) vao filter.
    @Test
    void execute_vbdiDocumentOutAll_withoutKeysAndAdvancedSearch_shouldResolveIdentifierServerSideAndMergeFilter() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        when(resolveIdentifierByLevelUseCase.resolveForCurrentUser()).thenReturn(Optional.of("H05"));
        ArgumentCaptor<com.td.application.vbdi.VbdiDocumentOutFilter> filterCaptor =
            ArgumentCaptor.forClass(com.td.application.vbdi.VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), any(), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_All");
        Map<String, Object> search = new java.util.LinkedHashMap<>();
        search.put("document_type", "Công văn");
        search.put("sector", "Khoa học công nghệ");
        search.put("unit_publish_identifier", "H05");
        search.put("unit_in_charge_identifier", "H05.01");
        search.put("user_signer", "nguyenvanb");
        search.put("draftor", "Nguyen Van A");
        search.put("lien_thong", "tthc");
        search.put("send_agent", "1");
        search.put("code_full", "123/QD-UBND");
        search.put("mc_thutuc", "Cap giay phep xay dung");
        search.put("promulgation_date", List.of("01/08/2026", "31/08/2026"));
        request.setSearch(search);

        service.execute("vbdi", request);

        var filter = filterCaptor.getValue();
        assertThat(filter.getIncludeFieldValues())
            .containsEntry("document_type", List.of("Công văn"))
            .containsEntry("sector", List.of("Khoa học công nghệ"))
            .containsEntry("unit_publish_identifier", List.of("H05"))
            .containsEntry("unit_in_charge_identifier", List.of("H05.01"))
            .containsEntry("user_signer", List.of("nguyenvanb"))
            .containsEntry("draftor", List.of("Nguyen Van A"))
            .containsEntry("lien_thong", List.of("tthc"))
            .containsEntry("send_agent", List.of("1"));
        assertThat(filter.getContainsAnyFieldValues())
            .containsEntry("code_full", List.of("123/QD-UBND"))
            .containsEntry("mc_thutuc", List.of("Cap giay phep xay dung"));
        assertThat(filter.getPromulgationDateFrom()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(filter.getPromulgationDateTo()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void execute_vbdiDocumentOutAll_withKeysProvided_shouldStillPreferClientSuppliedIdentifier() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), any(), eq("H05"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_All");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        verify(resolveIdentifierByLevelUseCase, never()).resolveForCurrentUser();
    }

    // vwDocumentOut_UserAll (route "cn_toanbo"): field kieu username, khong qua macro "$username$"
    // (ChoBanHanh/ItemsList.jsx gui thang currentUser.username, khac han component phoihop/xemdebiet/
    // xuly rieng) - resolveUsernameToken phai GIU NGUYEN gia tri nay (khong phai "$username$").
    @Test
    void execute_vbdiUserAll_shouldQueryByFourFieldsContainsMember() {
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        VbdiDocument document = new VbdiDocument("VB cua toi", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByAnyFieldContainsMember(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("user1"), any(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocumentOut_UserAll");
        request.setKeys(List.of("user1"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(fieldsCaptor.getValue())
            .containsExactly("user_creator", "user_draft", "coordinator_hide_rtext", "userxemdebiet");
    }

    // vwCreator_DocumentOut_WaitingToSend (route "chuagui"): tai dung findIssuedByPublishOrCreatorIdentifier
    // (cung cong thuc @If-fallback voi "Toan bo") + extraPredicateSql rieng (send_agent/deletelistWaitingToSend).
    @Test
    void execute_vbdiWaitingToSend_shouldReuseTextBookIdentifierFormulaWithExtraPredicate() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        when(vbdiDocumentRepository.findIssuedByPublishOrCreatorIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_WaitingToSend");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        assertThat(filterCaptor.getValue().getExtraPredicateSql())
            .contains("send_agent").contains("deletelistWaitingToSend");
    }

    // vwCreator_DocumentOut_Reply_Draftor (route "datralai"): status=RETURNED_BY_CLERK ("3"), KHAC
    // ISSUED - xac nhan dung status truyen xuong repository.
    @Test
    void execute_vbdiReplyDraftor_shouldQueryWithReturnedByClerkStatus() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.RETURNED_BY_CLERK), any(), eq("H05"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_Reply_Draftor");
        request.setKeys(List.of("H05"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository).findByAnyFieldEqualsIdentifier(
            eq("vbdi"), eq(VbdiStatus.RETURNED_BY_CLERK), any(), eq("H05"), any(), any());
    }

    // 09/09/2026 (port not SoLuongCanXuLy, nhom VBDI_COQUAN - xem javadoc GetSoLuongCanXuLyUseCase):
    // vwCreator_DocumentOut_Refuse - view THAT KHAC vwCreator_DocumentOut_ReplyRefuse (dxl ~646-676).
    @Test
    void execute_vbdiCreatorDocumentOutRefuse_shouldMatchDxlFormula() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_Refuse");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue()).containsExactly("user_creator", "unit_creator_identifier");
        assertThat(filterCaptor.getValue().getExtraPredicateSql())
            .contains("unit_reply_refuse").contains("is_unit_reply_refuse");
    }

    // vwCreator_DocumentOut_Reply - view THAT KHAC vwCreator_DocumentOut_Reply_Draftor (dxl ~435-473).
    @Test
    void execute_vbdiCreatorDocumentOutReply_shouldMatchDxlFormula() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_Reply");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue())
            .containsExactly("user_creator", "unit_creator_identifier", "unit_publish_identifier", "user_draft");
        assertThat(filterCaptor.getValue().getExtraPredicateSql())
            .contains("is_document_reply").contains("deletelistWaitingToSend");
    }

    // vwCreator_DocumentOut_Revoke (route "vanbandathuhoi"): extraPredicateSql rieng (revoke_documentOut).
    @Test
    void execute_vbdiRevoke_shouldIncludeRevokeExtraPredicate() {
        stubOwnIdentifier("user1", "H05.01;H05;G10;");
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldEqualsIdentifier(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("H05"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwCreator_DocumentOut_Revoke");
        request.setKeys(List.of("H05"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue()).containsExactly("user_creator", "unit_creator_identifier");
        assertThat(filterCaptor.getValue().getExtraPredicateSql()).contains("revoke_documentOut");
    }

    // vwDocumentOut_Coordinater_Receive (route "chophoihop") + vwDocumentOut_coordinater ("phoihop"):
    // FE gui macro THAT "$username$" (khac cn_toanbo) - resolveUsernameToken PHAI thay bang username
    // phien dang nhap qua CurrentUserProvider, KHONG duoc dung "$username$" lam gia tri that.
    @Test
    void execute_vbdiCoordinaterReceive_shouldResolveUsernameTokenViaCurrentUserProvider() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("user1"));
        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<VbdiDocumentOutFilter> filterCaptor = ArgumentCaptor.forClass(VbdiDocumentOutFilter.class);
        VbdiDocument document = new VbdiDocument("VB cho y kien", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
        Page<VbdiDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbdiDocumentRepository.findByAnyFieldContainsMember(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq(List.of("usercoordinater")), memberCaptor.capture(),
                filterCaptor.capture(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocumentOut_Coordinater_Receive");
        request.setKeys(List.of("$username$"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(memberCaptor.getValue()).isEqualTo("user1");
        assertThat(filterCaptor.getValue().getExtraPredicateSql()).contains("isgiveopinion");
    }

    @Test
    void execute_vbdiCoordinater_shouldQueryByCoordinaterSendField() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("user1"));
        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        when(vbdiDocumentRepository.findByAnyFieldContainsMember(
                eq("vbdi"), eq(VbdiStatus.ISSUED), eq(List.of("usercoordinater_send")), memberCaptor.capture(),
                any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocumentOut_coordinater");
        request.setKeys(List.of("$username$"));

        service.execute("vbdi", request);

        assertThat(memberCaptor.getValue()).isEqualTo("user1");
    }

    // vwDocumentOut_ChoXemDeBiet (route "choxemdebiet"): OR 2 field (coordinator_hide_not_seen_rtext,
    // userxemdebiet) - dung @Explode(field1+";"+field2) cua dxl.
    @Test
    void execute_vbdiChoXemDeBiet_shouldQueryByTwoFieldsContainsMember() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("user1"));
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        when(vbdiDocumentRepository.findByAnyFieldContainsMember(
                eq("vbdi"), eq(VbdiStatus.ISSUED), fieldsCaptor.capture(), eq("user1"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocumentOut_ChoXemDeBiet");
        request.setKeys(List.of("$username$"));

        service.execute("vbdi", request);

        assertThat(fieldsCaptor.getValue())
            .containsExactly("coordinator_hide_not_seen_rtext", "userxemdebiet");
    }

    @Test
    void execute_vbdiCoordinaterReceive_withUnresolvedUsername_shouldReturnEmptyPageWithoutQuerying() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwDocumentOut_Coordinater_Receive");
        request.setKeys(List.of("$username$"));

        var result = service.execute("vbdi", request);

        assertThat(result.getTotalItems()).isZero();
        verify(vbdiDocumentRepository, never())
            .findByAnyFieldContainsMember(anyString(), anyString(), any(), anyString(), any(), any());
    }

    // DT-002 - port lai lan 2 (25/08/2026) - xem javadoc VIEW_DUTHAO_CREATOR_PROCESS_HANDLER_SUBMIT:
    // XML export moi tu DB that la nguon chuan, khoa DXL thuc su la user_other_PROCESSED_handler
    // (lich su nguoi da chuyen di, chuoi ";"-joined) - khong phai UNION 2 field process_handler nhu
    // doc view_duthao.dxl cu (da stale), cung khong phai user_creator du header ghi vay.
    @Test
    void execute_duthaoVwUserCreatorProcessHandlerSubmit_shouldQueryByProcessHandlerFieldsAndStatus() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(duThaoDocumentRepository.findByVbdenOrgView(eq("duthao"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUserCreator_Process_Handler_Submit");
        request.setKeys(List.of("username_test"));

        service.execute("duthao", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgListFieldNames()).containsExactly("user_other_processed_handler");
        assertThat(filter.getOrgFieldNames()).isNull();
        assertThat(filter.getOrgValue()).isEqualTo("username_test");
        assertThat(filter.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.FORWARDED_FOR_APPROVAL, DuThaoStatus.OTHER_HANDLER_SUBMIT));
    }

    @Test
    void execute_duthaoVwUserCreatorProcessHandlerSubmit_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUserCreator_Process_Handler_Submit");
        request.setKeys(List.of());

        var result = service.execute("duthao", request);

        assertThat(result.getTotalItems()).isZero();
        verify(duThaoDocumentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // DT-002 (tiep, 25/08/2026) - xem javadoc VIEW_DUTHAO_USER_OTHER_PROCESS_HANDLER_SUBMIT: khoa DXL
    // la user_other_process_handler (cong thuc cot $4, dxl 874-905), header 'user_signer' chi la
    // artefact copy-paste cua design Domino; CHI lay status "9" (chuyennguoikhac) - khac view
    // UserCreator o tren gom UNION 2 field + ca status "5"/"9".
    @Test
    void execute_duthaoVwUserOtherProcessHandlerSubmit_shouldQueryByOtherProcessHandlerFieldAndStatus() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(duThaoDocumentRepository.findByVbdenOrgView(eq("duthao"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Other_Process_Handler_Submit");
        request.setKeys(List.of("username_test"));

        service.execute("duthao", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_other_process_handler");
        assertThat(filter.getOrgValue()).isEqualTo("username_test");
        assertThat(filter.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.OTHER_HANDLER_SUBMIT));
    }

    @Test
    void execute_duthaoVwUserOtherProcessHandlerSubmit_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Other_Process_Handler_Submit");
        request.setKeys(List.of());

        var result = service.execute("duthao", request);

        assertThat(result.getTotalItems()).isZero();
        verify(duThaoDocumentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // ================= DT-002 PORT LOAT 2 (25/08/2026) - 20 view con lai =================
    // Moi test khoa 1 view theo dung cong thuc dxl da doi chieu; ten field/header artefact va
    // ly do don gian hoa (author->user_creator...) ghi o khoi comment constants cua ViewQueryService.

    private VbdenOrgViewFilter capturedFilterFor(String viewname) {
        ArgumentCaptor<VbdenOrgViewFilter> captor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(duThaoDocumentRepository.findByVbdenOrgView(eq("duthao"), captor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname(viewname);
        request.setKeys(List.of("username_test"));
        service.execute("duthao", request);
        return captor.getValue();
    }

    @Test
    void duthaoVwSignerSubmit_shouldMatchDxlFormula() {
        VbdenOrgViewFilter f = capturedFilterFor("vwSigner_Submit");
        assertThat(f.getOrgFieldNames()).containsExactly("user_signer");
        assertThat(f.getIncludeFieldValues()).containsEntry("status",
            List.of(DuThaoStatus.PENDING_SIGNATURE, DuThaoStatus.SIGNED_WAITING_SUBMIT));
    }

    @Test
    void duthaoVwPreSignerSubmit_shouldMatchDxlFormula() {
        VbdenOrgViewFilter f = capturedFilterFor("vwPre_Signer_Submit");
        assertThat(f.getOrgFieldNames()).containsExactly("user_pre_signer");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.PRE_SIGN_SUBMIT));
    }

    @Test
    void duthaoVwUserReturnDraft_shouldRequireReturnedStatusAndEmptyRetfor() {
        VbdenOrgViewFilter f = capturedFilterFor("vwUser_Return_Draft");
        assertThat(f.getOrgFieldNames()).containsExactly("user_creator");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.RETURNED));
        assertThat(f.getRequireEmptyFieldName()).isEqualTo("return_document_out_for");
    }

    @Test
    void duthaoVwCreatorDocumentOutSubmited_shouldMatchDxlFormula() {
        VbdenOrgViewFilter f = capturedFilterFor("vwCreator_DocumentOut_Submited");
        assertThat(f.getOrgFieldNames()).containsExactly("user_creator");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.PUBLISHED));
    }

    // 09/09/2026 (port not SoLuongCanXuLy): "vwDocumentOut_Submited" THAT (dxl 1608-1641) - KHAC view
    // tren (khong co "Creator_") - CUNG field/status nhung THEM dieu kien xoavanbandi rong.
    @Test
    void duthaoVwDocumentOutSubmited_shouldMatchDxlFormulaWithXoaVanBanDiCondition() {
        VbdenOrgViewFilter f = capturedFilterFor("vwDocumentOut_Submited");
        assertThat(f.getOrgFieldNames()).containsExactly("user_creator");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.PUBLISHED));
        assertThat(f.getRequireEmptyFieldName()).isEqualTo("xoavanbandi");
    }

    // 09/09/2026 (port not SoLuongCanXuLy): "vwChoDuyetKyBanHanh"/"vwChoDuyetKyBanHanhMC" (dxl
    // 1806-1872) - OR-group 2 nhanh (signer/handler) x 2 bien the (mc_mahs rong/khac rong), xem
    // javadoc GetSoLuongCanXuLyUseCase + hang so VIEW_DUTHAO_CHODUYETKYBANHANH* trong ViewQueryService.
    @Test
    void duthaoChoDuyetKyBanHanhSigner_shouldRequireSignerWaitingStatusAndEmptyMcMahs() {
        VbdenOrgViewFilter f = capturedFilterFor("vwchoduyetkybanhanh_signer_internal");
        assertThat(f.getOrgFieldNames()).containsExactly("user_signer");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.PENDING_SIGNATURE, DuThaoStatus.SIGNED_WAITING_SUBMIT));
        assertThat(f.getRequireEmptyFieldName()).isEqualTo("mc_mahs");
    }

    @Test
    void duthaoChoDuyetKyBanHanhHandler_shouldRequireOtherHandlerSubmitStatusAndEmptyMcMahs() {
        VbdenOrgViewFilter f = capturedFilterFor("vwchoduyetkybanhanh_handler_internal");
        assertThat(f.getOrgFieldNames()).containsExactly("user_other_process_handler");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.OTHER_HANDLER_SUBMIT));
        assertThat(f.getRequireEmptyFieldName()).isEqualTo("mc_mahs");
    }

    @Test
    void duthaoChoDuyetKyBanHanhMcSigner_shouldRequireNonEmptyMcMahs() {
        VbdenOrgViewFilter f = capturedFilterFor("vwchoduyetkybanhanhmc_signer_internal");
        assertThat(f.getOrgFieldNames()).containsExactly("user_signer");
        assertThat(f.getRequireNonEmptyFieldName()).isEqualTo("mc_mahs");
    }

    @Test
    void duthaoChoDuyetKyBanHanhMcHandler_shouldRequireNonEmptyMcMahs() {
        VbdenOrgViewFilter f = capturedFilterFor("vwchoduyetkybanhanhmc_handler_internal");
        assertThat(f.getOrgFieldNames()).containsExactly("user_other_process_handler");
        assertThat(f.getRequireNonEmptyFieldName()).isEqualTo("mc_mahs");
    }

    @Test
    void duthaoVwDocumentOutPublished_shouldSimplifyAuthorToCreatorAndUseStatus8() {
        VbdenOrgViewFilter f = capturedFilterFor("vwDocumentOut_Published");
        assertThat(f.getOrgFieldNames()).containsExactly("user_creator");
        assertThat(f.getIncludeFieldValues()).containsEntry("status", List.of("8"));
    }

    @Test
    void duthaoVwCreatorSignerSubmit_shouldRequireNonEmptySigner() {
        VbdenOrgViewFilter f = capturedFilterFor("vwCreator_Signer_Submit");
        assertThat(f.getOrgFieldNames()).containsExactly("user_creator");
        assertThat(f.getIncludeFieldValues()).containsEntry("status",
            List.of(DuThaoStatus.PENDING_SIGNATURE, DuThaoStatus.SIGNED_WAITING_SUBMIT));
        assertThat(f.getRequireNonEmptyFieldName()).isEqualTo("user_signer");
    }

    @Test
    void duthaoVwCreatorPreSignerSubmit_shouldRequireNonEmptyPreSigner() {
        VbdenOrgViewFilter f = capturedFilterFor("vwCreator_Pre_Signer_Submit");
        assertThat(f.getOrgFieldNames()).containsExactly("user_creator");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.PRE_SIGN_SUBMIT));
        assertThat(f.getRequireNonEmptyFieldName()).isEqualTo("user_pre_signer");
    }

    @Test
    void duthaoVwUserProcessHandlerSubmit_shouldMatchDxlFormula() {
        VbdenOrgViewFilter f = capturedFilterFor("vwUser_Process_Handler_Submit");
        assertThat(f.getOrgFieldNames()).containsExactly("user_process_handler");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.FORWARDED_FOR_APPROVAL));
    }

    @Test
    void duthaoVwUserSignerReturnDraft_shouldMatchDxlFormula() {
        VbdenOrgViewFilter f = capturedFilterFor("vwUser_Signer_Return_Draft");
        assertThat(f.getOrgFieldNames()).containsExactly("user_signer");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.RETURNED));
    }

    @Test
    void duthaoVwUserHandlerReturnDraft_shouldMemberMatchProcessHandlerAndProcessed() {
        VbdenOrgViewFilter f = capturedFilterFor("vwUser_Handler_Return_Draft");
        assertThat(f.getOrgListFieldNames())
            .containsExactly("user_process_handler", "user_processed");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.RETURNED));
    }

    @Test
    void duthaoVwSignerDocumentOutSubmited_shouldMatchDxlFormula() {
        VbdenOrgViewFilter f = capturedFilterFor("vwSigner_DocumentOut_Submited");
        assertThat(f.getOrgFieldNames()).containsExactly("user_signer");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.PUBLISHED));
    }

    @Test
    void duthaoVwCreatorProcessHandlerSubmit_shouldRequireNonEmptyProcessHandler() {
        VbdenOrgViewFilter f = capturedFilterFor("vwCreator_Process_Handler_Submit");
        assertThat(f.getOrgFieldNames()).containsExactly("user_creator");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.FORWARDED_FOR_APPROVAL));
        assertThat(f.getRequireNonEmptyFieldName()).isEqualTo("user_process_handler");
    }

    @Test
    void duthaoVwUserProcessHandlerForward_shouldUseFormulaAfterRemBlock() {
        VbdenOrgViewFilter f = capturedFilterFor("vwUser_Process_Handler_Foward");
        assertThat(f.getOrgListFieldNames()).containsExactly("user_creator", "user_processed");
        assertThat(f.getIncludeFieldValues()).containsEntry("status",
            List.of(DuThaoStatus.PENDING_SIGNATURE, DuThaoStatus.FORWARDED_FOR_APPROVAL));
    }

    @Test
    void duthaoVwByHandlerDocumentOutSubmited_shouldMemberMatchAndStatus4() {
        VbdenOrgViewFilter f = capturedFilterFor("vwBy_Handler_DocumentOut_Submited");
        assertThat(f.getOrgListFieldNames())
            .containsExactly("user_process_handler", "user_processed");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.PUBLISHED));
    }

    @Test
    void duthaoVwPreSignerSigned_shouldFilterByPreSignerSignedFlagWithoutStatus() {
        VbdenOrgViewFilter f = capturedFilterFor("vwPre_Signer_Signed");
        assertThat(f.getOrgFieldNames()).containsExactly("user_pre_signer");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("pre_signer_signed", List.of("1"))
            .doesNotContainKey("status");
    }

    @Test
    void duthaoVwUserDocumentOutPublished_shouldMemberMatchThreeFieldsAtStatus8() {
        VbdenOrgViewFilter f = capturedFilterFor("vwUser_DocumentOut_Published");
        assertThat(f.getOrgListFieldNames())
            .containsExactly("user_signer", "user_process_handler", "user_processed");
        assertThat(f.getIncludeFieldValues()).containsEntry("status", List.of("8"));
    }

    @Test
    void duthaoVwCreatorDocumentOutReturn_shouldMatchRetfor2Condition() {
        VbdenOrgViewFilter f = capturedFilterFor("vwCreator_DocumentOut_Return");
        assertThat(f.getOrgListFieldNames())
            .containsExactly("user_creator", "user_signer", "user_other_process_handler");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.RETURNED))
            .containsEntry("return_document_out_for", List.of("2"));
    }

    @Test
    void duthaoVwSignerDocumentOutReturn_shouldMatchRetfor1Condition() {
        VbdenOrgViewFilter f = capturedFilterFor("vwSigner_DocumentOut_Return");
        assertThat(f.getOrgFieldNames()).containsExactly("user_signer");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.PENDING_SIGNATURE))
            .containsEntry("return_document_out_for", List.of("1"));
    }

    @Test
    void duthaoVwSignerDocumentOutWaitingSubmit_shouldMatchDxlFormula() {
        VbdenOrgViewFilter f = capturedFilterFor("vwSigner_DocumentOut_WaitingSubmit");
        assertThat(f.getOrgFieldNames()).containsExactly("user_signer");
        assertThat(f.getIncludeFieldValues())
            .containsEntry("status", List.of(DuThaoStatus.SIGNED_WAITING_SUBMIT));
    }

    @Test
    void duthaoVwUserCreatorProcessedHandlerSubmit_shouldMemberMatchWithoutStatusFilter() {
        VbdenOrgViewFilter f = capturedFilterFor("vwUserCreator_Processed_Handler_Submit");
        assertThat(f.getOrgListFieldNames()).containsExactly("user_other_processed_handler");
        assertThat(f.getIncludeFieldValues()).isNull();
        assertThat(f.getOrgValue()).isEqualTo("username_test");
    }

    // DT-002 (26/08/2026, theo yeu cau nguoi dung "port ca 2 view, bo cot khoa dac biet 'all'") -
    // vwUser_Coordinator: nhan dinh cu (ghi la OR-group) SAI - selection that la AND don gian, xem
    // javadoc VIEW_DUTHAO_USER_COORDINATOR. Phan tu khoa dac biet "all" CO CHU DICH khong port.
    @Test
    void execute_duthaoVwUserCoordinator_shouldQueryByCoordinatorFieldExcludingDoneStatuses() {
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(duThaoDocumentRepository.findByVbdenOrgView(eq("duthao"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Coordinator");
        request.setKeys(List.of("username_test"));

        service.execute("duthao", request);

        VbdenOrgViewFilter filter = filterCaptor.getValue();
        assertThat(filter.getOrgFieldNames()).containsExactly("user_coordinator");
        assertThat(filter.getOrgValue()).isEqualTo("username_test");
        assertThat(filter.getExcludeFieldValues())
            .containsEntry("status", List.of("8", DuThaoStatus.ENDED, DuThaoStatus.PUBLISHED));
    }

    @Test
    void execute_duthaoVwUserCoordinator_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Coordinator");
        request.setKeys(List.of());

        var result = service.execute("duthao", request);

        assertThat(result.getTotalItems()).isZero();
        verify(duThaoDocumentRepository, never()).findByVbdenOrgView(anyString(), any(), any());
    }

    // vwUser_Coordinated: OR-group THAT SU (2 field khac nhau, 2 dieu kien status khac nhau) -
    // VbdenOrgViewFilter khong bieu dien duoc, di qua method repository rieng
    // findByFieldOrFieldWithStatus (xem javadoc VIEW_DUTHAO_USER_COORDINATED).
    @Test
    void execute_duthaoVwUserCoordinated_shouldQueryByFieldOrFieldWithStatus() {
        when(duThaoDocumentRepository.findByFieldOrFieldWithStatus(
                eq("duthao"), eq("user_coordinated"), eq("user_coordinator"),
                eq(List.of("8", DuThaoStatus.ENDED, DuThaoStatus.PUBLISHED)), eq("username_test"),
                any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Coordinated");
        request.setKeys(List.of("username_test"));

        service.execute("duthao", request);

        verify(duThaoDocumentRepository).findByFieldOrFieldWithStatus(
            eq("duthao"), eq("user_coordinated"), eq("user_coordinator"),
            eq(List.of("8", DuThaoStatus.ENDED, DuThaoStatus.PUBLISHED)), eq("username_test"),
            any(), any(), any());
    }

    @Test
    void execute_duthaoVwUserCoordinated_withBlankKey_shouldReturnEmptyPageWithoutQuerying() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwUser_Coordinated");
        request.setKeys(List.of());

        var result = service.execute("duthao", request);

        assertThat(result.getTotalItems()).isZero();
        verify(duThaoDocumentRepository, never()).findByFieldOrFieldWithStatus(
            anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any());
    }

    // DT-007 (26/08/2026) - "vwGroup_process_by_draft_id": lich su xu ly theo 1 du thao cu the
    // (khoa la id du thao, KHONG phai username - khac moi view VIEW_DUTHAO_* khac ben tren).
    @Test
    void execute_duthaoVwGroupProcessByDraftId_shouldQueryByParentIdAndMapProcessFields() {
        DraftProcessDocument process = newProcessDocument(
            "{\"parentid\":\"draft-1\",\"action_type\":\"chuyenduyet\",\"creator\":\"nguyenvana\"}");
        when(draftProcessDocumentRepository.findByParentId(eq("draft-1"), any()))
            .thenReturn(new PageImpl<>(List.of(process), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwGroup_process_by_draft_id");
        request.setKeys(List.of("draft-1"));
        request.setItems("getall");

        var result = service.execute("duthao_res", request);

        verify(draftProcessDocumentRepository).findByParentId(eq("draft-1"), any());
        assertThat(result.getTotalItems()).isEqualTo(1);
        Map<String, Object> row = result.getItems().get(0);
        // Bugfix 26/08/2026: khoa response phai la "action_type" (khop duthaofile.jsx), KHONG phai
        // "action" - xem javadoc XuLyDuThaoUseCase#PROCESS_ACTION_FIELD.
        assertThat(row).containsEntry("action_type", "chuyenduyet");
        assertThat(row).containsEntry("creator", "nguyenvana");
        assertThat(row).containsKey("created");
    }

    // Bugfix 27/08/2026 — LAN 2, dao lai thiet ke lan 1 cung ngay (yeu cau nguoi dung "quan ly tep
    // dinh kem theo id cha, draft_process van the hien dung lan gan kem"): tep KHONG con luu theo
    // id vet - luu theo document_id = id du thao CHA, loc lai qua ListDocumentFilesByProcessUseCase
    // (cap parentDraftId + processId). Dung UUID that (khong phai "draft-1" nhu cac test khac trong
    // file nay) vi collectProcessFiles parse parentid ra UUID that su.
    @Test
    void execute_duthaoVwGroupProcessByDraftId_shouldIncludeFilesFilteredByParentAndProcessId() {
        UUID parentDraftId = UUID.randomUUID();
        DraftProcessDocument process = newProcessDocument("{\"parentid\":\"" + parentDraftId + "\"}");
        when(draftProcessDocumentRepository.findByParentId(eq(parentDraftId.toString()), any()))
            .thenReturn(new PageImpl<>(List.of(process), PageRequest.of(0, 20), 1));
        FileDto fileDto = FileDto.builder().originalFileName("bao_cao.docx").fileSize(1024L).build();
        ListFilesByProcessRequest expectedFilesRequest = ListFilesByProcessRequest.builder()
            .documentId(parentDraftId)
            .processId(process.getId())
            .build();
        when(listDocumentFilesByProcessUseCase.execute(expectedFilesRequest)).thenReturn(
            new PaginationResponse<>(List.of(fileDto), 0, 20, 1L, 1, true, true));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwGroup_process_by_draft_id");
        request.setKeys(List.of(parentDraftId.toString()));
        request.setItems("getall");

        var result = service.execute("duthao_res", request);

        Map<String, Object> row = result.getItems().get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) row.get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).containsEntry("name", "bao_cao.docx");
        assertThat(files.get(0)).containsEntry("size", 1024L);
        assertThat(files.get(0)).containsEntry("type", "docx");
    }

    // parentid rong/khong hop le (vd du lieu cu) -> tra rong thay vi loi.
    @Test
    void execute_duthaoVwGroupProcessByDraftId_withInvalidParentId_shouldReturnEmptyFiles() {
        DraftProcessDocument process = newProcessDocument("{\"parentid\":\"khong-phai-uuid\"}");
        when(draftProcessDocumentRepository.findByParentId(eq("draft-1"), any()))
            .thenReturn(new PageImpl<>(List.of(process), PageRequest.of(0, 20), 1));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwGroup_process_by_draft_id");
        request.setKeys(List.of("draft-1"));
        request.setItems("getall");

        var result = service.execute("duthao_res", request);

        Map<String, Object> row = result.getItems().get(0);
        assertThat(row).containsEntry("files", List.of());
        verify(listDocumentFilesByProcessUseCase, never()).execute(any());
    }

    private DraftProcessDocument newProcessDocument(String dynamicFieldsJson) {
        DraftProcessDocument document = new DraftProcessDocument(
            "Xử lý dự thảo", "draft_process", DuThaoStatus.FORWARDED_FOR_APPROVAL, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    private BusinessDocument newDocument(String title, String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument(title, documentType, "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    // Mo rong BL-240: hosocv gio la HoSoCVDocument (bang rieng hosocv_documents), khong con
    // BusinessDocument (bang documents dung chung).
    private com.td.domain.hosocv.HoSoCVDocument newHoSoCVDocument(String title, String dynamicFieldsJson) {
        var document = new com.td.domain.hosocv.HoSoCVDocument(title, "hosocv", "0", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    // BL-210 DoD (4), yeu cau nguoi dung 05/09/2026 ("tach vbden_res sang bang rieng"): ban ghi
    // vbden_res gio la VbdenResDocument (bang doc lap), khong con la BusinessDocument.
    private com.td.domain.vbden.VbdenResDocument newVbdenResDocument(String title, String dynamicFieldsJson) {
        var document = new com.td.domain.vbden.VbdenResDocument(title, "vbden_res", "DRAFT", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    // BL-250 (VBCDDH Core), bo sung 08/09/2026: ban ghi vbcddh gio la VbcddhDocument (bang doc lap),
    // khong con la BusinessDocument.
    private VbcddhDocument newVbcddhDocument(String title, String dynamicFieldsJson) {
        var document = new VbcddhDocument(title, "vbcddh", "DRAFT", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        return document;
    }

    private static final List<String> VBCDDH_DVTH_FIELDS_FOR_TEST =
        List.of("donvithuchienhide", "chuyenvienthuchienhide");
    private static final List<String> VBCDDH_IDENTITY_FIELDS_FOR_TEST = List.of(
        "user_creator", "nguoitheodoihide", "nguoiphutrachhide", "nguoichidaohide",
        "user_nguoichuyencddh", "coquanid");

    @Test
    void execute_vbcddhNhiemVuByDvth_bare_shouldReturnAllNhiemVuOfUnit() {
        VbcddhDocument document = newVbcddhDocument("Nhiem vu 1",
            "{\"form\":\"nhiemvu\",\"donvithuchienhide\":\"h56.1\",\"tinhtrang\":\"Chưa thực hiện\"}");
        Page<VbcddhDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbcddhDocumentRepository.findByFormAndAnyFieldContainsMember(
                eq("vbcddh"), eq("nhiemvu"), eq(VBCDDH_DVTH_FIELDS_FOR_TEST), eq("H56.1"), isNull(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwNhiem_Vu_By_Donvithuchienhide");
        request.setKeys(List.of("H56.1"));
        request.setItems("getall");

        var result = service.execute("vbcddh", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
        assertThat(result.getItems().get(0)).containsEntry("title", "Nhiem vu 1");
    }

    @Test
    void execute_vbcddhNhiemVuByDvth_chuaXuLy_shouldFilterByTinhTrangAndExcludeTraLai() {
        VbcddhDocument document = newVbcddhDocument("Nhiem vu chua xu ly",
            "{\"form\":\"nhiemvu\",\"donvithuchienhide\":\"h56.1\",\"tinhtrang\":\"Chưa thực hiện\"}");
        Page<VbcddhDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbcddhDocumentRepository.findByFormAndAnyFieldContainsMember(
                eq("vbcddh"), eq("nhiemvu"), eq(VBCDDH_DVTH_FIELDS_FOR_TEST), eq("H56.1"),
                argThat(sql -> sql != null && sql.contains("'Chưa thực hiện'") && sql.contains("tralai")), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwNhiem_Vu_By_Donvithuchienhide_chuaxuly");
        request.setKeys(List.of("H56.1"));
        request.setItems("getall");

        var result = service.execute("vbcddh", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_vbcddhNhiemVuByDvth_withoutKey_shouldReturnEmptyPage() {
        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwNhiem_Vu_By_Donvithuchienhide");
        request.setItems("getall");

        var result = service.execute("vbcddh", request);

        assertThat(result.getTotalItems()).isEqualTo(0L);
        verify(vbcddhDocumentRepository, never())
            .findByFormAndAnyFieldContainsMember(any(), any(), any(), any(), any(), any());
    }

    @Test
    void execute_vbcddhNhiemVuCaNhanCoQuan_shouldUseBothKeysInOrder() {
        VbcddhDocument document = newVbcddhDocument("Nhiem vu giao",
            "{\"form\":\"nhiemvu\",\"coquanid\":\"h56.1\"}");
        Page<VbcddhDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbcddhDocumentRepository.findByFormAndAnyFieldContainsMemberAndLoaiNhiemVu(
                eq("vbcddh"), eq("nhiemvu"), eq(VBCDDH_IDENTITY_FIELDS_FOR_TEST), eq("H56.1"),
                eq("nvtuqlvb"), isNull(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwNhiem_Vu_Ca_Nhan_Co_Quan");
        request.setKeys(List.of("nvtuqlvb", "H56.1"));
        request.setItems("getall");

        var result = service.execute("vbcddh", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_vbcddhVanBanCaNhanCoQuan1_shouldUseIdentityUnionKey() {
        VbcddhDocument document = newVbcddhDocument("Van ban chi dao", "{\"form\":\"vanban\"}");
        Page<VbcddhDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbcddhDocumentRepository.findByFormAndAnyFieldContainsMember(
                eq("vbcddh"), eq("vanban"), eq(VBCDDH_IDENTITY_FIELDS_FOR_TEST), eq("H56.1"), isNull(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwVan_Ban_Ca_Nhan_Co_Quan_1");
        request.setKeys(List.of("H56.1"));
        request.setItems("getall");

        var result = service.execute("vbcddh", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_vbcddhNhiemVuDaXoa_shouldFilterByFormOnly() {
        VbcddhDocument document = newVbcddhDocument("Nhiem vu da xoa", "{\"form\":\"nhiemvu_Deleted\"}");
        Page<VbcddhDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbcddhDocumentRepository.findByForm(eq("vbcddh"), eq("nhiemvu_Deleted"), isNull(), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwNhiem_Vu_Da_Xoa");
        request.setItems("getall");

        var result = service.execute("vbcddh", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_vbcddhNhiemVuByIdVanBan_shouldUseAnyFieldContainsMember() {
        UUID idVanBan = UUID.randomUUID();
        VbcddhDocument document = newVbcddhDocument("Nhiem vu con",
            "{\"form\":\"nhiemvu\",\"idvanban\":\"" + idVanBan + "\"}");
        Page<VbcddhDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbcddhDocumentRepository.findByAnyFieldContainsMember(
                eq("vbcddh"), eq(List.of("idvanban", "idvbdi", "idvbden", "idduthao")), eq(idVanBan.toString()), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwNhiem_Vu_By_Idvanban");
        request.setKeys(List.of(idVanBan.toString()));
        request.setItems("getall");

        var result = service.execute("vbcddh", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    // Bugfix 16/09/2026 (bug that: tab "Danh sach nhiem vu" o chi tiet vbden rong vi truoc day CHI
    // khop idvanban - nhiem vu phat sinh tu vbden thi luu vao field idvbden, khac han).
    @Test
    void execute_vbcddhNhiemVuByIdVanBan_shouldAlsoMatchIdvbdenField() {
        UUID idVbden = UUID.randomUUID();
        VbcddhDocument document = newVbcddhDocument("Nhiem vu tu van ban den",
            "{\"form\":\"nhiemvu\",\"idvbden\":\"" + idVbden + "\"}");
        Page<VbcddhDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbcddhDocumentRepository.findByAnyFieldContainsMember(
                eq("vbcddh"), eq(List.of("idvanban", "idvbdi", "idvbden", "idduthao")), eq(idVbden.toString()), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwNhiem_Vu_By_Idvanban");
        request.setKeys(List.of(idVbden.toString()));
        request.setItems("getall");

        var result = service.execute("vbcddh", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    @Test
    void execute_vbcddhTienDoByIdNhiemVu_shouldQueryVbcddhResWithTienDoNhiemVuForm() {
        UUID idNhiemVu = UUID.randomUUID();
        VbcddhDocument document = newVbcddhDocument("Tien do", "{\"form\":\"tiendonhiemvu\"}");
        Page<VbcddhDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(vbcddhDocumentRepository.findByFormAndFieldValue(
                eq("vbcddh_res"), eq("tiendonhiemvu"), eq("idnhiemvu"), eq(idNhiemVu.toString()), any()))
            .thenReturn(page);

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwTien_Do_Nhiem_Vu_By_Idnhiemvu");
        request.setKeys(List.of(idNhiemVu.toString()));
        request.setItems("getall");

        var result = service.execute("vbcddh", request);

        assertThat(result.getTotalItems()).isEqualTo(1L);
    }

    // Port module "Phieu trinh tham dinh" (phieutrinhtd), 09/09/2026 - xem
    // docs/analysis/20-phan-tich-port-phieutrinh-domino-sang-java.md muc 6.3. Test doi chieu dispatch
    // dung method repository + tham so dung nghia ten tab (khong lap lai loi FE query_by_phieutrinh
    // da phat hien - tai lieu 20 muc 4 #2).
    @Test
    void execute_phieutrinhtdToanBo_shouldDispatchToAnyFieldContainsMemberAcrossRelationFields() {
        PhieuTrinhTdDocument document = new PhieuTrinhTdDocument(
            "Phieu trinh A", PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO, null);
        when(phieuTrinhTdDocumentRepository.findByAnyFieldContainsMember(any(), eq("nguyenvana"), any()))
            .thenReturn(new PageImpl<>(List.of(document)));

        var request = new ViewQueryRequest();
        request.setViewname("phieutrinhtd_toanbo");
        request.setKeys(List.of("nguyenvana"));

        var result = service.execute("phieutrinhtd", request);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).get("status")).isEqualTo(PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO);
        verify(phieuTrinhTdDocumentRepository).findByAnyFieldContainsMember(
            eq(List.of("user_creator", "user_lanhdao1", "user_lanhdao2", "user_lanhdaovanphong")),
            eq("nguyenvana"), any());
    }

    @Test
    void execute_phieutrinhtdDangSoan_shouldFilterByUserCreatorAndStatusDangSoanThao() {
        when(phieuTrinhTdDocumentRepository.findByAnyFieldContainsMemberAndStatusIn(any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        var request = new ViewQueryRequest();
        request.setViewname("phieutrinhtd_dangsoan");
        request.setKeys(List.of("nguyenvana"));

        service.execute("phieutrinhtd", request);

        verify(phieuTrinhTdDocumentRepository).findByAnyFieldContainsMemberAndStatusIn(
            eq(List.of("user_creator")), eq("nguyenvana"), eq(List.of(PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO)), any());
    }

    @Test
    void execute_phieutrinhtdChoDuyet_shouldDispatchToApproverWaiting() {
        when(phieuTrinhTdDocumentRepository.findApproverWaiting(eq("nguyenvanb"), any()))
            .thenReturn(new PageImpl<>(List.of()));

        var request = new ViewQueryRequest();
        request.setViewname("phieutrinhtd_choduyet");
        request.setKeys(List.of("nguyenvanb"));

        service.execute("phieutrinhtd", request);

        verify(phieuTrinhTdDocumentRepository).findApproverWaiting(eq("nguyenvanb"), any());
    }

    @Test
    void execute_phieutrinhtdThamKhao_shouldDispatchToThamKhaoChoYKien() {
        when(phieuTrinhTdDocumentRepository.findThamKhaoChoYKien(eq("hueltm"), any()))
            .thenReturn(new PageImpl<>(List.of()));

        var request = new ViewQueryRequest();
        request.setViewname("phieutrinhtd_thamkhao");
        request.setKeys(List.of("hueltm"));

        service.execute("phieutrinhtd", request);

        verify(phieuTrinhTdDocumentRepository).findThamKhaoChoYKien(eq("hueltm"), any());
    }

    @Test
    void execute_phieutrinhtdThungRac_shouldDispatchToTrash() {
        when(phieuTrinhTdDocumentRepository.findTrash(any())).thenReturn(new PageImpl<>(List.of()));

        var request = new ViewQueryRequest();
        request.setViewname("phieutrinhtd_thungrac");

        service.execute("phieutrinhtd", request);

        verify(phieuTrinhTdDocumentRepository).findTrash(any());
    }

    @Test
    void execute_phieutrinhtdChoThongQua_shouldFilterByLanhdaovanphongAndStatusTrinhThongQua() {
        when(phieuTrinhTdDocumentRepository.findByAnyFieldContainsMemberAndStatusIn(any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        var request = new ViewQueryRequest();
        request.setViewname("phieutrinhtd_chothongqua");
        request.setKeys(List.of("lanhdaovp01"));

        service.execute("phieutrinhtd", request);

        verify(phieuTrinhTdDocumentRepository).findByAnyFieldContainsMemberAndStatusIn(
            eq(List.of("user_lanhdaovanphong")), eq("lanhdaovp01"),
            eq(List.of(PhieuTrinhTdDocument.STATUS_TRINH_THONG_QUA)), any());
    }

    private AppUser newUser(String username, String fullName, String email) {
        return new AppUser(UUID.randomUUID().toString(), username, fullName, email, null, null, true);
    }

    private AppUser newUserWithIdentifier(String username, String fullName, String email, String identifier) {
        return new AppUser(
            UUID.randomUUID().toString(), username, fullName, email, null, null, true,
            null, 0, null, null, null, null, null, null, identifier, false, null);
    }

    private AppUser newLeaderUser(String username, String fullName, int sortOrder, String capLanhDao) {
        return new AppUser(
            UUID.randomUUID().toString(), username, fullName, null, null, null, true,
            null, sortOrder, null, null, null, null, null, capLanhDao, null, false, null);
    }
}
