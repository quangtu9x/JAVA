package com.td.web.controllers.v1;

import com.td.application.categories.CategoryCacheService;
import com.td.application.categories.CategoryDto;
import com.td.application.categories.CreateCategoryUseCase;
import com.td.application.categories.CreateCategoryRequest;
import com.td.application.categories.DeleteCategoryFileUseCase;
import com.td.application.categories.DeleteCategoryUseCase;
import com.td.application.categories.GetCategoryFileUseCase;
import com.td.application.categories.GetCategoryUseCase;
import com.td.application.categories.ListCategoryFilesUseCase;
import com.td.application.categories.SearchCategoriesUseCase;
import com.td.application.categories.UpdateCategoryUseCase;
import com.td.application.categories.UploadCategoryFileUseCase;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.workflows.WorkflowDefinitionFacade;
import com.td.application.workflows.WorkflowDefinitionSummaryDto;
import com.td.application.workflows.WorkflowPageRequest;
import com.td.application.workflows.WorkflowRuntimeFacade;
import com.td.infrastructure.config.MinioService;
import com.td.web.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-005 (2/4) — Response format check: response JSON của MỌI endpoint đã migrate phải đúng
 * envelope {@code ApiResponse} ({@code status, code, message, data, properties, total}) —
 * kiểm tra trên 2 controller triển khai độc lập ({@link CategoriesController},
 * {@link WorkflowsController}) để xác nhận đây là hành vi chung của {@code BaseController}
 * (BL-003), không phải trùng hợp của riêng 1 controller.
 */
@WebMvcTest(controllers = {CategoriesController.class, WorkflowsController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FeCompatibilityResponseFormatTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @MockBean
    private WorkflowDefinitionFacade workflowDefinitionFacade;
    @MockBean
    private WorkflowRuntimeFacade workflowRuntimeFacade;

    @Test
    void listCategories_shouldReturnEnvelopeWithPaginationProperties() throws Exception {
        CategoryDto category = new CategoryDto();
        category.setId(UUID.randomUUID());
        category.setCode("SNV");
        category.setName("So Noi Vu");

        when(categoryCacheService.getList(any())).thenReturn(null);
        when(searchCategoriesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(category), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].code").value("SNV"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.properties.pageNumber").value(0));
    }

    @Test
    void getCategory_notFound_shouldReturnErrorEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(categoryCacheService.isCachedById(id)).thenReturn(false);
        when(getCategoryUseCase.execute(id)).thenReturn(Result.failure("Khong tim thay danh muc"));

        mockMvc.perform(get("/api/v1/categories/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("Khong tim thay danh muc"));
    }

    @Test
    void createCategory_shouldReturnCreatedEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(createCategoryUseCase.execute(any())).thenReturn(Result.success(id));

        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setCode("SNV");
        request.setName("So Noi Vu");

        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.code").value(201))
            .andExpect(jsonPath("$.data").value(id.toString()));
    }

    @Test
    void listWorkflowDefinitions_shouldUseSameEnvelopeAsCategories() throws Exception {
        WorkflowDefinitionSummaryDto def = new WorkflowDefinitionSummaryDto();
        when(workflowDefinitionFacade.listDefinitions(any(WorkflowPageRequest.class)))
            .thenReturn(new PaginationResponse<>(List.of(def), 0, 10, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/workflows/definitions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.properties.pageNumber").value(0));
    }
}
