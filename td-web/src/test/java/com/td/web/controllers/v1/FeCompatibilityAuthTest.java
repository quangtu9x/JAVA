package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.categories.CategoryCacheService;
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
import com.td.infrastructure.config.MinioService;
import com.td.application.sharedcore.AppRoleDto;
import com.td.application.sharedcore.CreateAppRoleUseCase;
import com.td.application.sharedcore.DeleteAppRoleUseCase;
import com.td.application.sharedcore.GetAppRoleUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.sharedcore.SearchAppRolesUseCase;
import com.td.application.sharedcore.UpdateAppRoleUseCase;
import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.infrastructure.security.ApiResponseAccessDeniedHandler;
import com.td.infrastructure.security.ApiResponseAuthenticationEntryPoint;
import com.td.infrastructure.security.AccountProvisioningChecker;
import com.td.infrastructure.security.AccountProvisioningFilter;
import com.td.infrastructure.security.CorsProperties;
import com.td.infrastructure.security.KeycloakJwtConverter;
import com.td.infrastructure.security.KeycloakProperties;
import com.td.infrastructure.security.SecurityConfig;
import com.td.infrastructure.security.TdTokenAuthenticationFilter;
import com.td.infrastructure.security.TdTokenProperties;
import com.td.infrastructure.security.TdTokenService;
import com.td.infrastructure.tenant.TenantActiveRegistry;
import com.td.infrastructure.tenant.TenantFilter;
import com.td.infrastructure.tenant.TenantProperties;
import com.td.infrastructure.tenant.TenantResolver;
import com.td.web.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-005 (3/4) — Auth check qua filter chain thật (không mock security), bổ sung các tình huống
 * thực tế phát hiện khi đọc trực tiếp {@code ThanhHoaDEV/src/utils/baseAPI.js} mà
 * {@code TdTokenAuthenticationFilterIntegrationTest} (BL-004) chưa phủ:
 * <ul>
 *   <li>FE gửi header <b>{@code TDtoken}</b> (hoa T-D), không phải {@code Tdtoken} —
 *       xem `docs/frontend/00-tong-quan.md` mục "Ghi chú quan trọng". HTTP header không phân biệt
 *       hoa/thường nên phải hoạt động giống hệt.</li>
 *   <li>{@code requestPOSTStream} trong {@code baseAPI.js} gửi <b>đồng thời</b> cả
 *       {@code TDtoken} và {@code Authorization: Bearer} — phải ưu tiên luồng Keycloak, không được
 *       để Tdtoken "âm thầm" xác thực thay.</li>
 *   <li>Lỗi validate nghiệp vụ (400) <b>không được</b> mang message {@code "Sai token"} — nếu không
 *       FE sẽ hiểu nhầm thành lỗi token và kích hoạt luồng tự làm mới token
 *       ({@code baseAPI.js} so khớp chuỗi chính xác message này).</li>
 * </ul>
 */
@WebMvcTest(controllers = {SharedCoreRolesController.class, CategoriesController.class})
@Import({
    SecurityConfig.class,
    KeycloakJwtConverter.class,
    KeycloakProperties.class,
    CorsProperties.class,
    TdTokenProperties.class,
    TdTokenService.class,
    TdTokenAuthenticationFilter.class,
    ApiResponseAuthenticationEntryPoint.class,
    ApiResponseAccessDeniedHandler.class,
    GlobalExceptionHandler.class,
    TenantProperties.class,
    TenantActiveRegistry.class,
    TenantResolver.class,
    TenantFilter.class,
    AccountProvisioningFilter.class
})
@TestPropertySource(properties = "app.security.tdtoken.secret=bl-005-fe-compatibility-secret-please-ignore")
class FeCompatibilityAuthTest {

    private static final String SECRET = "bl-005-fe-compatibility-secret-please-ignore";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void stubAccountProvisioningChecker() {
        when(accountProvisioningChecker.isProvisioned(any(), any())).thenReturn(true);
    }

    @Autowired
    private ObjectMapper objectMapper;

