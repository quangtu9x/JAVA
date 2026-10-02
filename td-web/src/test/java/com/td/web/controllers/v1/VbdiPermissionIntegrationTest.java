package com.td.web.controllers.v1;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.vbdi.BanHanhVbdiUseCase;
import com.td.application.vbdi.GetVbdiDetailUseCase;
import com.td.application.vbdi.GuiEmailVbdiUseCase;
import com.td.application.vbdi.KySoVbdiUseCase;
import com.td.application.vbdi.GanKemTepVbdiUseCase;
import com.td.application.vbdi.ListVbdiSendLogUseCase;
import com.td.application.vbdi.SendVbdiDocumentUseCase;
import com.td.application.vbdi.HieuChinhVbdiUseCase;
import com.td.application.vbdi.AddVbdiEditHistoryUseCase;
import com.td.application.vbdi.ListVbdiEditHistoryUseCase;
import com.td.application.vbdi.SoanThaoVbdiUseCase;
import com.td.application.vbdi.TrinhKyVbdiUseCase;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-01 (QLVB Permission Model) — kiểm tra qua filter chain thật: quyền "cập nhật văn bản đi" thật
 * ({@code QCapNhatVBDi}/{@code _DV}/{@code _PB}/{@code CVQuanTri} — xem
 * {@link com.td.infrastructure.security.QlvbRoles}, bằng chứng {@code docs/frontend/05-vbden-vbdi.md}
 * mục "Quyền") phải được tôn trọng cho {@code SoanThao}/{@code TrinhKy}/{@code BanHanh}, trong khi
 * đọc ({@code GetView}) chỉ cần đã đăng nhập.
 */
@WebMvcTest(controllers = VbdiController.class)
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
class VbdiPermissionIntegrationTest {

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
    private SoanThaoVbdiUseCase soanThaoVbdiUseCase;
    @MockBean
    private HieuChinhVbdiUseCase hieuChinhVbdiUseCase;
    // Bo sung 13/09/2026 - xem ghi chu trong VbdiControllerTest.java (pre-existing gap, khong lien
    // quan tinh nang thay the/thu hoi vbden dang port).
    @MockBean
    private com.td.application.vbdi.CapNhatVanBanDiUseCase capNhatVanBanDiUseCase;
    @MockBean
    private AddVbdiEditHistoryUseCase addVbdiEditHistoryUseCase;
    @MockBean
    private ListVbdiEditHistoryUseCase listVbdiEditHistoryUseCase;
    @MockBean
    private TrinhKyVbdiUseCase trinhKyVbdiUseCase;
    @MockBean
    private BanHanhVbdiUseCase banHanhVbdiUseCase;
    @MockBean
    private KySoVbdiUseCase kySoVbdiUseCase;
    @MockBean
    private GanKemTepVbdiUseCase ganKemTepVbdiUseCase;
    @MockBean
    private GuiEmailVbdiUseCase guiEmailVbdiUseCase;
    @MockBean
    private GetVbdiDetailUseCase getVbdiDetailUseCase;
    @MockBean
    private ViewQueryService viewQueryService;
    @MockBean
    private SendVbdiDocumentUseCase sendVbdiDocumentUseCase;
    @MockBean
    private ListVbdiSendLogUseCase listVbdiSendLogUseCase;
    @MockBean
    private com.td.application.vbdi.GanVbdiDocumentUseCase ganVbdiDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.AddVbdiUserActionLogUseCase addVbdiUserActionLogUseCase;
    @MockBean
    private com.td.application.vbdi.ListVbdiUserActionLogUseCase listVbdiUserActionLogUseCase;
    @MockBean
    private com.td.application.vbdi.AddVbdiSeenDocumentUseCase addVbdiSeenDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.AddVbdiImportantDocumentUseCase addVbdiImportantDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.ChuyenXemDeBietVbdiUseCase chuyenXemDeBietVbdiUseCase;
    // Bo sung 13/09/2026 - cung nhom pre-existing gap voi CapNhatVanBanDiUseCase o tren.
    @MockBean
    private com.td.application.vbdi.DeleteVbdiDocumentUseCase deleteVbdiDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.GanVanBanLienQuanUseCase ganVanBanLienQuanUseCase;
    @MockBean
    private com.td.application.vbdi.RestoreVbdiDocumentUseCase restoreVbdiDocumentUseCase;
    @MockBean
    private com.td.application.vbdi.TraLoiVanBanDenUseCase traLoiVanBanDenUseCase;
    @MockBean
    private com.td.application.vbdi.XoaTraLoiVanBanDenUseCase xoaTraLoiVanBanDenUseCase;
    @MockBean
    private com.td.application.vbdi.XoaVanBanLienQuanUseCase xoaVanBanLienQuanUseCase;

