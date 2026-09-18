package com.td.web.controllers.v1;

import com.td.application.categories.CategoryCacheService;
import com.td.application.categories.CategoryDto;
import com.td.application.categories.CategoryRepository;
import com.td.application.categories.CreateCategoryRequest;
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
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.infrastructure.config.MinioService;
import com.td.web.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-105 (DanhMuc Module) — Definition of Done (2) / Tiêu chí kiểm thử chính xác:
 * "Gọi list categories → cache MISS lần đầu, HIT lần 2. Tạo category mới → cache evict → list trả
 * về dữ liệu mới." Test này dùng {@link CreateCategoryUseCase} và {@link CategoryCacheService}
 * THẬT (không mock) — chỉ mock {@link CategoryRepository} — để chứng minh việc tạo category thật
 * sự xoá cache list qua {@code categoryCacheService.evictAllListCaches()} (không chỉ giả lập bằng
 * mock use case, vốn sẽ không chạy code eviction thật).
 */
@WebMvcTest(controllers = CategoriesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, CreateCategoryUseCase.class, CategoriesControllerCacheIntegrationTest.RealCacheConfig.class})
class CategoriesControllerCacheIntegrationTest {

    @TestConfiguration
    static class RealCacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                CategoryCacheService.CATEGORY_BY_ID_CACHE, CategoryCacheService.CATEGORY_LIST_CACHE);
        }

        @Bean
        CategoryCacheService categoryCacheService(CacheManager cacheManager) {
            return new CategoryCacheService(cacheManager);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryRepository categoryRepository;
    @MockBean
    private SearchCategoriesUseCase searchCategoriesUseCase;
    @MockBean
    private UpdateCategoryUseCase updateCategoryUseCase;
    @MockBean
    private DeleteCategoryUseCase deleteCategoryUseCase;
    @MockBean
    private GetCategoryUseCase getCategoryUseCase;

    // BL-110 da them dependency nay vao CreateCategoryUseCase (Dynamic Data Engine) sau khi test
    // nay duoc viet (BL-105) - thieu @MockBean khien Spring khong dung duoc bean CreateCategoryUseCase
    // that (@Import truc tiep, khong mock) - phat hien khi ra soat lai toan bo test o BL-117.
    @MockBean
    private DynamicDocumentFieldsService dynamicDocumentFieldsService;

    // BL-118: CategoriesController thêm 5 dependency mới (tệp đính kèm Category dùng chung) — cùng
    // lý do phải @MockBean như DynamicDocumentFieldsService ở trên (@WebMvcTest cần mock ĐỦ mọi
    // dependency constructor của controller, không chỉ những cái test này thực sự dùng tới).
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

    @Autowired
    private CategoryCacheService categoryCacheService;

    @BeforeEach
    void clearCacheBetweenTests() {
        // Spring TestContext dung chung 1 ApplicationContext (va do do 1 CacheManager that) giua cac
        // test method trong cung 1 class - phai xoa cache truoc moi test de dam bao trang thai doc lap.
        categoryCacheService.evictAllListCaches();
    }

    @Test
    void listCategories_shouldMissThenHitCache() throws Exception {
        stubSearchWithOneItem();

        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Cache", "MISS"))
            .andExpect(jsonPath("$.data[0].name").value("Danh muc A"));

        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Cache", "HIT"))
            .andExpect(jsonPath("$.data[0].name").value("Danh muc A"));

        // SearchCategoriesUseCase chi duoc goi 1 lan - lan 2 phuc vu tu cache, khong cham repository/use case
        verify(searchCategoriesUseCase, times(1)).execute(any());
    }

    @Test
    void createCategory_shouldEvictListCache_soNextListIsFreshMissAgain() throws Exception {
        stubSearchWithOneItem();

        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(header().string("X-Cache", "MISS"));
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(header().string("X-Cache", "HIT"));

        when(categoryRepository.existsByCodeAndDeletedOnIsNull(any())).thenReturn(false);
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var createRequest = new CreateCategoryRequest();
        createRequest.setCode("DANH_MUC_B");
        createRequest.setName("Danh muc B");

        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated());

        // Cache list da bi evict boi CreateCategoryUseCase that (khong mock) -> phai la MISS moi, khong con HIT
        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Cache", "MISS"));

        verify(searchCategoriesUseCase, times(2)).execute(any());
    }

    private void stubSearchWithOneItem() {
        CategoryDto dto = new CategoryDto();
        dto.setId(UUID.randomUUID());
        dto.setName("Danh muc A");
        when(searchCategoriesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(dto), 0, 20, 1L, 1, true, true));
    }
}