    // BL-118 (20/08/2026): KeycloakJwtConverter nay inject them use case nay de tra cuu quyen Nhom
    // vai tro theo username Keycloak — @WebMvcTest phai mock de context khoi dong duoc.
    @MockBean
    private GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;

    // BL-118 (20/08/2026): KeycloakJwtConverter/TenantResolver nay inject them use case nay de tra
    // cuu tenant that cua username - @WebMvcTest phai mock de context khoi dong duoc.
    @MockBean
    private ResolveUserTenantUseCase resolveUserTenantUseCase;

    // "Khoa triet de" (22/09/2026): AccountProvisioningFilter (chan MOI request tu tai khoan chua
    // khai bao) nay la 1 phan cua SecurityConfig filter chain - @WebMvcTest phai mock de context
    // khoi dong duoc. Stub mac dinh true (@BeforeEach duoi) vi cac test trong file nay kiem tra
    // PHAN QUYEN theo role, khong phai provisioning - khong duoc de gate moi nay chan nham.
    @MockBean
    private AccountProvisioningChecker accountProvisioningChecker;

    @MockBean
    private SearchAppRolesUseCase searchAppRolesUseCase;
    @MockBean
    private GetAppRoleUseCase getAppRoleUseCase;
    // BL-111 da them 3 use case nay vao SharedCoreRolesController sau khi test nay duoc viet (BL-005)
    // - thieu @MockBean khien @WebMvcTest khong dung duoc bean constructor cho controller (phat hien
    // khi ra soat lai toan bo test tenant o BL-117, sua luon vi cung file dang sua).
    @MockBean
    private CreateAppRoleUseCase createAppRoleUseCase;
    @MockBean
    private UpdateAppRoleUseCase updateAppRoleUseCase;
    @MockBean
    private DeleteAppRoleUseCase deleteAppRoleUseCase;

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

    // BL-118: CategoriesController thêm 5 dependency mới (tệp đính kèm Category dùng chung) — cùng
    // lý do phải @MockBean như các use case Role ở trên.
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
    void mixedCaseTDtokenHeader_shouldAuthenticate_sameAsLowercase() throws Exception {
        when(searchAppRolesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.<AppRoleDto>of(), 0, 20, 0L, 0, true, true));

        // baseAPI.js gửi "TDtoken" (hoa T-D), không phải "Tdtoken" — xem docs/frontend/00-tong-quan.md
        String token = buildToken("nguyenvana", "USER", Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/v1/shared-core/roles").header("TDtoken", token))
            .andExpect(status().isOk());
    }

    @Test
    void tdtokenAndAuthorizationBothPresent_shouldPreferKeycloak_notSilentlyAcceptTdtoken() throws Exception {
        // baseAPI.js#requestPOSTStream gửi đồng thời cả 2 header. Authorization (dù là token hỏng,
        // không cần Keycloak thật) phải được ưu tiên — Tdtoken hợp lệ không được "cứu" request này.
        String validTdtoken = buildToken("nguyenvana", "USER", Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/v1/shared-core/roles")
                .header("TDtoken", validTdtoken)
                .header("Authorization", "Bearer malformed-not-a-real-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").isNotEmpty())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not("Sai token")));
    }

    @Test
    void validationError_shouldNotReturnSaiTokenMessage() throws Exception {
        // Thiếu field bắt buộc (code, name) → 400 do @Valid, KHÔNG được trùng message "Sai token"
        // (baseAPI.js so khớp message này để kích hoạt refresh token — không liên quan gì đến lỗi này).
        CreateCategoryRequest invalidRequest = new CreateCategoryRequest();

        mockMvc.perform(post("/api/v1/categories")
                .header("Tdtoken", buildToken("nguyenvana", "USER", Instant.now().plusSeconds(3600)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not("Sai token")));
    }

    private static String buildToken(String username, String roles, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(username)
            .claim("username", username)
            .claim("roles", roles)
            .expirationTime(Date.from(expiresAt))
            .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new MACSigner(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return signedJWT.serialize();
    }
}
