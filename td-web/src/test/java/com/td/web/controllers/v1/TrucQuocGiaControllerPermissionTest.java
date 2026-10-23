package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.common.models.Result;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.application.tonghop.national.GetNationalExchangeAccessUseCase;
import com.td.application.tonghop.national.GetNationalExchangeDetailUseCase;
import com.td.application.tonghop.national.NationalExchangeAccess;
import com.td.application.tonghop.national.NationalExchangeSearchResult;
import com.td.application.tonghop.national.SearchNationalExchangesUseCase;
import com.td.infrastructure.security.AccountProvisioningChecker;
import com.td.infrastructure.security.AccountProvisioningFilter;
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
import java.util.ArrayList;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 22/10/2026 (tdgateway/docs/24 §4.2): cổng quyền màn Trục quốc gia qua filter chain thật — ADMIN/PLATFORM_ADMIN/
// mã quyền NATIONAL_EXCHANGE_VIEW vào được; người dùng thường 403; chưa đăng nhập 401. Phạm vi tenant/che dữ liệu
// kiểm ở NationalExchangeAccessPolicyTest/GetNationalExchangeDetailUseCaseTest (td-application).
@WebMvcTest(controllers = TrucQuocGiaController.class)
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
@TestPropertySource(properties = "app.security.tdtoken.secret=truc-quoc-gia-permission-test-secret-please-ignore")
class TrucQuocGiaControllerPermissionTest {

    private static final String SECRET = "truc-quoc-gia-permission-test-secret-please-ignore";
    private static final String BASE = "/api/v1/tonghop/TrucQuocGia";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;
    @MockBean
    private ResolveUserTenantUseCase resolveUserTenantUseCase;
    @MockBean
    private AccountProvisioningChecker accountProvisioningChecker;

    @MockBean
    private SearchNationalExchangesUseCase searchNationalExchangesUseCase;
    @MockBean
    private GetNationalExchangeDetailUseCase getNationalExchangeDetailUseCase;
    @MockBean
    private GetNationalExchangeAccessUseCase getNationalExchangeAccessUseCase;
    @MockBean
    private com.td.application.tonghop.national.RequestNationalExchangeOperationUseCase requestOperationUseCase;
    @MockBean
    private com.td.application.tonghop.national.CancelNationalExchangeOperationUseCase cancelOperationUseCase;
    @MockBean
    private com.td.application.tonghop.national.DownloadNationalExchangePackageUseCase downloadPackageUseCase;

    @BeforeEach
    void stub() {
        when(accountProvisioningChecker.isProvisioned(any(), any())).thenReturn(true);
        when(searchNationalExchangesUseCase.execute(any()))
            .thenReturn(Result.success(NationalExchangeSearchResult.builder().items(new ArrayList<>()).build()));
        when(getNationalExchangeAccessUseCase.execute()).thenReturn(Result.success(NationalExchangeAccess.denied()));
    }

    @Test
    void list_withAdmin_shouldReturn200() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Tdtoken", buildToken("qtv01", "ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void list_withViewPermissionCode_shouldReturn200() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Tdtoken", buildToken("vanthu01", "NATIONAL_EXCHANGE_VIEW")))
            .andExpect(status().isOk());
    }

    @Test
    void list_withPlatformAdmin_shouldReturn200() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Tdtoken", buildToken("root01", "PLATFORM_ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void list_withRegularUser_shouldReturn403() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Tdtoken", buildToken("cv01", "CVQuanTri")))
            .andExpect(status().isForbidden());
    }

    @Test
    void detail_withRegularUser_shouldReturn403() throws Exception {
        mockMvc.perform(get(BASE + "/ChiTiet").param("exchangeId", "IN:x")
                .header("Tdtoken", buildToken("cv01", "USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void summary_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post(BASE + "/ThongKe").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void operation_withAdmin_shouldReturn202() throws Exception {
        when(requestOperationUseCase.execute(any(), any(), any())).thenReturn(Result.success(
            com.td.application.tonghop.national.NationalExchangeDetail.Operation.builder()
                .requestId("r1").status("PENDING").build()));
        mockMvc.perform(post(BASE + "/ThaoTac").param("exchangeId", "OUT:u1")
                .header("Idempotency-Key", "k1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationType\":\"RESEND\",\"reason\":\"Đơn vị báo chưa nhận được\"}")
                .header("Tdtoken", buildToken("qtv01", "ADMIN")))
            .andExpect(status().isAccepted());
    }

    @Test
    void operation_withRegularUser_shouldReturn403() throws Exception {
        mockMvc.perform(post(BASE + "/ThaoTac").param("exchangeId", "OUT:u1")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Tdtoken", buildToken("cv01", "USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void access_isOpenToAnyAuthenticatedUser() throws Exception {
        mockMvc.perform(get(BASE + "/Quyen").header("Tdtoken", buildToken("cv01", "USER")))
            .andExpect(status().isOk());
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
