package com.td.web.controllers.v1;

import com.td.application.sharedcore.GetSystemConfigurationUseCase;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.application.sharedcore.UserRoleGroupConfigDto;
import com.td.infrastructure.security.AuthenticatedUser;
import com.td.infrastructure.security.TdTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// BL-117 (bugfix): /qlvb/profile trước đây GHI ĐÈ HẲN roles thật của user "quản trị tối cao" bằng
// hằng số SUPER_ADMIN_ROLES cứng — làm mất mọi role Keycloak THẬT (vd PLATFORM_ADMIN) dù JWT gốc có
// đủ, khiến FE (đọc currentUser.roles từ đúng endpoint này) không bao giờ thấy được role mới. Test
// này khoá lại đúng hành vi: role thật PHẢI được hợp nhất vào, không bị mất.
@ExtendWith(MockitoExtension.class)
class QLVBCompatAuthControllerTest {

    @Mock
    private TdTokenService tdTokenService;
    @Mock
    private GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;
    @Mock
    private GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    @Mock
    private GetSystemConfigurationUseCase getSystemConfigurationUseCase;

    private QLVBCompatAuthController newController() {
        return new QLVBCompatAuthController(
            tdTokenService, getUserRoleGroupConfigUseCase, getUserOrganizationProfileUseCase, getSystemConfigurationUseCase);
    }

    @Test
    void profile_shouldMergeRealKeycloakRoles_intoSuperAdminRoles_notOverwriteThem() {
        lenient().when(tdTokenService.issue(any(), anyMap())).thenReturn("fake-tdtoken");
        when(getUserOrganizationProfileUseCase.execute("admin")).thenReturn(new UserOrganizationProfileDto());
        var controller = newController();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "admin")
            .claim("realm_access", Map.of("roles", List.of("ADMIN", "PLATFORM_ADMIN")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));

        var response = controller.profile(authentication);