    @Test
    void soanThao_withRealCapNhatVbdiRole_shouldReturn201() throws Exception {
        when(soanThaoVbdiUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbdi/SoanThao")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDi"))
                .param("subject", "Van ban di test"))
            .andExpect(status().isCreated());
    }

    @Test
    void soanThao_withAdminRole_shouldReturn201() throws Exception {
        when(soanThaoVbdiUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbdi/SoanThao")
                .header("Tdtoken", buildToken("admin01", "ADMIN"))
                .param("subject", "Van ban di test"))
            .andExpect(status().isCreated());
    }

    @Test
    void soanThao_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/SoanThao")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .param("subject", "Van ban di test"))
            .andExpect(status().isForbidden());
    }

    @Test
    void trinhKy_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/TrinhKy")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"nguoiKy\":\"lanhdao01\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void banHanh_withRealCapNhatVbdiDvRole_shouldReturn201() throws Exception {
        when(banHanhVbdiUseCase.execute(any())).thenReturn(Result.success("5/2026-VBD"));

        mockMvc.perform(post("/api/v1/vbdi/BanHanh")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDiDV"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void banHanh_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/BanHanh")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void kySo_withRealCapNhatVbdiRole_shouldReturn201() throws Exception {
        when(kySoVbdiUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        MockMultipartFile file = new MockMultipartFile("file", "vbdi_signed.pdf", "application/pdf", "pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/vbdi/KySo")
                .file(file)
                .param("id", UUID.randomUUID().toString())
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDiPB")))
            .andExpect(status().isCreated());
    }

    @Test
    void kySo_withoutQlvbRole_shouldReturn403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "vbdi_signed.pdf", "application/pdf", "pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/vbdi/KySo")
                .file(file)
                .param("id", UUID.randomUUID().toString())
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen")))
            .andExpect(status().isForbidden());
    }

    @Test
    void guiEmail_withRealCapNhatVbdiRole_shouldReturn200() throws Exception {
        when(guiEmailVbdiUseCase.execute(any())).thenReturn(Result.success(null));

        mockMvc.perform(post("/api/v1/vbdi/GuiEmail")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDi"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"toEmails\":[\"a@example.com\"]}"))
            .andExpect(status().isOk());
    }

    @Test
    void guiEmail_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/GuiEmail")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"toEmails\":[\"a@example.com\"]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getView_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(viewQueryService.execute(any(), any()))
            .thenReturn(new PaginationResponse<>(List.<Map<String, Object>>of(), 1, 20, 0L, 0, true, true));

        mockMvc.perform(post("/api/v1/vbdi/GetView")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"viewname\":\"vwvandi_chobanhanh\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void getView_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/GetView")
                .contentType("application/json")
                .content("{\"viewname\":\"vwvandi_chobanhanh\"}"))
            .andExpect(status().isUnauthorized());
    }

    // Đường đọc chi tiết (tách bảng riêng vbdi_documents) — chỉ cần đã đăng nhập, không có role-gate
    // riêng (cùng nhóm "đọc" với GetView, khác nhóm cập nhật QCapNhatVBDi*).
    @Test
    void xemChiTiet_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(getVbdiDetailUseCase.execute(any()))
            .thenReturn(Result.success(java.util.Collections.<String, Object>emptyMap()));

        mockMvc.perform(post("/api/v1/vbdi/XemChiTiet")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void xemChiTiet_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/XemChiTiet")
                .contentType("application/json")
                .content("{\"id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    // Yeu cau nguoi dung 28/08/2026 ("Nhat ky gui nhan"): SendDocument dung CHUNG role-gate
    // CAP_NHAT_VBDI_ROLES voi SoanThao/BanHanh/KySo; SendLog chi can da dang nhap (cung nhom "doc"
    // voi GetView/XemChiTiet).
    @Test
    void sendDocument_withRealCapNhatVbdiRole_shouldReturn201() throws Exception {
        when(sendVbdiDocumentUseCase.execute(any())).thenReturn(Result.success(1));

        mockMvc.perform(post("/api/v1/vbdi/SendDocument")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDi"))
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void sendDocument_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/SendDocument")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void sendDocument_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/SendDocument")
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sendLog_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(listVbdiSendLogUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.<Map<String, Object>>of(), 1, 20, 0L, 0, true, true));

        mockMvc.perform(post("/api/v1/vbdi/SendLog")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void sendLog_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/SendLog")
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    // 29/08/2026 (port "Gắn VB đi" — GanVbdiDocumentUseCase, cùng CAP_NHAT_VBDI_ROLES với SendDocument).
    @Test
    void ganVanBanDi_withRealCapNhatVbdiRole_shouldReturn200() throws Exception {
        when(ganVbdiDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbdi/GanVanBanDi")
                .header("Tdtoken", buildToken("vanthu01", "QCapNhatVBDi"))
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void ganVanBanDi_withoutQlvbRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/GanVanBanDi")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void ganVanBanDi_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/GanVanBanDi")
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    // 30/08/2026 (port "Lịch sử thao tác văn bản"): CHỈ cần đăng nhập (isAuthenticated()), không
    // yêu cầu role cập nhật vbdi — xem javadoc VbdiController#logUserAction.
    @Test
    void logUserAction_withAnyAuthenticatedRole_shouldReturn201() throws Exception {
        when(addVbdiUserActionLogUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbdi/LogUserAction")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\",\"actionType\":\"guivanban\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void logUserAction_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/LogUserAction")
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\",\"actionType\":\"guivanban\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getLogUserAction_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(listVbdiUserActionLogUseCase.execute(any())).thenReturn(List.<Map<String, Object>>of());

        mockMvc.perform(post("/api/v1/vbdi/GetLogUserAction")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void getLogUserAction_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/GetLogUserAction")
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    // 09/09/2026 (port "đánh dấu đã xem văn bản đi"): CHỈ cần đăng nhập (isAuthenticated()), không
    // yêu cầu role cập nhật vbdi — xem javadoc VbdiController#seenDocument.
    @Test
    void seenDocument_withAnyAuthenticatedRole_shouldReturn200() throws Exception {
        when(addVbdiSeenDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/vbdi/SeenDocument")
                .header("Tdtoken", buildToken("nhanvien01", "QXemVBDen"))
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void seenDocument_withoutAnyToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/vbdi/SeenDocument")
                .contentType("application/json")
                .content("{\"documentId\":\"" + UUID.randomUUID() + "\"}"))
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
