package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.common.models.Result;
import com.td.application.vbden.ChuyenXuLyVbdenUseCase;
import com.td.application.vbden.GetSoVanBanDenUseCase;
import com.td.application.vbden.GetVbdenDetailUseCase;
import com.td.application.vbden.GuiSangVbcddhUseCase;
import com.td.application.vbden.KiemTrungSoDenUseCase;
import com.td.application.vbden.KiemTrungVbdenUseCase;
import com.td.application.vbden.LuuVanBanDenUseCase;
import com.td.application.vbden.TiepNhanVbdenUseCase;
import com.td.application.processdiagram.ProcessDiagramService;
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
 * SEC-01 (QLVB Permission Model) — kiểm tra qua filter chain thật (không mock security, khác
 * {@link VbdenControllerTest} dùng {@code addFilters=false}): quyền "cập nhật/hiệu chỉnh văn bản
 * đến" thật ({@code QCapNhatVBDen}/{@code QCapNhatVBDenDV}/{@code QCapNhatVBDenPB}/{@code CVQuanTri}
 * — xem {@link com.td.infrastructure.security.QlvbRoles}) phải được tôn trọng cho {@code TiepNhan},
 * trong khi đọc ({@code XemChiTiet}/{@code GetView}/{@code QuaTrinhXuLy}) chỉ cần đã đăng nhập,
 * đúng bằng chứng từ Domino thật (không có role gate cho đọc trên luồng vbden).
 */
@WebMvcTest(controllers = VbdenController.class)
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
class VbdenPermissionIntegrationTest {

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
    private TiepNhanVbdenUseCase tiepNhanVbdenUseCase;
    @MockBean
    private GetVbdenDetailUseCase getVbdenDetailUseCase;
    @MockBean
    private ViewQueryService viewQueryService;
    @MockBean
    private ChuyenXuLyVbdenUseCase chuyenXuLyVbdenUseCase;
    @MockBean
    private GuiSangVbcddhUseCase guiSangVbcddhUseCase;
    // Bo sung 18/09/2026 - xem ghi chu trong VbdenControllerTest (bay "WebMvcTest MockBean drift").
    @MockBean
    private com.td.application.vbden.ChuyenXuLyVbcddhNhiemVuUseCase chuyenXuLyVbcddhNhiemVuUseCase;
    @MockBean
    private com.td.application.vbden.AddVbdenUserActionLogUseCase addVbdenUserActionLogUseCase;
    @MockBean
    private com.td.application.vbden.ListVbdenUserActionLogUseCase listVbdenUserActionLogUseCase;
    @MockBean
    private KiemTrungVbdenUseCase kiemTrungVbdenUseCase;
    @MockBean
    private KiemTrungSoDenUseCase kiemTrungSoDenUseCase;
    @MockBean
    private GetSoVanBanDenUseCase getSoVanBanDenUseCase;
    @MockBean
    private LuuVanBanDenUseCase luuVanBanDenUseCase;
    @MockBean
    private com.td.application.vbden.ChoYKienXinYKienUseCase choYKienXinYKienUseCase;
    @MockBean
    private com.td.application.vbden.GuiXinYKienUseCase guiXinYKienUseCase;
    @MockBean
    private com.td.application.vbden.GuiXinYKienTuVBdenUseCase guiXinYKienTuVBdenUseCase;
    @MockBean
    private com.td.application.vbden.LichSuXuLyUseCase lichSuXuLyUseCase;
    @MockBean
    private com.td.application.vbden.XuLyLaiUseCase xuLyLaiUseCase;
    @MockBean
    private com.td.application.vbden.ThuHoiVanBanUseCase thuHoiVanBanUseCase;
    @MockBean
    private com.td.application.vbden.LayLaiVanBanUseCase layLaiVanBanUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatKetQuaXuLyUseCase capNhatKetQuaXuLyUseCase;
    @MockBean
    private com.td.application.vbden.VBdenChuyenVanBanPhapQuyUseCase vbdenChuyenVanBanPhapQuyUseCase;
    @MockBean
    private com.td.application.vbden.KetThucXuLyUseCase ketThucXuLyUseCase;
    @MockBean
    private com.td.application.vbden.TaoDuThaoVBdenUseCase taoDuThaoVBdenUseCase;
    @MockBean
    private com.td.application.vbden.TuChoiTiepNhanVbdenUseCase tuChoiTiepNhanVbdenUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatVanBanThayTheThuHoiUseCase capNhatVanBanThayTheThuHoiUseCase;
    @MockBean
    private com.td.application.vbden.AddVbdenEditHistoryUseCase addVbdenEditHistoryUseCase;
    @MockBean
    private com.td.application.vbden.ListVbdenEditHistoryUseCase listVbdenEditHistoryUseCase;
    @MockBean
    private com.td.application.vbden.ListVbdenUpdateHistoryUseCase listVbdenUpdateHistoryUseCase;
    @MockBean
    private com.td.application.vbden.KhongVaoSoVbdenUseCase khongVaoSoVbdenUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatPhoiHopUseCase capNhatPhoiHopUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatDaXemDeBietUseCase capNhatDaXemDeBietUseCase;
    @MockBean
    private com.td.application.vbden.TheoDoiNhiemVuUseCase theoDoiNhiemVuUseCase;
    @MockBean
    private com.td.application.vbden.TheoDoiVanBanDenUseCase theoDoiVanBanDenUseCase;
    @MockBean
    private com.td.application.vbden.GetSoLuongCanXuLyUseCase getSoLuongCanXuLyUseCase;
    @MockBean
    private ProcessDiagramService processDiagramService;
    @MockBean
    private com.td.application.vbden.XoaVanBanDenUseCase xoaVanBanDenUseCase;
    @MockBean
    private com.td.application.vbden.PhucHoiVanBanDenUseCase phucHoiVanBanDenUseCase;
    @MockBean
    private com.td.application.vbden.GetNguoiTraLaiUseCase getNguoiTraLaiUseCase;
    @MockBean
    private com.td.application.vbden.GetNguoiThuHoiUseCase getNguoiThuHoiUseCase;
    @MockBean
    private com.td.application.vbden.TraLaiVanBanUseCase traLaiVanBanUseCase;
    @MockBean
    private com.td.application.vbden.SuaYKienVbdenUseCase suaYKienVbdenUseCase;
    @MockBean
    private com.td.application.vbden.CapNhatYKienVbdenUseCase capNhatYKienVbdenUseCase;
    @MockBean
    private com.td.application.vbden.KySoVbdenUseCase kySoVbdenUseCase;
    @MockBean
    private com.td.application.vbden.GanKemTepVbdenUseCase ganKemTepVbdenUseCase;