        String roles = (String) response.getBody().get("roles");
        assertThat(roles).contains("PLATFORM_ADMIN");
        // Danh sách quyền Domino cũ vẫn phải còn nguyên - khong duoc mat khi hop nhat.
        assertThat(roles).contains("CVQuanTri").contains("ADMIN").contains("USER");
        assertThat(response.getBody().get("access")).isEqualTo("Manager");
    }

    // BL-118: tài khoản "admin" bootstrap KHÔNG có bản ghi AppUser thật đi kèm — phải fallback về
    // giá trị demo cũ (H56/"Tỉnh"), không được để trống trơn (hồi quy trải nghiệm test/demo hiện có).
    @Test
    void profile_forSuperAdminWithoutAppUserRecord_shouldFallbackToLegacyDemoOrgValues() {
        lenient().when(tdTokenService.issue(any(), anyMap())).thenReturn("fake-tdtoken");
        when(getUserOrganizationProfileUseCase.execute("admin")).thenReturn(new UserOrganizationProfileDto());
        var controller = newController();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "admin")
            .claim("realm_access", Map.of("roles", List.of("ADMIN")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        var response = controller.profile(authentication);

        assertThat(response.getBody().get("agency")).isEqualTo("H56");
        assertThat(response.getBody().get("coquan")).isEqualTo("H56");
        assertThat(response.getBody().get("caplanhdao")).isEqualTo("Tỉnh");
    }

    @Test
    void profile_forRegularUser_shouldUseTheirOwnRealRoles_notSuperAdminList() {
        lenient().when(tdTokenService.issue(any(), anyMap())).thenReturn("fake-tdtoken");
        when(getUserRoleGroupConfigUseCase.execute(anyString())).thenReturn(new UserRoleGroupConfigDto());
        when(getUserOrganizationProfileUseCase.execute(anyString())).thenReturn(new UserOrganizationProfileDto());
        var controller = newController();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "nhanvien01")
            .claim("realm_access", Map.of("roles", List.of("USER")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        var response = controller.profile(authentication);

        assertThat(response.getBody().get("roles")).isEqualTo("USER;");
        assertThat(response.getBody().get("access")).isEqualTo("User");
        // Không có "Nhóm vai trò" nào -> menu_top rỗng ("", KHÔNG phải mảng rỗng — xem javadoc
        // buildProfile) và các menu_* còn lại là chuỗi JSON mảng rỗng "[]".
        assertThat(response.getBody().get("menu_top")).isEqualTo("");
        assertThat(response.getBody().get("menu_qlvb")).isEqualTo("[]");
        // Không tìm thấy AppUser -> không phải superAdmin -> KHÔNG áp dụng fallback H56/"Tỉnh".
        assertThat(response.getBody().get("agency")).isEqualTo("");
        assertThat(response.getBody().get("caplanhdao")).isEqualTo("");
    }

    // Bổ sung 19/08/2026 (bugfix): user KHÔNG có role Keycloak trực tiếp nhưng được gán "Nhóm vai
    // trò" (RoleGroup) — trước đây /qlvb/profile hoàn toàn bỏ qua cấu hình này, khiến FE không hiện
    // được menu chức năng dù đã cấu hình đủ ở "Nhóm vai trò". Test khoá lại: quyền + menu từ role
    // group PHẢI được hợp nhất vào response.
    @Test
    void profile_forRegularUser_withRoleGroup_shouldMergePermissionsAndMenusFromRoleGroup() {
        lenient().when(tdTokenService.issue(any(), anyMap())).thenReturn("fake-tdtoken");
        when(getUserOrganizationProfileUseCase.execute("nhanvien02")).thenReturn(new UserOrganizationProfileDto());
        var roleGroupConfig = new UserRoleGroupConfigDto();
        roleGroupConfig.setPermissionCodes(List.of("QCapNhatVBDen"));
        roleGroupConfig.setMenuFields(Map.of(
            "menu_top", List.of("Văn bản đến"),
            "menu_qlvb", List.of("Người dùng", "Vai trò")));
        when(getUserRoleGroupConfigUseCase.execute("nhanvien02")).thenReturn(roleGroupConfig);
        var controller = newController();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "nhanvien02")
            .claim("realm_access", Map.of("roles", List.of("USER")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        var response = controller.profile(authentication);

        String roles = (String) response.getBody().get("roles");
        assertThat(roles).contains("USER").contains("QCapNhatVBDen");
        // menu_top: chuỗi nối ";" thô, menu_qlvb: chuỗi JSON-encode mảng (khác định dạng nhau, xem
        // javadoc buildProfile/legacyAdminProfile.json).
        assertThat(response.getBody().get("menu_top")).isEqualTo("Văn bản đến;");
        assertThat(response.getBody().get("menu_qlvb")).isEqualTo("[\"Người dùng\",\"Vai trò\"]");
    }

    // Bổ sung 20/08/2026 (bugfix nghiêm trọng): claim "roles" nhúng vào Tdtoken THẬT trước đây chỉ
    // lấy role Keycloak thô (ở đây chỉ "USER") — quyền nghiệp vụ "QCapNhatVBDen" gán qua Nhóm vai trò
    // ĐÃ hiện đúng ở response.roles (test ở trên) nhưng KHÔNG hề có trong token thật, khiến mọi
    // @PreAuthorize dùng role nghiệp vụ (vd POST /api/v1/vbden/TiepNhan) luôn trả 403. Test này khoá
    // lại: claim token phải khớp CHÍNH XÁC roles đã hiển thị ở profile.
    @Test
    void profile_forRegularUser_withRoleGroup_shouldEmbedMergedRolesIntoTdTokenClaim() {
        when(getUserOrganizationProfileUseCase.execute("nhanvien06")).thenReturn(new UserOrganizationProfileDto());
        var roleGroupConfig = new UserRoleGroupConfigDto();
        roleGroupConfig.setPermissionCodes(List.of("QCapNhatVBDen"));
        when(getUserRoleGroupConfigUseCase.execute("nhanvien06")).thenReturn(roleGroupConfig);
        when(tdTokenService.issue(any(), anyMap())).thenReturn("fake-tdtoken");
        var controller = newController();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "nhanvien06")
            .claim("realm_access", Map.of("roles", List.of("USER")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        controller.profile(authentication);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(tdTokenService).issue(any(AuthenticatedUser.class), claimsCaptor.capture());
        String tokenRoles = (String) claimsCaptor.getValue().get(TdTokenService.ROLES_CLAIM);
        assertThat(tokenRoles).contains("USER").contains("QCapNhatVBDen");
    }

    // BL-118: fullName/position/coquan/donvi/phongban/caplanhdao phải lấy từ hồ sơ AppUser +
    // Organization THẬT (GetUserOrganizationProfileUseCase) — KHÔNG còn lấy fullName từ claim
    // Keycloak/hardcode rỗng như trước.
    @Test
    void profile_forRegularUser_shouldUseRealOrganizationProfile_notKeycloakClaimOrHardcode() {
        lenient().when(tdTokenService.issue(any(), anyMap())).thenReturn("fake-tdtoken");
        when(getUserRoleGroupConfigUseCase.execute("nhanvien03")).thenReturn(new UserRoleGroupConfigDto());
        var orgProfile = new UserOrganizationProfileDto();
        orgProfile.setUsername("nhanvien03");
        orgProfile.setFullName("Nguyễn Văn A");
        orgProfile.setPosition("Chuyên viên");
        orgProfile.setCapLanhDao("khong");
        orgProfile.setAgencyName("Văn phòng Bộ");
        orgProfile.setAgencyId("agency-uuid");
        orgProfile.setUnitName("Phòng Kế hoạch - Tài chính");
        orgProfile.setUnitId("unit-uuid");
        orgProfile.setDepartmentName("");
        when(getUserOrganizationProfileUseCase.execute("nhanvien03")).thenReturn(orgProfile);
        var controller = newController();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "nhanvien03")
            .claim("name", "Ten Tu Keycloak (khong duoc dung)")
            .claim("realm_access", Map.of("roles", List.of("USER")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        var response = controller.profile(authentication);

        assertThat(response.getBody().get("fullName")).isEqualTo("Nguyễn Văn A");
        assertThat(response.getBody().get("name")).isEqualTo("Nguyễn Văn A");
        assertThat(response.getBody().get("position")).isEqualTo("Chuyên viên");
        assertThat(response.getBody().get("agency")).isEqualTo("Văn phòng Bộ");
        assertThat(response.getBody().get("coquan")).isEqualTo("Văn phòng Bộ");
        assertThat(response.getBody().get("unit")).isEqualTo("Phòng Kế hoạch - Tài chính");
        assertThat(response.getBody().get("donvi")).isEqualTo("Phòng Kế hoạch - Tài chính");
    }

    // BL-118: "Kiêm nhiệm" — user_other_obj phải là hồ sơ ĐẦY ĐỦ (org + quyền/menu riêng + tdtoken
    // riêng) cho từng chức vụ kiêm nhiệm, đúng cơ chế Domino thật (không phải chỉ danh sách rỗng).
    @Test
    @SuppressWarnings("unchecked")
    void profile_withKiemNhiem_shouldIncludeFullSubProfilesInUserOtherObj() {
        when(tdTokenService.issue(any(), anyMap())).thenReturn("fake-tdtoken");
        var orgProfile = new UserOrganizationProfileDto();
        orgProfile.setUsername("nhanvien04");
        orgProfile.setFullName("Trần Thị B");

        var kiemNhiem = new UserOrganizationProfileDto();
        kiemNhiem.setUsername("nhanvien04-kn1");
        kiemNhiem.setFullName("Trần Thị B (kiêm nhiệm)");
        kiemNhiem.setPosition("Phó phòng");
        orgProfile.setKiemNhiem(List.of(kiemNhiem));

        when(getUserOrganizationProfileUseCase.execute("nhanvien04")).thenReturn(orgProfile);
        when(getUserRoleGroupConfigUseCase.execute("nhanvien04")).thenReturn(new UserRoleGroupConfigDto());
        when(getUserRoleGroupConfigUseCase.execute("nhanvien04-kn1")).thenReturn(new UserRoleGroupConfigDto());
        var controller = newController();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "nhanvien04")
            .claim("realm_access", Map.of("roles", List.of("USER")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        var response = controller.profile(authentication);

        var userOtherObj = (List<Map<String, Object>>) response.getBody().get("user_other_obj");
        assertThat(userOtherObj).hasSize(1);
        Map<String, Object> knProfile = userOtherObj.get(0);
        assertThat(knProfile.get("username")).isEqualTo("nhanvien04-kn1");
        assertThat(knProfile.get("fullName")).isEqualTo("Trần Thị B (kiêm nhiệm)");
        assertThat(knProfile.get("position")).isEqualTo("Phó phòng");
        assertThat(knProfile.get("tdtoken")).isEqualTo("fake-tdtoken");
    }

    @Test
    void profile_withoutKiemNhiem_shouldNotIncludeUserOtherObjKey() {
        lenient().when(tdTokenService.issue(any(), anyMap())).thenReturn("fake-tdtoken");
        when(getUserRoleGroupConfigUseCase.execute(anyString())).thenReturn(new UserRoleGroupConfigDto());
        when(getUserOrganizationProfileUseCase.execute(anyString())).thenReturn(new UserOrganizationProfileDto());
        var controller = newController();

        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", "nhanvien05")
            .claim("realm_access", Map.of("roles", List.of("USER")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        var response = controller.profile(authentication);

        assertThat(response.getBody()).doesNotContainKey("user_other_obj");
    }

    // N16 (03/09/2026, xem javadoc getCauHinh()): mac dinh (khong cau hinh qua application.yml/bien
    // moi truong) PHAI giu nguyen hanh vi an nut "Chuyen VBQP" hien co (cfg_chuyenvbqp="0").
    @Test
    void getCauHinh_withDefaultConfig_shouldReturnVbqpDisabled() {
        when(getSystemConfigurationUseCase.execute()).thenReturn(Map.of());
        var controller = newController();

        var response = controller.getCauHinh();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("cfg_chuyenvbqp")).isEqualTo("0");
        assertThat(data.get("cfg_vbpq_mailin")).isEqualTo("");
    }

    // Operator co the bat qua VBDEN_VBQP_ENABLED/VBDEN_VBQP_MAILIN (app.vbden.vbqp.*).
    @Test
    void getCauHinh_withVbqpEnabledViaConfig_shouldReturnEnabledAndMailin() {
        when(getSystemConfigurationUseCase.execute()).thenReturn(Map.of());
        var controller = newController();
        ReflectionTestUtils.setField(controller, "vbdenVbqpEnabled", true);
        ReflectionTestUtils.setField(controller, "vbdenVbqpMailin", "vbqp@example.com");

        var response = controller.getCauHinh();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("cfg_chuyenvbqp")).isEqualTo("1");
        assertThat(data.get("cfg_vbpq_mailin")).isEqualTo("vbqp@example.com");
    }

    // 16/09/2026 (bugfix "modal Chuyen xu ly chua nhan cau hinh -> Chuyen chu tri"): cfg_* cua TO
    // CHUC (vd cfg_chuyenchutri thiet lap o "Co cau to chuc") phai duoc tra ve, VA phai GHI DE 2
    // default vbqp o tren neu to chuc da tu cau hinh field do.
    @Test
    void getCauHinh_withOrganizationCfgFields_shouldMergeAndOverrideDefaults() {
        when(getSystemConfigurationUseCase.execute()).thenReturn(Map.of(
            "cfg_chuyenchutri", "1",
            "cfg_chuyenvbqp", "1"
        ));
        var controller = newController();

        var response = controller.getCauHinh();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data.get("cfg_chuyenchutri")).isEqualTo("1");
        // To chuc da tu cau hinh cfg_chuyenvbqp="1" - phai thang the default env var (false/"0").
        assertThat(data.get("cfg_chuyenvbqp")).isEqualTo("1");
        assertThat(data.get("cfg_vbpq_mailin")).isEqualTo("");
    }
}
