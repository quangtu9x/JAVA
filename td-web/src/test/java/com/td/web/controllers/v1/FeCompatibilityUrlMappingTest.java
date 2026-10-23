package com.td.web.controllers.v1;

import com.td.application.categories.CategoryCacheService;
import com.td.application.categories.CreateCategoryUseCase;
import com.td.application.categories.DeleteCategoryFileUseCase;
import com.td.application.categories.DeleteCategoryUseCase;
import com.td.application.categories.GetCategoryFileUseCase;
import com.td.application.categories.GetCategoryUseCase;
import com.td.application.categories.ListCategoryFilesUseCase;
import com.td.application.categories.SearchCategoriesUseCase;
import com.td.application.categories.UpdateCategoryUseCase;
import com.td.application.categories.UploadCategoryFileUseCase;
import com.td.application.common.models.PaginationResponse;
import com.td.application.sharedcore.CreateOrganizationUseCase;
import com.td.application.sharedcore.DeleteOrganizationUseCase;
import com.td.application.sharedcore.GetCapBanHanhOptionsUseCase;
import com.td.application.sharedcore.GetKyThuaLenhSignersUseCase;
import com.td.application.sharedcore.GetLegacyOrganizationFlatListUseCase;
import com.td.application.sharedcore.GetLegacyOrganizationTreeUseCase;
import com.td.application.sharedcore.GetLegacyOrgLookupForDungChungUseCase;
import com.td.application.sharedcore.LegacyOrganizationTreeResponse;
import com.td.application.sharedcore.UpdateOrganizationUseCase;
import com.td.application.viewquery.ViewQueryService;
import com.td.application.workcalendars.DeleteWorkCalendarUseCase;
import com.td.application.workcalendars.GetWeekWorkCalendarUseCase;
import com.td.application.workcalendars.SaveWorkCalendarUseCase;
import com.td.infrastructure.config.MinioService;
import com.td.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-005 (1/4) — URL mapping check: mỗi module Domino đã migrate phải có endpoint Spring Boot
 * tương ứng đúng path mà FE hiện đang gọi.
 *
 * <p>Đối chiếu `docs/migration/03-danh-muc-api-endpoint.md` (URL key {@code qlvb/<key>}):
 * <ul>
 *   <li>{@code qlvb/danhmuc} (DanhMucController, MappingCommon) → {@code /api/v1/danhmuc/*}
 *       ({@link LegacyDanhMucController}) — action {@code CayCoCauToChuc} là action danh mục
 *       phổ biến nhất theo FE (`docs/frontend/04-qlvb-danhmuc-quantri.md`).</li>
 *   <li>{@code qlvb/danhmuc}/{@code qlvb/luudanhmuc} (CRUD danh mục) → {@code /api/v1/categories}
 *       ({@link CategoriesController}) — API chính thức thay thế LegacyDanhMuc về lâu dài (BL-105).</li>
 * </ul>
 *
 * <p>Chỉ test được @ AutoConfigureMockMvc(addFilters = false) vì mục tiêu ở đây là xác nhận
 * <b>route tồn tại đúng path</b> (không 404), việc xác thực đã có {@link FeCompatibilityAuthTest} riêng.
 */
@WebMvcTest(controllers = {LegacyDanhMucController.class, CategoriesController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FeCompatibilityUrlMappingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetLegacyOrganizationTreeUseCase getLegacyOrganizationTreeUseCase;
    @MockBean
    private com.td.application.sharedcore.OrganizationTreeCache organizationTreeCache;
    @MockBean
    private GetLegacyOrganizationFlatListUseCase getLegacyOrganizationFlatListUseCase;
    @MockBean
    private GetLegacyOrgLookupForDungChungUseCase getLegacyOrgLookupForDungChungUseCase;
    @MockBean
    private com.td.application.sharedcore.SearchLegacyDanhMucUseCase searchLegacyDanhMucUseCase;
    // 09/10/2026: LegacyDanhMucController nhom_*_by_identifier (xin y kien thanh vien).
    @MockBean
    private com.td.application.privatecatalog.GetGroupPeopleByTypeUseCase getGroupPeopleByTypeUseCase;
    @MockBean
    private CreateOrganizationUseCase createOrganizationUseCase;
    @MockBean
    private UpdateOrganizationUseCase updateOrganizationUseCase;
    @MockBean
    private DeleteOrganizationUseCase deleteOrganizationUseCase;
    @MockBean
    private GetCapBanHanhOptionsUseCase getCapBanHanhOptionsUseCase;
    @MockBean
    private GetKyThuaLenhSignersUseCase getKyThuaLenhSignersUseCase;

    @MockBean
    private CreateCategoryUseCase createCategoryUseCase;
    @MockBean
    private UpdateCategoryUseCase updateCategoryUseCase;
    @MockBean
    private DeleteCategoryUseCase deleteCategoryUseCase;
    @MockBean
    private GetCategoryUseCase getCategoryUseCase;
    @MockBean
    private SearchCategoriesUseCase searchCategoriesUseCase;
    @MockBean
    private CategoryCacheService categoryCacheService;
    @MockBean
    private ViewQueryService viewQueryService;

    // Port lichct (08/09/2026) - can @MockBean cho 3 dependency moi cua LegacyDanhMucController.
    @MockBean
    private GetWeekWorkCalendarUseCase getWeekWorkCalendarUseCase;
    @MockBean
    private SaveWorkCalendarUseCase saveWorkCalendarUseCase;
    @MockBean
    private DeleteWorkCalendarUseCase deleteWorkCalendarUseCase;

    // BL-118: CategoriesController thêm 5 dependency mới (tệp đính kèm Category dùng chung).
    @MockBean
    private UploadCategoryFileUseCase uploadCategoryFileUseCase;
    @MockBean
    private ListCategoryFilesUseCase listCategoryFilesUseCase;
    @MockBean
    private GetCategoryFileUseCase getCategoryFileUseCase;
    @MockBean
    private DeleteCategoryFileUseCase deleteCategoryFileUseCase;
    @MockBean
    private MinioService minioService;

    @Test
    void legacyDanhMuc_CayCoCauToChuc_shouldMapToDocumentedPath() throws Exception {
        when(getLegacyOrganizationTreeUseCase.execute(any(), anyBoolean())).thenReturn(new LegacyOrganizationTreeResponse());

        // qlvb/danhmuc/CayCoCauToChuc (Domino) → /api/v1/danhmuc/CayCoCauToChuc (Spring Boot)
        mockMvc.perform(get("/api/v1/danhmuc/CayCoCauToChuc"))
            .andExpect(status().isOk());
    }

    @Test
    void legacyDanhMuc_DanhSachCoCauToChuc_shouldMapToDocumentedPath() throws Exception {
        when(getLegacyOrganizationFlatListUseCase.execute()).thenReturn(List.of());

        // qlvb/danhmuc/DanhSachCoCauToChuc?databasetype=vbden (Domino) →
        // /api/v1/danhmuc/DanhSachCoCauToChuc (Spring Boot)
        mockMvc.perform(post("/api/v1/danhmuc/DanhSachCoCauToChuc"))
            .andExpect(status().isOk());
    }

    @Test
    void categories_list_shouldMapToDocumentedPath() throws Exception {
        when(searchCategoriesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 20, 0L, 0, true, true));
        when(categoryCacheService.getList(any())).thenReturn(null);

        // qlvb/danhmuc, qlvb/luudanhmuc (Domino) → /api/v1/categories (Spring Boot, BL-105)
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk());
    }
}
