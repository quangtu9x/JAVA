package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.common.models.Result;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.tenant.ActivateTenantUseCase;
import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.application.tenant.CreateTenantUseCase;
import com.td.application.tenant.DeactivateTenantUseCase;
import com.td.application.tenant.GetTenantUseCase;
import com.td.application.tenant.ListTenantsUseCase;
import com.td.application.tenant.TenantDto;
import com.td.application.tenant.TestTenantConnectionUseCase;
import com.td.application.tenant.UpdateTenantUseCase;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// BL-117: PlatformTenantsController là thao tác NỀN TẢNG (platform-level) — PHẢI chỉ cho phép role
// PLATFORM_ADMIN (role MỚI), KHÔNG được để ADMIN thường (quyền cao nhất TRONG 1 tenant) tự động có
// quyền xem/tạo/khoá tenant KHÁC. Test này xác nhận qua filter chain thật (giống *PermissionIntegrationTest
// khác của project) — không mock security.
@WebMvcTest(controllers = PlatformTenantsController.class)
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
@TestPropertySource(properties = "app.security.tdtoken.secret=bl-117-platform-tenants-test-secret-please-ignore")
class PlatformTenantsControllerPermissionTest {

    private static final String SECRET = "bl-117-platform-tenants-test-secret-please-ignore";

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
    private CreateTenantUseCase createTenantUseCase;
    @MockBean
    private UpdateTenantUseCase updateTenantUseCase;
    @MockBean
    private GetTenantUseCase getTenantUseCase;
    @MockBean
    private ListTenantsUseCase listTenantsUseCase;
    @MockBean
    private ActivateTenantUseCase activateTenantUseCase;
    @MockBean
    private DeactivateTenantUseCase deactivateTenantUseCase;
    @MockBean
    private TestTenantConnectionUseCase testTenantConnectionUseCase;

    @Test
    void listTenants_withPlatformAdminRole_shouldReturn200() throws Exception {
        when(listTenantsUseCase.execute()).thenReturn(Result.success(List.<TenantDto>of()));

        mockMvc.perform(get("/api/v1/platform/tenants")
                .header("Tdtoken", buildToken("root01", "PLATFORM_ADMIN")))
            .andExpect(status().isOk());
    }

    // Trọng tâm của test này: ADMIN (quyền cao nhất TRONG 1 tenant) KHÔNG được tự động có quyền
    // nền tảng — khác hẳn convention "hasAnyRole('ADMIN', ...)" dùng cho CRUD nghiệp vụ thường.
    @Test
    void listTenants_withRegularAdminRole_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants")
                .header("Tdtoken", buildToken("admin01", "ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void listTenants_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants"))
            .andExpect(status().isUnauthorized());
    }

    private static String buildToken(String username, String roles) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(username)
            .claim("username", username)
            .claim("roles", roles)
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new MACSigner(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return signedJWT.serialize();
    }
}
