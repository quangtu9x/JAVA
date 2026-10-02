package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.common.models.PaginationResponse;
import com.td.application.sharedcore.AppRoleDto;
import com.td.application.sharedcore.CreateAppRoleUseCase;
import com.td.application.sharedcore.DeleteAppRoleUseCase;
import com.td.application.sharedcore.SearchAppRolesUseCase;
import com.td.application.sharedcore.GetAppRoleUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra Definition of Done của BL-004 ở tầng HTTP thật (qua filter chain thật, không mock
 * security): (1) Tdtoken hợp lệ → 200. (2) Không có token nào → 401. (3) Tdtoken hết hạn → 401.
 * (4) Tdtoken sai chữ ký → 401. Response 401 phải theo envelope {@code ApiResponse}.
 *
 * <p>Đặt cùng package với {@link SharedCoreRolesControllerTest} để tái dùng
 * {@link WebSliceTestApplication} — {@code @WebMvcTest} chỉ scan được controller nếu
 * {@code @SpringBootConfiguration} gần nhất theo package bao trùm nó.
 */
@WebMvcTest(controllers = SharedCoreRolesController.class)
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
    TenantProperties.class,
    TenantActiveRegistry.class,
    TenantResolver.class,
    TenantFilter.class,
    AccountProvisioningFilter.class
})
@TestPropertySource(properties = "app.security.tdtoken.secret=bl-004-integration-test-secret-please-ignore")
class TdTokenAuthenticationFilterIntegrationTest {

    private static final String SECRET = "bl-004-integration-test-secret-please-ignore";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void stubAccountProvisioningChecker() {
        when(accountProvisioningChecker.isProvisioned(any(), any())).thenReturn(true);
    }

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

    // BL-111 da them 3 use case nay vao SharedCoreRolesController sau khi test nay duoc viet (BL-004)
    // - thieu @MockBean khien @WebMvcTest khong dung duoc bean constructor cho controller (phat hien
    // khi ra soat lai toan bo test tenant o BL-117, sua luon vi cung file dang sua).
    @MockBean
    private CreateAppRoleUseCase createAppRoleUseCase;

    @MockBean
    private UpdateAppRoleUseCase updateAppRoleUseCase;

    @MockBean
    private DeleteAppRoleUseCase deleteAppRoleUseCase;

    @Test
    void request_withValidTdtoken_shouldReturn200() throws Exception {
        when(searchAppRolesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.<AppRoleDto>of(), 0, 20, 0L, 0, true, true));

        String token = buildToken("nguyenvana", "USER;ADMIN", Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/v1/shared-core/roles").header("Tdtoken", token))
            .andExpect(status().isOk());
    }

    @Test
    void request_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/shared-core/roles"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void request_withExpiredTdtoken_shouldReturn401WithSaiTokenMessage() throws Exception {
        String token = buildToken("nguyenvana", "USER", Instant.now().minusSeconds(3600));

        mockMvc.perform(get("/api/v1/shared-core/roles").header("Tdtoken", token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.message").value("Sai token"));
    }

    @Test
    void request_withTamperedTdtoken_shouldReturn401() throws Exception {
        String token = buildToken("nguyenvana", "USER", Instant.now().plusSeconds(3600));
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/api/v1/shared-core/roles").header("Tdtoken", tampered))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Sai token"));
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
