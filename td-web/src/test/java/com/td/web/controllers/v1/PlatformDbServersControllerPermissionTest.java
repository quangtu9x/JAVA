package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.common.models.Result;
import com.td.application.dbserver.ActivateDbServerUseCase;
import com.td.application.dbserver.CreateDbServerUseCase;
import com.td.application.dbserver.DbServerDto;
import com.td.application.dbserver.DeactivateDbServerUseCase;
import com.td.application.dbserver.DeleteDbServerUseCase;
import com.td.application.dbserver.GetDbServerCredentialUseCase;
import com.td.application.dbserver.GetDbServerUseCase;
import com.td.application.dbserver.ListDbServersUseCase;
import com.td.application.dbserver.TestDbServerConnectionUseCase;
import com.td.application.dbserver.UpdateDbServerUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.infrastructure.security.ApiResponseAccessDeniedHandler;
import com.td.infrastructure.security.ApiResponseAuthenticationEntryPoint;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// BL-117 (bổ sung 19/08/2026): registry "Máy chủ CSDL" cũng là dữ liệu NỀN TẢNG (control-plane, dùng
// chung mọi tenant) — cùng phạm vi quyền PLATFORM_ADMIN với PlatformTenantsController, xem lý do đầy
// đủ ở PlatformTenantsControllerPermissionTest (cùng convention test qua filter chain thật).
@WebMvcTest(controllers = PlatformDbServersController.class)
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
    TenantFilter.class
})
@TestPropertySource(properties = "app.security.tdtoken.secret=bl-117-platform-dbservers-test-secret-please-ignore")
class PlatformDbServersControllerPermissionTest {

    private static final String SECRET = "bl-117-platform-dbservers-test-secret-please-ignore";

    @Autowired
    private MockMvc mockMvc;

    // BL-118 (20/08/2026): KeycloakJwtConverter nay inject them use case nay de tra cuu quyen Nhom
    // vai tro theo username Keycloak — @WebMvcTest phai mock de context khoi dong duoc.
    @MockBean
    private GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;

    // BL-118 (20/08/2026): KeycloakJwtConverter/TenantResolver nay inject them use case nay de tra
    // cuu tenant that cua username - @WebMvcTest phai mock de context khoi dong duoc.
    @MockBean
    private ResolveUserTenantUseCase resolveUserTenantUseCase;

    @MockBean
    private CreateDbServerUseCase createDbServerUseCase;
    @MockBean
    private UpdateDbServerUseCase updateDbServerUseCase;
    @MockBean
    private GetDbServerUseCase getDbServerUseCase;
    @MockBean
    private ListDbServersUseCase listDbServersUseCase;
    @MockBean
    private ActivateDbServerUseCase activateDbServerUseCase;
    @MockBean
    private DeactivateDbServerUseCase deactivateDbServerUseCase;
    @MockBean
    private DeleteDbServerUseCase deleteDbServerUseCase;
    @MockBean
    private GetDbServerCredentialUseCase getDbServerCredentialUseCase;
    @MockBean
    private TestDbServerConnectionUseCase testDbServerConnectionUseCase;

    @Test
    void listDbServers_withPlatformAdminRole_shouldReturn200() throws Exception {
        when(listDbServersUseCase.execute()).thenReturn(Result.success(List.<DbServerDto>of()));

        mockMvc.perform(get("/api/v1/platform/db-servers")
                .header("Tdtoken", buildToken("root01", "PLATFORM_ADMIN")))
            .andExpect(status().isOk());
    }

    // Trọng tâm: ADMIN thường (quyền cao nhất TRONG 1 tenant) KHÔNG được tự động có quyền quản lý
    // registry máy chủ CSDL nền tảng.
    @Test
    void listDbServers_withRegularAdminRole_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/platform/db-servers")
                .header("Tdtoken", buildToken("admin01", "ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void listDbServers_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/platform/db-servers"))
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
