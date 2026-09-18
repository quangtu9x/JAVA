package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.hosocv.CapNhatHoSoUseCase;
import com.td.application.hosocv.ChuyenLuuTruHoSoUseCase;
import com.td.application.hosocv.DeleteHoSoUseCase;
import com.td.application.hosocv.DownloadFilesHoSoCVUseCase;
import com.td.application.hosocv.DuyetHoSoUseCase;
import com.td.application.hosocv.GanKemVanBanUseCase;
import com.td.application.hosocv.GetHoSoChiTietUseCase;
import com.td.application.hosocv.GetTokenLgspUseCase;
import com.td.application.hosocv.GopYPhoiHopUseCase;
import com.td.application.hosocv.KetThucHoSoUseCase;
import com.td.application.hosocv.RestoreHoSoUseCase;
import com.td.application.hosocv.TaoHoSoCVUseCase;
import com.td.application.hosocv.TrinhDuyetHoSoUseCase;
import com.td.application.hosocv.XuLyLaiHoSoUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.application.viewquery.ViewQueryService;
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
 * SEC-01 (QLVB Permission Model) — kiểm tra qua filter chain thật: KHÔNG tìm thấy bất kỳ
 * role-string/permission check nào ở tầng module {@code hosocv} thật (xem Javadoc
 * {@link HoSoCVController}, khác hẳn {@code hosoluutru} — module riêng, ngoài phạm vi BL-240) — nên
 * mọi endpoint chỉ yêu cầu ĐÃ ĐĂNG NHẬP (bất kỳ role nào).
 */
@WebMvcTest(controllers = HoSoCVController.class)
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
class HoSoCVPermissionIntegrationTest {

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
    private TaoHoSoCVUseCase taoHoSoCVUseCase;
    @MockBean
    private GanKemVanBanUseCase ganKemVanBanUseCase;
    @MockBean
    private DownloadFilesHoSoCVUseCase downloadFilesHoSoCVUseCase;
    @MockBean
    private ViewQueryService viewQueryService;
    @MockBean
    private GetHoSoChiTietUseCase getHoSoChiTietUseCase;
    @MockBean
    private CapNhatHoSoUseCase capNhatHoSoUseCase;
    @MockBean
    private DeleteHoSoUseCase deleteHoSoUseCase;
    @MockBean
    private RestoreHoSoUseCase restoreHoSoUseCase;
    @MockBean
    private TrinhDuyetHoSoUseCase trinhDuyetHoSoUseCase;
    @MockBean
    private DuyetHoSoUseCase duyetHoSoUseCase;
    @MockBean
    private ChuyenLuuTruHoSoUseCase chuyenLuuTruHoSoUseCase;
    @MockBean
    private KetThucHoSoUseCase ketThucHoSoUseCase;
    @MockBean
    private XuLyLaiHoSoUseCase xuLyLaiHoSoUseCase;
    @MockBean
    private GopYPhoiHopUseCase gopYPhoiHopUseCase;
    @MockBean
    private GetTokenLgspUseCase getTokenLgspUseCase;

    @Test
    void taoHoSo_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(taoHoSoCVUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/hosocv/TaoHoSo")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .param("title", "Ho so test"))
            .andExpect(status().isCreated());
    }

    @Test
    void taoHoSo_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/TaoHoSo")
                .param("title", "Ho so test"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ganVanBan_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(ganKemVanBanUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/hosocv/GanVanBan")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"hosocvId\":\"" + UUID.randomUUID() + "\",\"actionName\":\"ganvanbandi\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void ganVanBan_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/GanVanBan")
                .contentType("application/json")
                .content("{\"hosocvId\":\"" + UUID.randomUUID() + "\",\"actionName\":\"ganvanbandi\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllFile_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(downloadFilesHoSoCVUseCase.execute(any())).thenReturn(Result.success(List.of()));

        mockMvc.perform(post("/api/v1/hosocv/GetAllFile")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"vanBanLienQuan\":[]}"))
            .andExpect(status().isOk());
    }

    @Test
    void getAllFile_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/GetAllFile")
                .contentType("application/json")
                .content("{\"vanBanLienQuan\":[]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getView_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(viewQueryService.execute(org.mockito.ArgumentMatchers.eq("hosocv"), any()))
            .thenReturn(new PaginationResponse<>(List.of(), 1, 20, 0L, 0, true, true));

        mockMvc.perform(post("/api/v1/hosocv/GetView")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"viewname\":\"vwUser_All\",\"keys\":[\"nhanvien01\"]}"))
            .andExpect(status().isOk());
    }

    @Test
    void getView_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/GetView")
                .contentType("application/json")
                .content("{\"viewname\":\"vwUser_All\"}"))
            .andExpect(status().isUnauthorized());
    }

    // Mo rong BL-240 - 10 endpoint moi cung 1 khuon @PreAuthorize("isAuthenticated()") - dai dien 2
    // endpoint (1 doc, 1 ghi) de xac nhan filter chain that khong doi hanh vi, khong lap lai toan bo
    // 10 endpoint (da cover day du o HoSoCVControllerTest muc "delegate").

    @Test
    void luuHoSo_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(capNhatHoSoUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/hosocv/LuuHoSo")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void luuHoSo_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/LuuHoSo")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void trinhDuyet_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(trinhDuyetHoSoUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/hosocv/TrinhDuyet")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"hosocvId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void trinhDuyet_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/hosocv/TrinhDuyet")
                .contentType("application/json")
                .content("{\"hosocvId\":\"" + UUID.randomUUID() + "\"}"))
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
