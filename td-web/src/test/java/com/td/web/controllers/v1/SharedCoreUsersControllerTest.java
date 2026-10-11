package com.td.web.controllers.v1;

import com.td.application.common.models.Result;
import com.td.application.sharedcore.CheckUsernameExistsUseCase;
import com.td.application.sharedcore.CreateAppUserUseCase;
import com.td.application.sharedcore.DeleteAppUserUseCase;
import com.td.application.sharedcore.GetAppUserUseCase;
import com.td.application.sharedcore.GetKiemNhiemPendingCountUseCase;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.sharedcore.GetUserSignatureImagesUseCase;
import com.td.application.sharedcore.ListUserDirectoryUseCase;
import com.td.application.sharedcore.LockAppUsersUseCase;
import com.td.application.sharedcore.SearchAppUsersUseCase;
import com.td.application.sharedcore.SwitchUserUseCase;
import com.td.application.sharedcore.TransferAppUsersUseCase;
import com.td.application.sharedcore.UnlockAppUsersUseCase;
import com.td.application.sharedcore.UpdateAppUserUseCase;
import com.td.application.sharedcore.UploadUserSignatureImageUseCase;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.application.sharedcore.UserRoleGroupConfigDto;
import com.td.domain.sharedcore.AppUser;
import com.td.infrastructure.security.TdTokenService;
import com.td.web.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-118 (tiếp, 6 — 20/08/2026): {@code organizationId}/{@code level} nay BẮT BUỘC khi tạo người
 * dùng — trước đây tuỳ chọn, hậu quả: user tạo không gán tổ chức/cấp khiến {@code qlvb/profile} trả
 * {@code identifiers}/{@code level} rỗng, làm gãy tính năng "chọn Sổ văn bản" (và các hàm cùng họ
 * trong {@code utils/global.jsx}) phía FE. Test này khoá lại: request thiếu 1 trong 2 field PHẢI bị
 * từ chối (400) trước khi tới {@link CreateAppUserUseCase}.
 */
@WebMvcTest(controllers = SharedCoreUsersController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SharedCoreUsersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchAppUsersUseCase searchAppUsersUseCase;
    @MockBean
    private GetAppUserUseCase getAppUserUseCase;
    @MockBean
    private CreateAppUserUseCase createAppUserUseCase;
    @MockBean
    private UpdateAppUserUseCase updateAppUserUseCase;
    @MockBean
    private DeleteAppUserUseCase deleteAppUserUseCase;
    @MockBean
    private LockAppUsersUseCase lockAppUsersUseCase;
    @MockBean
    private UnlockAppUsersUseCase unlockAppUsersUseCase;
    @MockBean
    private TransferAppUsersUseCase transferAppUsersUseCase;
    @MockBean
    private CheckUsernameExistsUseCase checkUsernameExistsUseCase;
    @MockBean
    private ListUserDirectoryUseCase listUserDirectoryUseCase;
    @MockBean
    private GetKiemNhiemPendingCountUseCase getKiemNhiemPendingCountUseCase;
    @MockBean
    private SwitchUserUseCase switchUserUseCase;
    @MockBean
    private GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    @MockBean
    private GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;
    @MockBean
    private TdTokenService tdTokenService;
    @MockBean
    private UploadUserSignatureImageUseCase uploadUserSignatureImageUseCase;
    @MockBean
    private GetUserSignatureImagesUseCase getUserSignatureImagesUseCase;

    @Test
    void createUser_withoutOrganizationIdAndLevel_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/shared-core/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nhanvien01\",\"fullName\":\"Nhan vien 1\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_withoutOrganizationId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/shared-core/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nhanvien01\",\"fullName\":\"Nhan vien 1\",\"level\":3}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_withoutLevel_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/shared-core/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nhanvien01\",\"fullName\":\"Nhan vien 1\",\"organizationId\":\""
                    + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_withOrganizationIdAndLevel_shouldReturn201() throws Exception {
        UUID id = UUID.randomUUID();
        when(createAppUserUseCase.execute(any())).thenReturn(Result.success(id));

        mockMvc.perform(post("/api/v1/shared-core/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nhanvien01\",\"fullName\":\"Nhan vien 1\",\"organizationId\":\""
                    + UUID.randomUUID() + "\",\"level\":3}"))
            .andExpect(status().isCreated());
    }

    // Port TDSwitchUser (docs/analysis/19-checklist-port-tdswitchuser-domino-sang-java.md, 08/09/2026).
    @Test
    void switchUser_withUnknownUsername_shouldReturn400() throws Exception {
        when(switchUserUseCase.execute("ghost")).thenReturn(Result.failure("Không tìm thấy tài khoản: ghost"));

        mockMvc.perform(post("/api/v1/shared-core/users/switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ghost\",\"ma\":\"123@!@#Aa\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void switchUser_withoutSecurityCode_shouldReturn400BeforeUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/shared-core/users/switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nhanvien02\"}"))
            .andExpect(status().isBadRequest());

        verify(switchUserUseCase, never()).execute(any());
    }

    @Test
    void switchUser_withInvalidSecurityCode_shouldReturn400BeforeUseCase() throws Exception {
        mockMvc.perform(post("/api/v1/shared-core/users/switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nhanvien02\",\"ma\":\"sai-ma\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Mã bảo mật không đúng"));

        verify(switchUserUseCase, never()).execute(any());
    }

    @Test
    void switchUser_withValidUsername_shouldReturn200WithTdtoken() throws Exception {
        AppUser target = new AppUser("kc-target", "nhanvien02", "Nhan vien 2", null, null, null, true);
        when(switchUserUseCase.execute("nhanvien02")).thenReturn(Result.success(target));

        var orgProfile = new UserOrganizationProfileDto();
        orgProfile.setUsername("nhanvien02");
        orgProfile.setFullName("Nhan vien 2");
        when(getUserOrganizationProfileUseCase.execute("nhanvien02")).thenReturn(orgProfile);

        var roleGroupConfig = new UserRoleGroupConfigDto();
        when(getUserRoleGroupConfigUseCase.execute("nhanvien02")).thenReturn(roleGroupConfig);

        when(tdTokenService.issue(any(), any(Duration.class), any())).thenReturn("fake-tdtoken");

        mockMvc.perform(post("/api/v1/shared-core/users/switch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nhanvien02\",\"ma\":\"123@!@#Aa\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tdtoken").value("fake-tdtoken"))
            .andExpect(jsonPath("$.data.username").value("nhanvien02"));
    }
}
