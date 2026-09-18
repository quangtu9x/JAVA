package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.common.models.Result;
import com.td.application.vbcddh.CapNhatTienDoUseCase;
import com.td.application.vbcddh.NhacViecResponseDto;
import com.td.application.vbcddh.NhacViecUseCase;
import com.td.application.vbcddh.PhanCongNhiemVuUseCase;
import com.td.application.vbcddh.ThongKeNhiemVuUseCase;
import com.td.application.viewquery.ViewQueryService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-01 (QLVB Permission Model) — kiểm tra qua filter chain thật (không mock security): quyền
 * "phân công" thật ({@code QPhanCong}/{@code QPhanPhoi}/{@code CVQuanTri} — xem
 * {@link com.td.infrastructure.security.QlvbRoles}, bằng chứng từ {@code docs/frontend/08-vbcddh.md}
 * mục "Quyền") phải được tôn trọng cho {@code PhanCongNhiemVu}, trong khi các hành động còn lại
 * ({@code CapNhatTienDo}/{@code ThongKeNhiemVu}/{@code NhacViec}) chỉ cần đã đăng nhập — đúng bằng
 * chứng không có role gate riêng trong source Domino thật cho các hành động đó.
 */
@WebMvcTest(controllers = VbcddhController.class)
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
@TestPropertySource(properties = "app.security.tdtoken.secret=sec-01-integration-test-secret-please-ignore")
class VbcddhPermissionIntegrationTest {

    private static final String SECRET = "sec-01-integration-test-secret-please-ignore";

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
    private PhanCongNhiemVuUseCase phanCongNhiemVuUseCase;
    @MockBean
    private CapNhatTienDoUseCase capNhatTienDoUseCase;
    @MockBean
    private ThongKeNhiemVuUseCase thongKeNhiemVuUseCase;
    @MockBean
    private NhacViecUseCase nhacViecUseCase;
    @MockBean
    private ViewQueryService viewQueryService;

    @Test
    void phanCongNhiemVu_withPhanCongRole_shouldReturn201() throws Exception {
        when(phanCongNhiemVuUseCase.execute(any())).thenReturn(Result.success(List.of(UUID.randomUUID())));

        mockMvc.perform(post("/api/v1/vbcddh/PhanCongNhiemVu")
                .header("Tdtoken", buildToken("truongphong01", "QPhanCong"))
                .contentType("application/json")
                .content("{\"idVanBan\":\"" + UUID.randomUUID() + "\",\"assignments\":["
                    + "{\"donViThucHien\":\"Don vi A\",\"donViThucHienHide\":\"donvi_a\"}]}"))
            .andExpect(status().isCreated());
    }

    @Test
    void phanCongNhiemVu_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbcddh/PhanCongNhiemVu")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"idVanBan\":\"" + UUID.randomUUID() + "\",\"assignments\":["
                    + "{\"donViThucHien\":\"Don vi A\",\"donViThucHienHide\":\"donvi_a\"}]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void phanCongNhiemVu_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbcddh/PhanCongNhiemVu")
                .contentType("application/json")
                .content("{\"idVanBan\":\"" + UUID.randomUUID() + "\",\"assignments\":["
                    + "{\"donViThucHien\":\"Don vi A\",\"donViThucHienHide\":\"donvi_a\"}]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void capNhatTienDo_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(capNhatTienDoUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbcddh/CapNhatTienDo")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"idNhiemVu\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void thongKeNhiemVu_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(thongKeNhiemVuUseCase.execute(any())).thenReturn(Result.success(java.util.Map.of()));

        mockMvc.perform(post("/api/v1/vbcddh/ThongKeNhiemVu")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"identifiers\":[\"donvi_a\"]}"))
            .andExpect(status().isOk());
    }

    @Test
    void nhacViec_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbcddh/NhacViec"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void nhacViec_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(nhacViecUseCase.execute()).thenReturn(Result.success(new NhacViecResponseDto(List.of(), List.of())));

        mockMvc.perform(post("/api/v1/vbcddh/NhacViec")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen")))
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
