package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.sharedcore.AppRoleDto;
import com.td.application.sharedcore.CreateAppRoleUseCase;
import com.td.application.sharedcore.DeleteAppRoleUseCase;
import com.td.application.sharedcore.GetAppRoleUseCase;
import com.td.application.sharedcore.SearchAppRolesUseCase;
import com.td.application.sharedcore.UpdateAppRoleUseCase;
import com.td.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra JSON path của response envelope (BL-003): mọi response phải đi qua
 * ApiResponse {status, code, message, data, properties, total} giống hệt TDMessage cũ.
 */
@WebMvcTest(SharedCoreRolesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SharedCoreRolesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchAppRolesUseCase searchAppRolesUseCase;

    @MockBean
    private GetAppRoleUseCase getAppRoleUseCase;

    // BL-111: SharedCoreRolesController thêm CRUD (POST/PUT/DELETE) — @WebMvcTest cần mock đủ mọi
    // bean được constructor-inject, dù test này chưa viết case riêng cho 3 use case dưới.
    @MockBean
    private CreateAppRoleUseCase createAppRoleUseCase;

    @MockBean
    private UpdateAppRoleUseCase updateAppRoleUseCase;

    @MockBean
    private DeleteAppRoleUseCase deleteAppRoleUseCase;

    @Test
    void listRoles_shouldWrapResponseInApiResponseEnvelope() throws Exception {
        AppRoleDto role = new AppRoleDto();
        role.setId(UUID.randomUUID());
        role.setCode("ADMIN");
        role.setName("Administrator");

        when(searchAppRolesUseCase.execute(any())).thenReturn(
            new PaginationResponse<>(List.of(role), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/v1/shared-core/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.message").value(""))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].code").value("ADMIN"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.properties.pageNumber").value(0))
            .andExpect(jsonPath("$.properties.totalPages").value(1));
    }

    @Test
    void getRole_shouldReturnErrorEnvelope_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(getAppRoleUseCase.execute(id)).thenReturn(Result.failure("Không tìm thấy vai trò với ID: " + id));

        mockMvc.perform(get("/api/v1/shared-core/roles/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("Không tìm thấy vai trò với ID: " + id))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getRole_shouldReturnDataEnvelope_whenFound() throws Exception {
        UUID id = UUID.randomUUID();
        AppRoleDto role = new AppRoleDto();
        role.setId(id);
        role.setCode("USER");
        role.setName("User");

        when(getAppRoleUseCase.execute(id)).thenReturn(Result.success(role));

        mockMvc.perform(get("/api/v1/shared-core/roles/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.data.code").value("USER"));
    }
}