    // Bo sung 02/09/2026 - doc (SoDoXuLy) khong co bang chung role gate rieng trong Domino
    // (qlvb/get chung, khong phai action rieng cho vbden) nen chi can da dang nhap, cung nhom voi
    // XemChiTiet/GetView/QuaTrinhXuLy.
    @Test
    void soDoXuLy_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(processDiagramService.getDiagramJson(any(), any())).thenReturn(null);

        mockMvc.perform(post("/api/v1/vbden/SoDoXuLy")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void soDoXuLy_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/SoDoXuLy")
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tiepNhan_withRealCapNhatVbdenRole_shouldReturn201() throws Exception {
        when(tiepNhanVbdenUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/TiepNhan")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDen"))
                .param("subject", "Van ban den test"))
            .andExpect(status().isCreated());
    }

    @Test
    void tiepNhan_withAdminRole_shouldReturn201() throws Exception {
        when(tiepNhanVbdenUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/TiepNhan")
                .header("Tdtoken", buildToken("admin01", "ADMIN"))
                .param("subject", "Van ban den test"))
            .andExpect(status().isCreated());
    }

    @Test
    void tiepNhan_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/TiepNhan")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .param("subject", "Van ban den test"))
            .andExpect(status().isForbidden());
    }

    @Test
    void xemChiTiet_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(getVbdenDetailUseCase.execute(any()))
            .thenReturn(Result.success(java.util.Map.of("databasetype", "vbden")));

        mockMvc.perform(post("/api/v1/vbden/XemChiTiet")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"idvbden\":\"" + UUID.randomUUID() + "\",\"items\":\"getall\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void xemChiTiet_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/XemChiTiet")
                .contentType("application/json")
                .content("{\"idvbden\":\"" + UUID.randomUUID() + "\",\"items\":\"getall\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void chuyenXuLy_withPhanCongRole_shouldReturn201() throws Exception {
        when(chuyenXuLyVbdenUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/ChuyenXuLy")
                .header("Tdtoken", buildToken("truongphong01", "QPhanCong"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"action\":\"phanxuly\",\"userProcess\":\"nguyenvana\"}"))
            .andExpect(status().isCreated());
    }

    // Bugfix 24/09/2026: catalog app_permissions (V1.0.18) co san 3 quyen pham vi hep
    // (QPhanCongDV/QPhanPhoiDV/QPhanCongPhong) nhung PHAN_CONG_CHUYEN_TIEP_ROLES truoc do chi
    // check ban toan co quan (QPhanCong/QPhanPhoi) - user chi duoc gan 1 trong 3 quyen nay se bi
    // 403 oan du dung dung nghia vu duoc phan cong/phan phoi cua minh.
    @Test
    void chuyenXuLy_withPhanCongDVRole_shouldReturn201() throws Exception {
        when(chuyenXuLyVbdenUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/ChuyenXuLy")
                .header("Tdtoken", buildToken("truongdonvi01", "QPhanCongDV"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"action\":\"phanxuly\",\"userProcess\":\"nguyenvana\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void chuyenXuLy_withPhanPhoiDVRole_shouldReturn201() throws Exception {
        when(chuyenXuLyVbdenUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/ChuyenXuLy")
                .header("Tdtoken", buildToken("truongdonvi02", "QPhanPhoiDV"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"action\":\"phanphoi\",\"userProcess\":\"nguyenvana\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void chuyenXuLy_withPhanCongPhongRole_shouldReturn201() throws Exception {
        when(chuyenXuLyVbdenUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/ChuyenXuLy")
                .header("Tdtoken", buildToken("truongphongban01", "QPhanCongPhong"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"action\":\"phanxuly\",\"userProcess\":\"nguyenvana\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void chuyenXuLy_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/ChuyenXuLy")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"action\":\"phanxuly\",\"userProcess\":\"nguyenvana\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void guiSangVbcddh_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(guiSangVbcddhUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/GuiSangVBCDDH")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void guiSangVbcddh_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/GuiSangVBCDDH")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void kiemTrung_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(kiemTrungVbdenUseCase.execute(any())).thenReturn(Result.success(java.util.List.of()));

        mockMvc.perform(post("/api/v1/vbden/KiemTrung")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"code\":\"123\",\"promulgation_date\":\"01/01/2026\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void kiemTrung_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/KiemTrung")
                .contentType("application/json")
                .content("{\"code\":\"123\",\"promulgation_date\":\"01/01/2026\"}"))
            .andExpect(status().isUnauthorized());
    }

    // MVP 21/08/2026: /Luu la hanh dong GHI (hieu chinh/vao so van ban da ton tai) - cung role gate
    // voi /TiepNhan (CAP_NHAT_VBDEN_ROLES).
    @Test
    void luu_withRealCapNhatVbdenRole_shouldReturn200() throws Exception {
        when(luuVanBanDenUseCase.execute(any()))
            .thenReturn(Result.success(new com.td.application.vbden.LuuVanBanDenResultDto(UUID.randomUUID(), null, "")));

        mockMvc.perform(post("/api/v1/vbden/Luu")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void luu_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/Luu")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isForbidden());
    }

    // N15 (03/09/2026) - hanh dong van thu ("Cap nhat ket qua xu ly"), cung role gate voi /Luu
    // (CAP_NHAT_VBDEN_ROLES) - xem VbdenController#capNhatKetQuaXuLy.
    @Test
    void capNhatKetQuaXuLy_withRealCapNhatVbdenRole_shouldReturn201() throws Exception {
        when(capNhatKetQuaXuLyUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/CapNhatKetQuaXuLy")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void capNhatKetQuaXuLy_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/CapNhatKetQuaXuLy")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isForbidden());
    }

    // N18 (04/09/2026) - "Lay lai VB", cung role gate voi /Luu (CAP_NHAT_VBDEN_ROLES) - xem
    // VbdenController#layLaiVanBan.
    @Test
    void layLaiVanBan_withRealCapNhatVbdenRole_shouldReturn201() throws Exception {
        when(layLaiVanBanUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/LayLaiVanBan")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Ly do\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void layLaiVanBan_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/LayLaiVanBan")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Ly do\"}"))
            .andExpect(status().isForbidden());
    }

    // N16 (03/09/2026) - cung role gate voi /Luu (CAP_NHAT_VBDEN_ROLES) - xem
    // VbdenController#chuyenVanBanPhapQuy.
    @Test
    void chuyenVanBanPhapQuy_withRealCapNhatVbdenRole_shouldReturn201() throws Exception {
        when(vbdenChuyenVanBanPhapQuyUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/ChuyenVanBanPhapQuy")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDen"))
                .contentType("application/json")
                .content("{\"idqlvb\":\"" + UUID.randomUUID() + "\",\"mailin\":\"vbqp@example.com\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void chuyenVanBanPhapQuy_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/ChuyenVanBanPhapQuy")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"idqlvb\":\"" + UUID.randomUUID() + "\",\"mailin\":\"vbqp@example.com\"}"))
            .andExpect(status().isForbidden());
    }

    // N08 (31/08/2026) - "cho y kien" la hanh dong CA NHAN (nguoi duoc hoi tu cho y kien cua minh),
    // KHONG can quyen phan cong/phan phoi nhu ChuyenXuLy - chi can da dang nhap, cung gate voi
    // XemChiTiet (xem VbdenController#choYKien).
    @Test
    void choYKien_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(choYKienXinYKienUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/ChoYKien")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Dong y\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void choYKien_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/ChoYKien")
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Dong y\"}"))
            .andExpect(status().isUnauthorized());
    }

    // N09 (01/09/2026) - xem javadoc LichSuXuLyUseCase.
    @Test
    void lichSuXuLy_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(lichSuXuLyUseCase.execute(any()))
            .thenReturn(new com.td.application.common.models.PaginationResponse<>(
                java.util.List.of(), 1, 20, 0L, 0, true, true));

        mockMvc.perform(post("/api/v1/vbden/LichSuXuLy")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    void lichSuXuLy_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/LichSuXuLy")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    // N10 (01/09/2026) - xem javadoc XuLyLaiUseCase.
    @Test
    void xuLyLai_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(xuLyLaiUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/XuLyLai")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Can xu ly lai\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void xuLyLai_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/XuLyLai")
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Can xu ly lai\"}"))
            .andExpect(status().isUnauthorized());
    }

    // N11 (01/09/2026) - xem javadoc ThuHoiVanBanUseCase.
    @Test
    void thuHoiVanBan_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(thuHoiVanBanUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/ThuHoiVanBan")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Can thu hoi\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void thuHoiVanBan_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/ThuHoiVanBan")
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Can thu hoi\"}"))
            .andExpect(status().isUnauthorized());
    }

    // N17 (04/09/2026) - khong tim thay role gate rieng trong CapNhatVanBanDen.java that cho action
    // "ketthucxuly" (nguoi dang xu ly tu ket thuc phan viec cua minh) - chi can da dang nhap, cung
    // gate voi XuLyLai/ThuHoiVanBan (xem VbdenController#ketThucXuLy).
    @Test
    void ketThucXuLy_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(ketThucXuLyUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/KetThucXuLy")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Da xu ly xong\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void ketThucXuLy_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/KetThucXuLy")
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Da xu ly xong\"}"))
            .andExpect(status().isUnauthorized());
    }

    // N19 (04/09/2026) - khong tim thay role gate rieng trong CapNhatVanBanDen.java that cho action
    // "taoduthao" - chi can da dang nhap, cung gate voi ketThucXuLy/XuLyLai/ThuHoiVanBan.
    @Test
    void taoDuThao_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(taoDuThaoVBdenUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/TaoDuThao")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void taoDuThao_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/TaoDuThao")
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    // Bo sung 07/09/2026 (2) - khong tim thay role gate rieng trong ReplyDocumentOut.java that -
    // chi can da dang nhap, cung gate voi KetThucXuLy/TaoDuThao.
    @Test
    void tuChoiTiepNhan_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(tuChoiTiepNhanVbdenUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/TuChoiTiepNhan")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Khong dung tham quyen\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void tuChoiTiepNhan_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/TuChoiTiepNhan")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Khong dung tham quyen\"}"))
            .andExpect(status().isUnauthorized());
    }

    // Bo sung 07/09/2026 (4) - khong tim thay role gate rieng trong CapNhatVanBanDen.java that cho
    // action "khongvaoso" - chi can da dang nhap, cung gate voi TuChoiTiepNhan/KetThucXuLy.
    @Test
    void khongVaoSo_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(khongVaoSoVbdenUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/KhongVaoSo")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Vui long xu ly don vi khac\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void khongVaoSo_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/KhongVaoSo")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Vui long xu ly don vi khac\"}"))
            .andExpect(status().isUnauthorized());
    }

    // N20 (07/09/2026) - khong tim thay role gate rieng trong CapNhatVanBanDen.java that cho action
    // "capnhatphoihop" - chi can da dang nhap, cung gate voi KetThucXuLy/XuLyLai/TuChoiTiepNhan.
    @Test
    void capNhatPhoiHop_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(capNhatPhoiHopUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/CapNhatPhoiHop")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Da phoi hop xong\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void capNhatPhoiHop_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/CapNhatPhoiHop")
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"opinion\":\"Da phoi hop xong\"}"))
            .andExpect(status().isUnauthorized());
    }

    // N20 (07/09/2026) - khong tim thay role gate rieng trong TheoDoiNhiemVu.java that - chi can da
    // dang nhap, cung gate voi CapNhatPhoiHop/KetThucXuLy.
    @Test
    void theoDoiNhiemVu_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(theoDoiNhiemVuUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbden/TheoDoiNhiemVu")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"action\":\"theodoi\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void theoDoiNhiemVu_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbden/TheoDoiNhiemVu")
                .contentType("application/json")
                .content("{\"parentid\":\"" + UUID.randomUUID() + "\",\"action\":\"theodoi\"}"))
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
