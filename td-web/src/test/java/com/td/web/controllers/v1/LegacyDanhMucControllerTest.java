package com.td.web.controllers.v1;

import com.td.application.categories.CategoryCacheService;
import com.td.application.categories.CategoryDto;
import com.td.application.categories.CreateCategoryRequest;
import com.td.application.categories.CreateCategoryUseCase;
import com.td.application.categories.DeleteCategoryUseCase;
import com.td.application.categories.ListCategoryFilesUseCase;
import com.td.application.categories.SearchCategoriesRequest;
import com.td.application.categories.SearchCategoriesUseCase;
import com.td.application.categories.UpdateCategoryRequest;
import com.td.application.categories.UpdateCategoryUseCase;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.sharedcore.CreateOrganizationUseCase;
import com.td.application.sharedcore.DeleteOrganizationUseCase;
import com.td.application.sharedcore.GetCapBanHanhOptionsUseCase;
import com.td.application.sharedcore.GetLegacyOrganizationFlatListUseCase;
import com.td.application.sharedcore.GetLegacyOrganizationTreeUseCase;
import com.td.application.sharedcore.GetLegacyOrgLookupForDungChungUseCase;
import com.td.application.sharedcore.LegacyOrganizationTreeResponse;
import com.td.application.sharedcore.UpdateOrganizationUseCase;
import com.td.application.viewquery.ViewQueryRequest;
import com.td.application.viewquery.ViewQueryService;
import com.td.application.workcalendars.DeleteWorkCalendarUseCase;
import com.td.application.workcalendars.GetWeekWorkCalendarUseCase;
import com.td.application.workcalendars.SaveWorkCalendarUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.web.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-105 (DanhMuc Module) — Definition of Done (1): các action danh mục phổ biến
 * (GetView, LuuDanhMuc, XoaDanhMuc, CapNhatCache) hoạt động qua Legacy, dùng chung dữ liệu/cache
 * với {@link CategoriesController}.
 */
