package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.common.models.Result;
import com.td.application.duthao.AddDuThaoEditHistoryUseCase;
import com.td.application.duthao.ChuyenThanhVanBanDiUseCase;
import com.td.application.duthao.DuThaoDaBanHanhUseCase;
import com.td.application.duthao.DuThaoVanThuKhongBanHanhUseCase;
import com.td.application.duthao.TraLaiDuThaoChoLanhDaoUseCase;
import com.td.application.duthao.TraLaiDuThaoChoNguoiSoanThaoUseCase;
import com.td.application.duthao.TraLaiDuThaoChoNguoiTrinhUseCase;
import com.td.application.duthao.GanKemTepDuThaoUseCase;
import com.td.application.duthao.GanVanBanLienQuanDuThaoUseCase;
import com.td.application.duthao.XoaTraLoiVanBanDenDuThaoUseCase;
import com.td.application.duthao.XoaVanBanLienQuanDuThaoUseCase;
import com.td.application.duthao.ListDuThaoEditHistoryUseCase;
import com.td.application.duthao.AddDuThaoSeenDocumentUseCase;
import com.td.application.duthao.GetDuThaoDetailUseCase;
import com.td.application.duthao.LuuDuThaoUseCase;
import com.td.application.duthao.TaoDuThaoUseCase;
import com.td.application.duthao.ThuHoiVanBanDiUseCase;
import com.td.application.duthao.TraLoiVanBanDenDuThaoUseCase;
import com.td.application.duthao.XinYKienPhoiHopUseCase;
import com.td.application.duthao.XuLyDuThaoUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.application.viewquery.ViewQueryService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-01 (QLVB Permission Model) — kiểm tra qua filter chain thật: KHÔNG tìm thấy bất kỳ
 * role-string/permission check nào trong toàn bộ 5 file Domino thật của module DuThao (xem Javadoc
 * {@link DuThaoController}) — nên mọi endpoint chỉ yêu cầu ĐÃ ĐĂNG NHẬP (bất kỳ role nào), không có
 * role-gate riêng, khác các module khác (VBDen/VBCDDH/VBDi) đã có bằng chứng role cụ thể.
 */
@WebMvcTest(controllers = DuThaoController.class)
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
@TestPropertySource(properties = "app.security.tdtoken.secret=sec-01-integration-test-secret-please-ignore")
class DuThaoPermissionIntegrationTest {

    private static final String SECRET = "sec-01-integration-test-secret-please-ignore";

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
    private TaoDuThaoUseCase taoDuThaoUseCase;
    @MockBean
    private GetDuThaoDetailUseCase getDuThaoDetailUseCase;
    @MockBean
    private LuuDuThaoUseCase luuDuThaoUseCase;
    @MockBean
    private XinYKienPhoiHopUseCase xinYKienPhoiHopUseCase;
    @MockBean
    private ChuyenThanhVanBanDiUseCase chuyenThanhVanBanDiUseCase;
    @MockBean
    private DuThaoDaBanHanhUseCase duThaoDaBanHanhUseCase;
    @MockBean
    private DuThaoVanThuKhongBanHanhUseCase duThaoVanThuKhongBanHanhUseCase;
    @MockBean
    private TraLaiDuThaoChoLanhDaoUseCase traLaiDuThaoChoLanhDaoUseCase;
    @MockBean
    private TraLaiDuThaoChoNguoiSoanThaoUseCase traLaiDuThaoChoNguoiSoanThaoUseCase;
    @MockBean
    private TraLaiDuThaoChoNguoiTrinhUseCase traLaiDuThaoChoNguoiTrinhUseCase;
    @MockBean
    private ThuHoiVanBanDiUseCase thuHoiVanBanDiUseCase;
    @MockBean
    private XuLyDuThaoUseCase xuLyDuThaoUseCase;
    @MockBean
    private GanKemTepDuThaoUseCase ganKemTepDuThaoUseCase;
    @MockBean
    private AddDuThaoEditHistoryUseCase addDuThaoEditHistoryUseCase;
    @MockBean
    private ListDuThaoEditHistoryUseCase listDuThaoEditHistoryUseCase;

    @MockBean
    private AddDuThaoSeenDocumentUseCase addDuThaoSeenDocumentUseCase;
    @MockBean
    private TraLoiVanBanDenDuThaoUseCase traLoiVanBanDenDuThaoUseCase;
    @MockBean
    private XoaTraLoiVanBanDenDuThaoUseCase xoaTraLoiVanBanDenDuThaoUseCase;
    @MockBean
    private GanVanBanLienQuanDuThaoUseCase ganVanBanLienQuanDuThaoUseCase;
    @MockBean
    private XoaVanBanLienQuanDuThaoUseCase xoaVanBanLienQuanDuThaoUseCase;
    @MockBean
    private com.td.application.duthao.XoaVetDuThaoUseCase xoaVetDuThaoUseCase;
    @MockBean
    private com.td.application.duthao.XoaTepVetDuThaoUseCase xoaTepVetDuThaoUseCase;
    @MockBean
    private ViewQueryService viewQueryService;

    @Test
    void taoDuThao_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(taoDuThaoUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/duthao/TaoDuThao")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .param("subject", "Du thao test"))
            .andExpect(status().isCreated());
    }

    @Test
    void taoDuThao_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/TaoDuThao")
                .param("subject", "Du thao test"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void xemChiTiet_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/XemChiTiet")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void xemChiTiet_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(getDuThaoDetailUseCase.execute(any())).thenReturn(Result.success(java.util.Map.of()));

        mockMvc.perform(post("/api/v1/duthao/XemChiTiet")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void luu_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/Luu")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void luu_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(luuDuThaoUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/duthao/Luu")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void xinYKienPhoiHop_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(xinYKienPhoiHopUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/duthao/XinYKienPhoiHop")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void chuyenThanhVanBanDi_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/ChuyenThanhVanBanDi")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void chuyenThanhVanBanDi_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(chuyenThanhVanBanDiUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/duthao/ChuyenThanhVanBanDi")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void thuHoiVanBanDi_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/ThuHoiVanBanDi")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void thuHoiVanBanDi_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(thuHoiVanBanDiUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/duthao/ThuHoiVanBanDi")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void xuLyDuThao_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/XuLyDuThao")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"action\":\"chuyenduyet\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void xuLyDuThao_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(xuLyDuThaoUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/duthao/XuLyDuThao")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"action\":\"chuyenduyet\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void ganKemTep_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/GanKemTep")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ganKemTep_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(ganKemTepDuThaoUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/duthao/GanKemTep")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void getView_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/duthao/GetView")
                .contentType("application/json")
                .content("{\"viewname\":\"vwUser_Creator\",\"keys\":[\"nhanvien01\"]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getView_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(viewQueryService.execute(any(), any())).thenReturn(
            new com.td.application.common.models.PaginationResponse<>(java.util.List.of(), 0, 20, 0L, 0, true, true));

        mockMvc.perform(post("/api/v1/duthao/GetView")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"viewname\":\"vwUser_Creator\",\"keys\":[\"nhanvien01\"]}"))
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