@WebMvcTest(controllers = LegacyDanhMucController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class LegacyDanhMucControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LegacyDanhMucController controller;

    @MockBean
    private GetLegacyOrganizationTreeUseCase getLegacyOrganizationTreeUseCase;
    @MockBean
    private GetLegacyOrganizationFlatListUseCase getLegacyOrganizationFlatListUseCase;
    @MockBean
    private GetLegacyOrgLookupForDungChungUseCase getLegacyOrgLookupForDungChungUseCase;
    @MockBean
    private CreateOrganizationUseCase createOrganizationUseCase;
    @MockBean
    private UpdateOrganizationUseCase updateOrganizationUseCase;
    @MockBean
    private DeleteOrganizationUseCase deleteOrganizationUseCase;
    @MockBean
    private GetCapBanHanhOptionsUseCase getCapBanHanhOptionsUseCase;
    @MockBean
    private CreateCategoryUseCase createCategoryUseCase;
    @MockBean
    private UpdateCategoryUseCase updateCategoryUseCase;
    @MockBean
    private DeleteCategoryUseCase deleteCategoryUseCase;
    @MockBean
    private CategoryCacheService categoryCacheService;
    @MockBean
    private ViewQueryService viewQueryService;
    @MockBean
    private SearchCategoriesUseCase searchCategoriesUseCase;
    // Bo sung 09/09/2026 (cutover Footer.tsx/Banner.tsx/ListItemHDSD.tsx).
    @MockBean
    private ListCategoryFilesUseCase listCategoryFilesUseCase;
    @MockBean
    private MinioService minioService;

    // Port lichct (08/09/2026) - can @MockBean cho 3 dependency moi cua constructor
    // (LegacyDanhMucController#GetLichCongTac/LuuDanhMuc/XoaDanhMuc nhanh lichct), neu khong
    // WebMvcTest context load that bai (cung loi da ghi nhan o docs/architecture/04 muc 11).
    @MockBean
    private GetWeekWorkCalendarUseCase getWeekWorkCalendarUseCase;
    @MockBean
    private SaveWorkCalendarUseCase saveWorkCalendarUseCase;
    @MockBean
    private DeleteWorkCalendarUseCase deleteWorkCalendarUseCase;

    // Bugfix 03/09/2026 (báo lỗi người dùng: cây cơ cấu lộ dữ liệu cơ quan khác - xem javadoc
    // GetLegacyOrganizationTreeUseCase). Goi thang method controller (khong qua MockMvc dispatch) -
    // cung tien le QLVBCompatAuthControllerTest, vi viec MockMvc resolve tham so Authentication
    // trong slice @WebMvcTest nay khong dam bao hoat dong dung nhu filter chain that.
    @Test
    void getCayCoCauToChuc_withRegularUser_shouldRequestScopedTree() {
        when(getLegacyOrganizationTreeUseCase.execute(any(), eq(false)))
            .thenReturn(LegacyOrganizationTreeResponse.success(List.of(), "key"));
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "nhanvien01", null,
            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));

        controller.getCayCoCauToChuc(null, null, authentication);

        verify(getLegacyOrganizationTreeUseCase).execute(any(), eq(false));
    }

    @Test
    void getCayCoCauToChuc_withAdminRole_shouldRequestFullTree() {
        when(getLegacyOrganizationTreeUseCase.execute(any(), eq(true)))
            .thenReturn(LegacyOrganizationTreeResponse.success(List.of(), "key"));
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "admin01", null,
            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));

        controller.getCayCoCauToChuc(null, null, authentication);

        verify(getLegacyOrganizationTreeUseCase).execute(any(), eq(true));
    }

    @Test
    void getCayCoCauToChuc_withoutAuthentication_shouldRequestScopedTree() {
        when(getLegacyOrganizationTreeUseCase.execute(any(), eq(false)))
            .thenReturn(LegacyOrganizationTreeResponse.success(List.of(), "key"));

        controller.getCayCoCauToChuc(null, null, null);

        verify(getLegacyOrganizationTreeUseCase).execute(any(), eq(false));
    }

    // Port danhmuc (09/09/2026, xem javadoc GetLegacyOrganizationFlatListUseCase).
    @Test
    void danhSachCoCauToChuc_shouldReturnFlatListFromUseCase() throws Exception {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", "org-1");
        row.put("name", "So Noi Vu");
        row.put("identifier", "SNV");
        when(getLegacyOrganizationFlatListUseCase.execute()).thenReturn(List.of(row));

        mockMvc.perform(post("/api/v1/danhmuc/DanhSachCoCauToChuc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.data[0].identifier").value("SNV"));

        verify(getLegacyOrganizationFlatListUseCase).execute();
    }

    /**
     * Bugfix 07/09/2026 (người dùng báo "Cơ quan, đơn vị thuộc Tỉnh" thiếu 1 Sở dù đã tồn tại/
     * active — xem javadoc {@code GetLegacyOrganizationTreeUseCase}): user THƯỜNG (không ADMIN)
     * truyền {@code full_tree=true} (vd modal "Phát hành văn bản đi") PHẢI vẫn nhận cây ĐẦY ĐỦ.
     */
    @Test
    void getCayCoCauToChuc_withRegularUserAndFullTreeParam_shouldRequestFullTree() {
        when(getLegacyOrganizationTreeUseCase.execute(any(), eq(true)))
            .thenReturn(LegacyOrganizationTreeResponse.success(List.of(), "key"));
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "nhanvien01", null,
            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));

        controller.getCayCoCauToChuc(null, true, authentication);

        verify(getLegacyOrganizationTreeUseCase).execute(any(), eq(true));
    }

    /** {@code full_tree} vắng/false với user thường vẫn PHẢI giữ hành vi cũ (không phá bugfix 03/09/2026). */
    @Test
    void getCayCoCauToChuc_withRegularUserAndFullTreeFalse_shouldStillRequestScopedTree() {
        when(getLegacyOrganizationTreeUseCase.execute(any(), eq(false)))
            .thenReturn(LegacyOrganizationTreeResponse.success(List.of(), "key"));
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "nhanvien01", null,
            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));

        controller.getCayCoCauToChuc(null, false, authentication);

        verify(getLegacyOrganizationTreeUseCase).execute(any(), eq(false));
    }

    @Test
    void publish_withGetCapBanHanh_shouldDelegateToGetCapBanHanhOptionsUseCase() throws Exception {
        Map<String, Object> item = Map.of("id", UUID.randomUUID(), "name", "Tỉnh A", "form", "agency_level");
        when(getCapBanHanhOptionsUseCase.execute("duthao")).thenReturn(Result.success(List.of(item)));

        mockMvc.perform(post("/api/v1/danhmuc/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"get\":\"capbanhanh\",\"databasetype\":\"duthao\",\"username\":\"nv1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("Tỉnh A"));

        verify(getCapBanHanhOptionsUseCase).execute("duthao");
    }

    @Test
    void publish_withUnsupportedGet_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/danhmuc/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"get\":\"kythualenh\"}"))
            .andExpect(status().isBadRequest());

        verify(getCapBanHanhOptionsUseCase, never()).execute(any());
    }

    @Test
    void getView_shouldDelegateToViewQueryService() throws Exception {
        Map<String, Object> item = Map.of("username", "alice");
        when(viewQueryService.execute(eq("danhmuc"), any()))
            .thenReturn(new PaginationResponse<>(List.of(item), 0, 20, 1L, 1, true, true));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwuserbyparentid");
        request.setKeys(List.of(UUID.randomUUID().toString()));

        mockMvc.perform(post("/api/v1/danhmuc/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.data[0].username").value("alice"));

        verify(viewQueryService).execute(eq("danhmuc"), any());
    }

    @Test
    void getView_withUnsupportedViewname_shouldReturn400() throws Exception {
        when(viewQueryService.execute(any(), any()))
            .thenThrow(new IllegalArgumentException("Khong ho tro viewname: vwKhongTonTai"));

        ViewQueryRequest request = new ViewQueryRequest();
        request.setViewname("vwKhongTonTai");

        mockMvc.perform(post("/api/v1/danhmuc/GetView")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void luuDanhMuc_withoutId_shouldCreateCategory() throws Exception {
        UUID id = UUID.randomUUID();
        when(createCategoryUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/danhmuc/LuuDanhMuc")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("code", "DANH_MUC_TEST")
                .param("name", "Danh muc test")
                .param("form", "config_sys"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data").value(id.toString()));

        ArgumentCaptor<CreateCategoryRequest> captor = ArgumentCaptor.forClass(CreateCategoryRequest.class);
        verify(createCategoryUseCase).execute(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("DANH_MUC_TEST");
        assertThat(captor.getValue().getName()).isEqualTo("Danh muc test");
        assertThat(captor.getValue().getForm()).isEqualTo("config_sys");
    }

    @Test
    void luuDanhMuc_withoutCode_shouldFallBackToName() throws Exception {
        when(createCategoryUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/danhmuc/LuuDanhMuc")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Danh muc khong co ma"))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreateCategoryRequest> captor = ArgumentCaptor.forClass(CreateCategoryRequest.class);
        verify(createCategoryUseCase).execute(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("Danh muc khong co ma");
    }

    @Test
    void luuDanhMuc_withId_shouldUpdateCategory() throws Exception {
        UUID id = UUID.randomUUID();
        when(updateCategoryUseCase.execute(eq(id), any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/danhmuc/LuuDanhMuc")
                .param("id", id.toString())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Danh muc cap nhat")
                .param("sort_order", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(id.toString()));

        ArgumentCaptor<UpdateCategoryRequest> captor = ArgumentCaptor.forClass(UpdateCategoryRequest.class);
        verify(updateCategoryUseCase).execute(eq(id), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Danh muc cap nhat");
        assertThat(captor.getValue().getSortOrder()).isEqualTo(5);
    }

    @Test
    void xoaDanhMuc_withId_shouldSoftDeleteCategory() throws Exception {
        UUID id = UUID.randomUUID();
        when(deleteCategoryUseCase.execute(id)).thenReturn(Result.success(id));

        mockMvc.perform(delete("/api/v1/danhmuc/XoaDanhMuc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"deleteoption\":\"trash\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(id.toString()));

        verify(deleteCategoryUseCase).execute(id);
    }

    @Test
    void xoaDanhMuc_withCacheDeleteType_shouldEvictCacheWithoutDeletingCategory() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/danhmuc/XoaDanhMuc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"deletetype\":\"cache\"}"))
            .andExpect(status().isOk());

        verify(categoryCacheService).evictAllListCaches();
        verify(categoryCacheService).evict(id);
        verify(deleteCategoryUseCase, never()).execute(any());
    }

    @Test
    void capNhatCache_shouldEvictListAndByIdCache() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/danhmuc/CapNhatCache")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\"}"))
            .andExpect(status().isOk());

        verify(categoryCacheService).evictAllListCaches();
        verify(categoryCacheService).evict(id);
    }

    @Test
    void capNhatCache_withoutBody_shouldStillEvictListCache() throws Exception {
        mockMvc.perform(post("/api/v1/danhmuc/CapNhatCache"))
            .andExpect(status().isOk());

        verify(categoryCacheService).evictAllListCaches();
        verify(categoryCacheService, never()).evict(any());
    }

    // BL-118 (tiếp, 20/08/2026): tương thích qlvb/danhmuc/LayDanhSachDungChung — dùng lại
    // SearchCategoriesUseCase (BL-113) lọc theo "forms" = keys[0], trả về đúng field name/sort_order/
    // short_name mà các control-select ở màn Nhập mới văn bản đến (SubFormThongTinCoBan.jsx) cần.
    @Test
    void layDanhSachDungChung_shouldDelegateToSearchCategoriesUseCase_filteredByForm() throws Exception {
        var dto = new CategoryDto();
        dto.setId(UUID.randomUUID());
        dto.setName("Công văn");
        dto.setCode("CV");
        dto.setSortOrder(1);
        when(searchCategoriesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(dto), 0, 200, 1L, 1, true, true));

        mockMvc.perform(post("/api/v1/danhmuc/LayDanhSachDungChung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"danhmuc_by_form\",\"keys\":[\"document_type\"],\"pageSize\":1000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("Công văn"))
            .andExpect(jsonPath("$.data[0].short_name").value("CV"))
            .andExpect(jsonPath("$.data[0].sort_order").value(1));

        ArgumentCaptor<SearchCategoriesRequest> captor = ArgumentCaptor.forClass(SearchCategoriesRequest.class);
        verify(searchCategoriesUseCase).execute(captor.capture());
        assertThat(captor.getValue().getForms()).containsExactly("document_type");
        // pageSize gui 1000 (giong FE that) - phai bi cap ve 200 (gioi han cua SearchCategoriesUseCase).
        assertThat(captor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void layDanhSachDungChung_withoutPageSize_shouldDefaultTo200() throws Exception {
        when(searchCategoriesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 200, 0L, 0, true, true));

        mockMvc.perform(post("/api/v1/danhmuc/LayDanhSachDungChung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keys\":[\"priority\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());

        ArgumentCaptor<SearchCategoriesRequest> captor = ArgumentCaptor.forClass(SearchCategoriesRequest.class);
        verify(searchCategoriesUseCase).execute(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(200);
    }

    // Bo sung 09/09/2026 (cutover Footer.tsx/Banner.tsx/ListItemHDSD.tsx) - "content"/"notification"
    // luon co (khong can image=true), "files" CHI xuat hien khi image=true.
    @Test
    void layDanhSachDungChung_shouldAlwaysIncludeContentAndNotification() throws Exception {
        var dto = new CategoryDto();
        dto.setId(UUID.randomUUID());
        dto.setName("Footer he thong");
        dto.setDescription("<p>Noi dung footer</p>");
        dto.setDynamicFields(Map.of("notification", "Bao tri 22h"));
        when(searchCategoriesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(dto), 0, 200, 1L, 1, true, true));

        mockMvc.perform(post("/api/v1/danhmuc/LayDanhSachDungChung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"danhmuc_by_form\",\"keys\":[\"footer\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].content").value("<p>Noi dung footer</p>"))
            .andExpect(jsonPath("$.data[0].notification").value("Bao tri 22h"))
            .andExpect(jsonPath("$.data[0].files").doesNotExist());

        verify(listCategoryFilesUseCase, never()).execute(any());
    }

    @Test
    void layDanhSachDungChung_withImageTrue_shouldEmbedFileContentAsBase64DataUri() throws Exception {
        var dto = new CategoryDto();
        dto.setId(UUID.randomUUID());
        dto.setName("Banner he thong");
        when(searchCategoriesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(dto), 0, 200, 1L, 1, true, true));

        var fileDto = com.td.application.categories.CategoryFileDto.builder()
            .fileId(UUID.randomUUID())
            .fileName("logo.png")
            .originalFileName("logo.png")
            .mimeType("image/png")
            .storagePath("categories/logo.png")
            .build();
        when(listCategoryFilesUseCase.execute(dto.getId()))
            .thenReturn(new PaginationResponse<>(List.of(fileDto), 0, 20, 1L, 1, true, true));
        byte[] bytes = {1, 2, 3};
        when(minioService.getObject("categories/logo.png"))
            .thenReturn(new java.io.ByteArrayInputStream(bytes));

        mockMvc.perform(post("/api/v1/danhmuc/LayDanhSachDungChung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"danhmuc_by_form\",\"keys\":\"banner\",\"search\":{\"isactive\":\"true\"},\"image\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].files[0].content")
                .value("data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes)));

        ArgumentCaptor<SearchCategoriesRequest> captor = ArgumentCaptor.forClass(SearchCategoriesRequest.class);
        verify(searchCategoriesUseCase).execute(captor.capture());
        // Domino gui "keys" dang chuoi don ("banner") - accept-single-value-as-array phai coerce
        // dung thanh List.of("banner").
        assertThat(captor.getValue().getForms()).containsExactly("banner");
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    void layDanhSachDungChung_withImageTrue_andFileReadError_shouldSkipFailingFileNotFailWholeResponse() throws Exception {
        var dto = new CategoryDto();
        dto.setId(UUID.randomUUID());
        dto.setName("Footer loi tep");
        when(searchCategoriesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(dto), 0, 200, 1L, 1, true, true));
        when(listCategoryFilesUseCase.execute(dto.getId()))
            .thenThrow(new RuntimeException("MinIO khong ket noi duoc"));

        mockMvc.perform(post("/api/v1/danhmuc/LayDanhSachDungChung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"danhmuc_by_form\",\"keys\":[\"footer\"],\"image\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].files").isArray())
            .andExpect(jsonPath("$.data[0].files").isEmpty());
    }

    // Bo sung 09/09/2026 (xem javadoc GetLegacyOrgLookupForDungChungUseCase): 3 viewname to chuc/
    // nguoi dung dinh tuyen sang use case rieng, KHONG cham SearchCategoriesUseCase. FE gui "keys"
    // dang CHUOI DON (khong phai mang) - accept-single-value-as-array (application.yml) phai coerce
    // dung thanh List.of("...") truoc khi toi controller.
    @Test
    void layDanhSachDungChung_withViewnameVwCoCauToChuc_shouldDelegateToOrgLookupUseCase() throws Exception {
        UUID parentId = UUID.randomUUID();
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", "org-1");
        row.put("name", "So Noi Vu");
        when(getLegacyOrgLookupForDungChungUseCase.getOrganizationChildren(parentId.toString()))
            .thenReturn(List.of(row));

        mockMvc.perform(post("/api/v1/danhmuc/LayDanhSachDungChung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"vwcocautochuc\",\"keys\":\"" + parentId + "\",\"items\":\"getall\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("So Noi Vu"));

        verify(getLegacyOrgLookupForDungChungUseCase).getOrganizationChildren(parentId.toString());
        verify(searchCategoriesUseCase, never()).execute(any());
    }

    @Test
    void layDanhSachDungChung_withViewnameUserByParentid_shouldDelegateNonRecursive() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(getLegacyOrgLookupForDungChungUseCase.getOrganizationMembers(orgId.toString(), false))
            .thenReturn(List.of());

        mockMvc.perform(post("/api/v1/danhmuc/LayDanhSachDungChung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"user_by_parentid\",\"keys\":\"" + orgId + "\",\"pageSize\":1000}"))
            .andExpect(status().isOk());

        verify(getLegacyOrgLookupForDungChungUseCase).getOrganizationMembers(orgId.toString(), false);
    }

    @Test
    void layDanhSachDungChung_withViewnameUsersByParentids_shouldDelegateRecursive() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(getLegacyOrgLookupForDungChungUseCase.getOrganizationMembers(orgId.toString(), true))
            .thenReturn(List.of());

        mockMvc.perform(post("/api/v1/danhmuc/LayDanhSachDungChung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewname\":\"users_by_parentids\",\"keys\":\"" + orgId + "\",\"pageSize\":1000}"))
            .andExpect(status().isOk());

        verify(getLegacyOrgLookupForDungChungUseCase).getOrganizationMembers(orgId.toString(), true);
    }
}
