package com.td.web.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.sharedcore.GetSystemConfigurationUseCase;
import com.td.application.sharedcore.GetUserOrganizationProfileUseCase;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.sharedcore.UserOrganizationProfileDto;
import com.td.application.sharedcore.UserRoleGroupConfigDto;
import com.td.infrastructure.security.AccountProvisioningChecker;
import com.td.infrastructure.security.AuthenticatedUser;
import com.td.infrastructure.security.TdTokenAuthenticationToken;
import com.td.infrastructure.security.TdTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bộ endpoint "đăng nhập tương thích FE cũ" (ThanhHoaDEV) — 3 gọi mà FE
 * {@code src/app/modules/auth/**} thực hiện trực tiếp bằng axios (KHÔNG qua baseAPI):
 * <ul>
 *   <li>{@code POST /qlvb/authen} — đổi {@code Authorization: Bearer <Keycloak>} lấy JWT nội bộ
 *       {@code Tdtoken} (xem {@link TdTokenService#issue(AuthenticatedUser)}).</li>
 *   <li>{@code GET /qlvb/profile} — trả hồ sơ người dùng {@code UserModel} theo hợp đồng FE.</li>
 *   <li>{@code GET /qlvb/danhmuc/GetCauHinh} — trả cấu hình hệ thống.</li>
 * </ul>
 *
 * <p><b>QUAN TRỌNG</b> — 3 response dưới đây KHÔNG bọc {@code ApiResponse}: FE đọc
 * {@code response.data.token} (authen) và {@code response.data} (profile, GetCauHinh) trực tiếp từ
 * body — đúng Domino TDMessage cũ (object phẳng). Đây là ngoại lệ có chủ đích, tách khỏi chuẩn
 * envelope của dự án (BL-003).</p>
 *
 * <p>Phạm vi hiện tại: mục tiêu MVP "đăng nhập được + tài khoản quản trị cao nhất". Các module
 * nghiệp vụ khác (vbden, vbdi...) vẫn ở {@code /api/v1/*} — chưa expose route compat
 * {@code /qlvb/*} cho các module đó.</p>
 */
@RestController
@RequestMapping("/qlvb")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "QLVB Compat Auth", description = "Đăng nhập tương thích FE cũ (ThanhHoaDev)")
public class QLVBCompatAuthController {

    private static final String MANAGER_ACCESS = "Manager";
    private static final String DEFAULT_USER_ACCESS = "User";
    // Fallback CHỈ dùng khi tài khoản "quản trị tối cao" không có bản ghi AppUser thật đi kèm (vd
    // "admin" bootstrap tạo qua Keycloak Admin Console, chưa từng tạo hồ sơ ở màn "Người dùng") —
    // giữ giá trị demo cũ để không hồi quy trải nghiệm test/demo hiện có. Mọi tài khoản CÓ AppUser
    // thật (kể cả admin thật) đều dùng dữ liệu tổ chức THẬT qua GetUserOrganizationProfileUseCase.
    private static final String COHQUAN_THANH_HOA = "H56";
    private static final String SUPER_ADMIN_CAP_LANH_DAO_FALLBACK = "Tỉnh";

    private final TdTokenService tdTokenService;
    private final GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;
    private final GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;
    private final GetSystemConfigurationUseCase getSystemConfigurationUseCase;
    // "Khóa triệt để" (22/09/2026): định nghĩa "superAdmin" giờ dùng CHUNG với AccountProvisioningFilter
    // (chặn mọi /api/v1/*) — xem javadoc AccountProvisioningChecker, tránh lệch định nghĩa giữa 2 nơi.
    private final AccountProvisioningChecker accountProvisioningChecker;

    // N16 (03/09/2026) - xem javadoc getCauHinh() ve ly do 2 field nay ton tai va gia tri mac dinh.
    // Cap nhat 16/09/2026: CHI con dung lam DEFAULT khi to chuc CHUA tung cau hinh 2 field nay o
    // "Co cau to chuc" (GetSystemConfigurationUseCase da doc duoc gia tri that tu Organization,
    // xem javadoc getCauHinh() ben duoi).
    @Value("${app.vbden.vbqp.enabled:false}")
    private boolean vbdenVbqpEnabled;
    @Value("${app.vbden.vbqp.mailin:}")
    private String vbdenVbqpMailin;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SUPER_ADMIN_ROLES =
        "CVQuanTri;QCapNhatVBDen;QCapNhatVBDenDV;QCapNhatVBDenPB;QCapNhatVBDi;QCapNhatVBDiDV;"
            + "QCapNhatVBDiPB;QPhanCong;QPhanPhoi;ADMIN;USER;";

    private static final List<String> MENU_TOP_ADMIN = List.of(
        "Quản trị",
        "Văn bản đến",
        "Văn bản đi",
        "Văn bản dự thảo",
        "Hồ sơ công việc",
        "VB chỉ đạo điều hành",
        "Hồ sơ lưu trữ",
        "TL cuộc họp"
    );

    /** menu_qlvb phủ các mục con thường thấy của phân hệ QLVB (FE hiển thị qua CheckShowMenu). */
    private static final List<String> MENU_QLVB_ADMIN = List.of(
        "Người dùng", "Vai trò", "Cơ cấu tổ chức", "Danh mục",
        "Sổ văn bản", "Cấu hình hệ thống", "Báo cáo", "Dữ liệu quá khứ"
    );

    /** Lấy Tdtoken mới từ {@code Authorization: Bearer} (Keycloak). Body FE có thể gửi
     * {@code {ipAddress}} — API không dùng, giữ để tương thích. */
    @PostMapping(value = "/authen", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Đăng nhập FE cũ — đổi Bearer Keycloak lấy Tdtoken (tương thích qlvb/authen)")
    public ResponseEntity<Map<String, Object>> authen(
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        AuthenticatedUser user = resolvePrincipal(authentication);
        boolean superAdmin = isSuperAdmin(user);

        // BL-118 (tiếp, 20/08/2026): Tdtoken phát hành kèm claim tổ chức (identifier/identifiers/
        // level/unit/department/agency/...) — xem javadoc TdTokenService#issue(AuthenticatedUser,
        // Duration, Map). Trước đây token chỉ có sub/username/name/roles, khiến mọi endpoint Domino
        // còn lại chưa migrate mà đọc field tổ chức từ token đều nhận null (ca đã phát hiện:
        // qlvb/danhmuc/DanhSachNguoiDung lọc theo dbpath luôn trả rỗng).
        UserOrganizationProfileDto orgProfile = getUserOrganizationProfileUseCase.execute(user.getUsername());

        // Yêu cầu người dùng (22/09/2026, "tài khoản đăng nhập được ở hệ thống đăng nhập nhưng chưa
        // khai báo trong danh mục thì không cho vào hệ thống"): trước đây KHÔNG có nhánh nào kiểm tra
        // AppUser tồn tại — tài khoản Keycloak hợp lệ nhưng chưa có bản ghi trong bảng app_users (vd
        // vừa tạo ở Keycloak, chưa từng khai báo ở màn "Người dùng") vẫn được cấp Tdtoken hợp lệ và
        // vào thẳng hệ thống với hồ sơ tổ chức rỗng. Chặn tại đây (KHÔNG cấp token) — FE (Auth.tsx#
        // getTDAuth) đọc field "accountProvisioned" TRƯỚC field "token", điều hướng sang trang thông
        // báo tĩnh thay vì lưu phiên đăng nhập. Không áp dụng cho superAdmin (tài khoản quản trị tối
        // cao bootstrap qua Keycloak Admin Console, xem javadoc COHQUAN_THANH_HOA).
        if (!isAccountProvisioned(superAdmin, orgProfile)) {
            log.warn("Dang nhap bi tu choi: tai khoan '{}' xac thuc Keycloak thanh cong nhung chua "
                + "duoc khai bao trong danh muc nguoi dung (app_users)", user.getUsername());
            return ResponseEntity.ok(buildNotProvisionedAuthResponse());
        }
        // 06/10/2026 (yêu cầu người dùng "khoá rồi thì khi đăng nhập hiển thị trang thông báo"): tài
        // khoản đã khai báo nhưng đang bị Khoá - KHÔNG cấp token, FE điều hướng trang taikhoanbikhoa.html.
        if (isAccountLocked(superAdmin, orgProfile)) {
            log.warn("Dang nhap bi tu choi: tai khoan '{}' dang bi khoa", user.getUsername());
            return ResponseEntity.ok(buildLockedAuthResponse());
        }

        // Bổ sung 20/08/2026 (bugfix nghiêm trọng): claim "roles" của Tdtoken trước đây CHỈ chứa role
        // Keycloak thật của user (vd chỉ "USER") — quyền nghiệp vụ QLVB (QCapNhatVBDen/QPhanCong/...)
        // gán qua "Nhóm vai trò" (RoleGroup, BL-111) CHƯA BAO GIỜ được đưa vào token thật, dù đã hiện
        // đúng trong response qlvb/profile (chỉ để FE HIỂN THỊ). Hậu quả: mọi @PreAuthorize dùng role
        // nghiệp vụ (vd VbdenController#tiepNhan yêu cầu QCapNhatVBDen) LUÔN trả 403 cho tài khoản
        // chỉ có quyền qua RoleGroup (không phải role Keycloak thật) — đúng nguyên nhân báo lỗi
        // "Khong co quyen thuc hien thao tac nay" ở POST /api/v1/vbden/TiepNhan.
        UserRoleGroupConfigDto roleGroupConfig = superAdmin ? null : getUserRoleGroupConfigUseCase.execute(user.getUsername());
        String tdToken = tdTokenService.issue(user, buildTdTokenClaims(user, orgProfile, superAdmin, roleGroupConfig));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", tdToken);
        response.put("refreshToken", "");
        response.put("refreshTokenExpiryTime", "");
        response.put("isAuthSuccessful", Boolean.TRUE);
        response.put("isTfaEnabled", Boolean.FALSE);
        log.info("Dang nhap thanh cong cho user '{}' (roles={})", user.getUsername(), user.getRoles());
        return ResponseEntity.ok(response);
    }

    /** Hồ sơ người dùng dạng {@code UserModel} của FE. User có role Keycloak {@code ADMIN}
     * (hoặc username {@code admin}) → trả hồ sơ quản trị tối cao: access=Manager, đủ menu_*. */
    @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lấy hồ sơ người dùng (tương thích qlvb/profile)", description = "Response phẳng, không bọc ApiResponse")
    public ResponseEntity<Map<String, Object>> profile(Authentication authentication) {
        AuthenticatedUser user = resolvePrincipal(authentication);
        boolean superAdmin = isSuperAdmin(user);
        UserOrganizationProfileDto orgProfile = getUserOrganizationProfileUseCase.execute(user.getUsername());

        // Cùng chặn như authen() ở trên (xem javadoc isAccountProvisioned) — endpoint này là 1 lối
        // vào ĐỘC LẬP (chỉ cần Keycloak JWT hoặc Tdtoken hợp lệ, không bắt buộc phải qua /authen
        // trước), nên phải kiểm tra lại ở đây thay vì tin tưởng /authen đã chặn.
        if (!isAccountProvisioned(superAdmin, orgProfile)) {
            log.warn("Lay ho so bi tu choi: tai khoan '{}' xac thuc Keycloak thanh cong nhung chua "
                + "duoc khai bao trong danh muc nguoi dung (app_users)", user.getUsername());
            return ResponseEntity.ok(buildNotProvisionedProfileResponse(user.getUsername()));
        }
        if (isAccountLocked(superAdmin, orgProfile)) {
            log.warn("Lay ho so bi tu choi: tai khoan '{}' dang bi khoa", user.getUsername());
            return ResponseEntity.ok(buildLockedProfileResponse(user.getUsername()));
        }

        // Fetch ĐÚNG 1 lần, dùng chung cho buildProfile() (hiển thị) lẫn buildTdTokenClaims() (claim
        // thật trong token) — tránh gọi GetUserRoleGroupConfigUseCase 2 lần trong cùng 1 request.
        UserRoleGroupConfigDto roleGroupConfig = superAdmin ? null : getUserRoleGroupConfigUseCase.execute(user.getUsername());
        Map<String, Object> profile = buildProfile(user, orgProfile, superAdmin, roleGroupConfig);
        // FE ghi đè userProfile.tdtoken bằng token từ /authen, gửi kèm để chủ động trước. Kèm claim
        // tổ chức + roles đã hợp nhất Nhóm vai trò (BL-118 tiếp, bugfix 403 TiepNhan).
        profile.put("tdtoken", tdTokenService.issue(user, buildTdTokenClaims(user, orgProfile, superAdmin, roleGroupConfig)));
        return ResponseEntity.ok(profile);
    }

    /**
     * Cấu hình hệ thống tối giản (đủ cho dashboard dùng).
     *
     * <p><b>Bổ sung 03/09/2026 (N16, xem javadoc {@code VBdenChuyenVanBanPhapQuyUseCase})</b>: Domino
     * thật ({@code GetCauHinh.java}) trả MỌI field {@code cfg_*} của tổ chức người dùng bọc trong 1
     * key {@code data} ({@code msg.setAnyObject(objItem)}) — FE đọc {@code systemConfig?.data?.
     * cfg_chuyenvbqp}/{@code cfg_vbpq_mailin} để hiện nút "Chuyển VBQP". Response tối giản trước đây
     * KHÔNG có key {@code data} nên 2 field đó luôn {@code undefined} (nút luôn ẩn, dù có cấu hình
     * Domino thật hay không). Bổ sung {@code data} chỉ với 2 key này, đọc qua {@code app.vbden.vbqp.*}
     * — LÚC ĐÓ hệ thống chưa có màn "Cơ cấu tổ chức" lưu {@code cfg_*} theo tổ chức.</p>
     *
     * <p><b>Cập nhật 16/09/2026 (yêu cầu người dùng: "modal Chuyển xử lý của văn bản đến chưa nhận
     * cấu hình -> Chuyển chủ trì có 2 lựa chọn là chọn một và chọn nhiều", cấu hình thiết lập ở "Cơ
     * cấu tổ chức")</b>: từ khi {@code organizationApi.js}/{@code ChiTietModalCoQuan.jsx} đã lưu ĐỦ
     * {@code cfg_*} (bao gồm {@code cfg_chuyenchutri} và 5 field khác) vào
     * {@code Organization.dynamicFieldsJson}, response cứng ở trên KHÔNG BAO GIỜ phản ánh giá trị
     * người dùng đã cấu hình — port {@link GetSystemConfigurationUseCase} (đọc ĐÚNG tổ chức của user
     * hiện tại, quét mọi field {@code cfg_*}, xem javadoc use case đó về thuật toán/đối chiếu Domino).
     * 2 field {@code cfg_chuyenvbqp}/{@code cfg_vbpq_mailin} từ biến môi trường CHỈ còn là DEFAULT khi
     * tổ chức chưa tự cấu hình field đó (org ghi đè default nếu có) — giữ nguyên hành vi cũ cho tổ
     * chức chưa từng đụng tới 2 field này.</p>
     */
    @GetMapping(value = "/danhmuc/GetCauHinh", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lấy cấu hình hệ thống (tương thích qlvb/danhmuc/GetCauHinh)")
    public ResponseEntity<Map<String, Object>> getCauHinh() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("appName", "Hệ thống Quản lý Văn bản và Hồ sơ điện tử");
        config.put("version", "1.0.0");
        config.put("serverIp", "localhost");
        config.put("features", Map.of());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cfg_chuyenvbqp", vbdenVbqpEnabled ? "1" : "0");
        data.put("cfg_vbpq_mailin", vbdenVbqpMailin == null ? "" : vbdenVbqpMailin);
        data.putAll(getSystemConfigurationUseCase.execute());
        config.put("data", data);
        return ResponseEntity.ok(config);
    }
// ---------------------------------------------------------------------------------------------
    // Hỗ trợ mapper / profile
    // ---------------------------------------------------------------------------------------------

    /** Map mọi cơ chế xác thực (Keycloak hay Tdtoken) về {@link AuthenticatedUser}. */
    private AuthenticatedUser resolvePrincipal(Authentication authentication) {
        if (authentication instanceof TdTokenAuthenticationToken tdToken) {
            if (tdToken.getPrincipal() instanceof AuthenticatedUser user) {
                return user;
            }
            throw new IllegalArgumentException("Du lieu xac thuc Tdtoken khong hop le");
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Khong xac dinh duoc username tu token Keycloak");
            }
            return AuthenticatedUser.builder()
                .username(username)
                .name(firstNonBlank(jwt.getClaimAsString("name"), username))
                .roles(realmRoles(jwt))
                .claims(new LinkedHashMap<>(jwt.getClaims()))
                .build();
        }

        throw new IllegalStateException("Yeu cau chua duoc xac thuc (thieu Authorization/Tdtoken)");
    }

    /** Đọc {@code realm_access.roles} từ JWT Keycloak. */
    private Set<String> realmRoles(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return roles;
        }
        Object raw = realmAccess.get("roles");
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof String role && !role.isBlank()) {
                    roles.add(role.trim());
                }
            }
        }
        return roles;
    }
/**
     * Build {@code UserModel} theo sát {@code _models.ts} của FE.
     *
     * <p>BL-118 (19/08/2026): trước đây {@code fullName} lấy từ claim Keycloak (không phải hồ sơ
     * thật) và {@code position}/{@code coquan}/{@code donvi}/{@code caplanhdao} hardcode rỗng/hằng
     * số cố định cho MỌI tài khoản — thay bằng dữ liệu THẬT của AppUser + Organization qua
     * {@link GetUserOrganizationProfileUseCase} (đối chiếu BE_THANHHOA
     * {@code TDDatabaseConnection.getJackSonUserInformationAndPermissions}/{@code ImportNguoiDung}
     * để đúng ý nghĩa field gốc). Đồng thời bổ sung "Kiêm nhiệm" ({@code user_other_obj}) — xác
     * nhận qua BE_THANHHOA đây KHÔNG chỉ là khái niệm quản trị (màn "Người dùng") mà THẬT SỰ được
     * nhúng vào payload đăng nhập để FE hiện nút "Chuyển vai trò" (xem {@code HeaderUserMenu.jsx}).
     */
    private Map<String, Object> buildProfile(AuthenticatedUser user, UserOrganizationProfileDto orgProfile,
                                              boolean superAdmin, UserRoleGroupConfigDto roleGroupConfig) {
        Map<String, Object> profile = buildBaseProfile(user.getUsername(), user.getName(), orgProfile, superAdmin);

        if (superAdmin) {
            profile.put("access", MANAGER_ACCESS);
            profile.put("roles", buildSuperAdminRoles(user));
            profile.put("menu_top", MENU_TOP_ADMIN);
            profile.put("menu_qlvb", MENU_QLVB_ADMIN);
            profile.put("menu_vbden", List.of("Văn bản đến"));
            profile.put("menu_vbdi", List.of("Văn bản đi"));
            profile.put("menu_duthao", List.of("Văn bản dự thảo"));
            profile.put("menu_hosocv", List.of("Hồ sơ công việc"));
            profile.put("menu_vbcddh", List.of("VB chỉ đạo điều hành"));
            profile.put("menu_lichct", List.of("Lịch công tác"));
            profile.put("menu_chiasetep", List.of("Chia sẻ tệp"));
            profile.put("menu_hosoluutru", List.of("Hồ sơ lưu trữ"));
            profile.put("menu_tlch", List.of("TL cuộc họp"));
            profile.put("menu_phieutrinh", List.of("Phiếu trình"));
        } else {
            profile.put("access", DEFAULT_USER_ACCESS);

            // Bổ sung 19/08/2026 (bugfix): user KHÔNG có role Keycloak trực tiếp mà chỉ được cấp
            // quyền/menu qua "Nhóm vai trò" (RoleGroup) trước đây hoàn toàn bị bỏ sót ở đây — tra cứu
            // thêm để hợp nhất vào, cùng tinh thần buildSuperAdminRoles() (hợp nhất, không ghi đè).
            // roleGroupConfig được fetch 1 lần ở caller (profile()/buildKiemNhiemProfile()), dùng
            // chung cho cả hiển thị lẫn claim token thật (BL-118 tiếp, bugfix 403 TiepNhan).
            applyRoleGroupFields(profile, user.getRoles(), roleGroupConfig);
        }

        // "Kiêm nhiệm" (BL-118) — mỗi mục là 1 hồ sơ ĐẦY ĐỦ (org info + quyền/menu riêng + tdtoken
        // riêng) để FE chuyển vai trò ngay không cần round-trip khác, đúng cơ chế Domino thật.
        if (!orgProfile.getKiemNhiem().isEmpty()) {
            profile.put("user_other_obj", orgProfile.getKiemNhiem().stream()
                .map(this::buildKiemNhiemProfile)
                .toList());
        }

        return profile;
    }

    /** Hồ sơ base (id/tên/tổ chức) dùng chung cho user chính lẫn từng mục kiêm nhiệm. */
    private Map<String, Object> buildBaseProfile(String username, String fallbackName,
                                                  UserOrganizationProfileDto orgProfile, boolean superAdmin) {
        Map<String, Object> profile = new LinkedHashMap<>();
        String fullName = firstNonBlank(orgProfile.getFullName(), fallbackName);
        profile.put("id", stableId(username));
        // Bổ sung 22/09/2026 (bugfix): "id" ở trên là số giả lập ổn định theo username (xem javadoc
        // stableId) - KHÔNG PHẢI UUID PK thật của AppUser. Field MỚI này mới là UUID thật, cần cho
        // MỌI lần FE gọi endpoint shared-core đòi UUID (vd SharedCoreUsersController#getSignatureImages)
        // với "chính tài khoản đang đăng nhập" thay vì 1 user khác chọn từ danh sách quản trị (nơi đã
        // có sẵn UUID thật từ GET /api/v1/shared-core/users). "" nếu không tìm thấy AppUser tương ứng.
        profile.put("appUserId", orgProfile.getAppUserId());
        // Bổ sung 22/09/2026 (bugfix): field "phonenumber" (lowercase, đúng quy ước FE
        // TdPdfViewerSignModal.jsx/tdsimsign/tdsimpki.jsx đang đọc) TỪNG BỊ THIẾU HOÀN TOÀN ở đây -
        // AppUser.phoneNumber có dữ liệu thật trong DB nhưng chưa từng lộ ra qlvb/profile.
        profile.put("phonenumber", orgProfile.getPhoneNumber());
        profile.put("userId", username);
        profile.put("username", username);
        profile.put("fullName", fullName);
        profile.put("name", fullName);
        profile.put("type", 0);
        profile.put("language", "vi");
        applyOrganizationFields(profile, orgProfile, superAdmin);
        return profile;
    }

    /**
     * Điền field tổ chức thật (position/caplanhdao/agency/unit/department/agency_level) —
     * {@code found = false} (không có AppUser tương ứng) chỉ xảy ra với tài khoản "quản trị tối
     * cao" bootstrap, dùng fallback demo cũ cho trường hợp đó, MỌI tài khoản khác dùng dữ liệu thật.
     */
    private void applyOrganizationFields(Map<String, Object> profile, UserOrganizationProfileDto org, boolean superAdmin) {
        var resolved = resolveAgencyAndCapLanhDao(org, superAdmin);
        String agency = resolved.agency();
        String capLanhDao = resolved.capLanhDao();

        profile.put("position", org.getPosition());
        profile.put("caplanhdao", capLanhDao);
        profile.put("identifier", org.getIdentifier());
        profile.put("level", org.getLevel());
        // BL-118 (tiếp): "identifiers" (chuỗi tổ tiên, lá→gốc) — trước đây cố tình để trống vì chưa
        // xác nhận thứ tự ghép chuỗi thật; nay đã xác nhận qua BE_THANHHOA (xem javadoc
        // UserOrganizationProfileDto#getIdentifiers) — FE getIdentifier_by_level() dùng field này
        // cùng "level" ở trên để tính identifier hiệu lực theo cấp (BL-2xx: chọn sổ văn bản mặc định).
        profile.put("identifiers", org.getIdentifiers());
        // Bổ sung 19/08/2026 (2): "parentids" — chuỗi UNID tổ tiên (Domino) ≈ chuỗi UUID Organization
        // (Java, không có UNID thật để tái tạo) — trước đây KHÔNG được set, làm FE crash ở
        // get_ParentId_by_level() (utils/global.jsx) khi mở "Thêm mới" văn bản đến (SubFormPhanVanBan
        // /SubFormSaoY gọi hàm này ngay khi render). Xem javadoc UserOrganizationProfileDto#getParentIds.
        profile.put("parentids", org.getParentIds());
        // Bổ sung 25/09/2026: "parents" — chuỗi TÊN tổ tiên (lá → gốc), xem javadoc
        // UserOrganizationProfileDto#getParents (FE getTenDonvi_by_level crash khi thiếu field này).
        profile.put("parents", org.getParents());
        // Tên field THẬT theo đúng payload Domino gốc (xem legacyAdminProfile.json + BE_THANHHOA
        // ImportNguoiDung.java/LuuDanhMuc.java) — HeaderUserMenu.jsx đọc trực tiếp các field này.
        profile.put("agency", agency);
        profile.put("agency_id", org.getAgencyId());
        profile.put("agency_level", org.getAgencyLevelName());
        profile.put("agency_level_id", org.getAgencyLevelId());
        profile.put("unit", org.getUnitName());
        profile.put("unit_id", org.getUnitId());
        profile.put("department", org.getDepartmentName());
        profile.put("department_id", org.getDepartmentId());
        // Alias mà 1 số màn khác đang đọc trực tiếp trên currentUser (vd duthao/ChiTietModal.jsx
        // dùng coquan/donvi/phongban/phongbanid để tự điền "đơn vị soạn thảo" mặc định) — cùng giá
        // trị, khác tên field, giữ CẢ HAI để không phá 2 nhóm màn đang dùng 2 quy ước tên khác nhau.
        profile.put("coquan", agency);
        profile.put("donvi", org.getUnitName());
        profile.put("phongban", org.getDepartmentName());
        profile.put("phongbanid", org.getDepartmentId());
    }

    /** Hồ sơ đầy đủ cho 1 mục "Kiêm nhiệm" — không đệ quy (1 tài khoản kiêm nhiệm không có kiêm-nhiệm-con). */
    private Map<String, Object> buildKiemNhiemProfile(UserOrganizationProfileDto kiemNhiem) {
        Map<String, Object> profile = buildBaseProfile(kiemNhiem.getUsername(), kiemNhiem.getFullName(), kiemNhiem, false);
        UserRoleGroupConfigDto roleGroupConfig = getUserRoleGroupConfigUseCase.execute(kiemNhiem.getUsername());
        // Tài khoản kiêm nhiệm không có JWT Keycloak riêng — chỉ có quyền qua Nhóm vai trò của
        // chính username kiêm nhiệm đó (không hợp nhất role Keycloak của tài khoản chính).
        applyRoleGroupFields(profile, Set.of(), roleGroupConfig);
        AuthenticatedUser kiemNhiemUser = AuthenticatedUser.builder()
            .username(kiemNhiem.getUsername())
            .name(kiemNhiem.getFullName())
            .build();
        // Token riêng để FE chuyển vai trò ngay (handleClickCanhan) không cần đăng nhập lại — đúng
        // cơ chế TDAuthentication.CreateKiemNhiemAuthenToken() ở BE_THANHHOA. Kèm claim tổ chức
        // (BL-118 tiếp) của CHÍNH mục kiêm nhiệm này — không phải của tài khoản chính. Kèm luôn claim
        // "roles" đã hợp nhất Nhóm vai trò của username kiêm nhiệm (bugfix 403 TiepNhan) — trước đây
        // thiếu, khiến token kiêm nhiệm KHÔNG có quyền nghiệp vụ nào dù profile hiển thị đúng.
        profile.put("tdtoken", tdTokenService.issue(kiemNhiemUser, buildTdTokenClaims(kiemNhiemUser, kiemNhiem, false, roleGroupConfig)));
        return profile;
    }

    /** agency/capLanhDao đã áp fallback "quản trị tối cao bootstrap" — dùng chung cho profile lẫn claim Tdtoken. */
    private record ResolvedAgency(String agency, String capLanhDao) {}

    private ResolvedAgency resolveAgencyAndCapLanhDao(UserOrganizationProfileDto org, boolean superAdmin) {
        boolean found = appUserExists(org);
        String agency = org.getAgencyName();
        String capLanhDao = org.getCapLanhDao();
        if (superAdmin && !found) {
            agency = COHQUAN_THANH_HOA;
            capLanhDao = SUPER_ADMIN_CAP_LANH_DAO_FALLBACK;
        }
        return new ResolvedAgency(agency, capLanhDao);
    }

    /**
     * BL-118 (tiếp, 20/08/2026): claim tổ chức nhúng vào Tdtoken khi phát hành — CÙNG tên field mà
     * {@code TDTokenObject} (BE_THANHHOA Domino thật) kỳ vọng, để mọi endpoint Domino còn lại chưa
     * migrate đọc field tổ chức từ token (qua {@code TDDatabaseConnection}) nhận đúng giá trị thay vì
     * null. Chỉ đưa vào claim có giá trị (bỏ qua rỗng/null) — giữ token gọn, khớp cách Domino thật
     * cũng để trống field không áp dụng thay vì gửi chuỗi rỗng.
     */
    private Map<String, Object> buildTdTokenOrgClaims(UserOrganizationProfileDto org, boolean superAdmin) {
        var resolved = resolveAgencyAndCapLanhDao(org, superAdmin);
        Map<String, Object> claims = new LinkedHashMap<>();
        putIfNotBlank(claims, "identifier", org.getIdentifier());
        putIfNotBlank(claims, "identifiers", org.getIdentifiers());
        putIfNotBlank(claims, "parentids", org.getParentIds());
        putIfNotBlank(claims, "parents", org.getParents());
        putIfNotBlank(claims, "level", org.getLevel());
        putIfNotBlank(claims, "position", org.getPosition());
        putIfNotBlank(claims, "caplanhdao", resolved.capLanhDao());
        putIfNotBlank(claims, "unit", org.getUnitName());
        putIfNotBlank(claims, "unit_id", org.getUnitId());
        putIfNotBlank(claims, "department", org.getDepartmentName());
        putIfNotBlank(claims, "department_id", org.getDepartmentId());
        putIfNotBlank(claims, "agency", resolved.agency());
        putIfNotBlank(claims, "agency_id", org.getAgencyId());
        putIfNotBlank(claims, "agency_level", org.getAgencyLevelName());
        return claims;
    }

    private void putIfNotBlank(Map<String, Object> claims, String key, String value) {
        if (value != null && !value.isBlank()) {
            claims.put(key, value);
        }
    }

    /**
     * BL-118 (tiếp, 20/08/2026, bugfix nghiêm trọng): claim {@code roles} nhúng vào Tdtoken khi phát
     * hành — PHẢI là danh sách ĐÃ HỢP NHẤT (role Keycloak/JWT thật ∪ quyền nghiệp vụ từ Nhóm vai
     * trò), khớp CHÍNH XÁC những gì {@code qlvb/profile} hiển thị cho FE. Trước đây claim này chỉ
     * lấy {@code user.getRoles()} (role Keycloak thật), khiến {@code @PreAuthorize(hasAnyRole(...))}
     * ở các controller dùng role nghiệp vụ QLVB (vd {@link VbdenController#tiepNhan}, cần
     * {@code QCapNhatVBDen}) LUÔN từ chối (403) tài khoản chỉ có quyền qua Nhóm vai trò — group này
     * KHÔNG BAO GIỜ tồn tại như 1 role Keycloak/JWT thật, đúng bản chất thiết kế RoleGroup (BL-111).
     */
    private Map<String, Object> buildTdTokenClaims(AuthenticatedUser user, UserOrganizationProfileDto orgProfile,
                                                    boolean superAdmin, UserRoleGroupConfigDto roleGroupConfig) {
        Map<String, Object> claims = new LinkedHashMap<>(buildTdTokenOrgClaims(orgProfile, superAdmin));
        claims.put(TdTokenService.ROLES_CLAIM, resolveMergedRoles(user, superAdmin, roleGroupConfig));
        return claims;
    }

    private String resolveMergedRoles(AuthenticatedUser user, boolean superAdmin, UserRoleGroupConfigDto roleGroupConfig) {
        if (superAdmin) {
            return buildSuperAdminRoles(user);
        }
        return mergeRoles(user.getRoles(), roleGroupConfig);
    }

    /** Hợp nhất role Keycloak/JWT thật với quyền nghiệp vụ từ Nhóm vai trò (RoleGroup, BL-111). */
    private String mergeRoles(Set<String> baseRoles, UserRoleGroupConfigDto roleGroupConfig) {
        Set<String> roleCodes = new LinkedHashSet<>(baseRoles);
        roleCodes.addAll(roleGroupConfig.getPermissionCodes());
        roleCodes.remove("");
        return roleCodes.isEmpty() ? "" : String.join(";", roleCodes) + ";";
    }

    /** roles/menu_* — dùng chung cho user chính (roles JWT ∪ Nhóm vai trò) và kiêm nhiệm (chỉ Nhóm vai trò). */
    private void applyRoleGroupFields(Map<String, Object> profile, Set<String> baseRoles, UserRoleGroupConfigDto roleGroupConfig) {
        profile.put("roles", mergeRoles(baseRoles, roleGroupConfig));

        Map<String, List<String>> menuFields = roleGroupConfig.getMenuFields();
        // menu_top: chuỗi nối ";" (KHÁC các menu_* còn lại) — đúng định dạng gốc Domino, xem
        // legacyAdminProfile.json (FE dùng .includes() trực tiếp trên chuỗi, không JSON.parse).
        profile.put("menu_top", joinSemicolon(menuFields.get("menu_top")));
        // Các menu_* còn lại: chuỗi JSON-encode 1 mảng (FE CheckShowMenu() gọi JSON.parse() lên
        // đúng field này — gửi mảng JS trần (List<String> Jackson serialize) sẽ KHÔNG khớp kiểu
        // "string" mà CheckShowMenu() yêu cầu, luôn bị coi là "không có quyền xem menu").
        profile.put("menu_qlvb", toJsonArrayString(menuFields.get("menu_qlvb")));
        profile.put("menu_vbden", toJsonArrayString(menuFields.get("menu_vbden")));
        profile.put("menu_vbdi", toJsonArrayString(menuFields.get("menu_vbdi")));
        profile.put("menu_duthao", toJsonArrayString(menuFields.get("menu_duthao")));
        profile.put("menu_hosocv", toJsonArrayString(menuFields.get("menu_hosocv")));
        profile.put("menu_vbcddh", toJsonArrayString(menuFields.get("menu_vbcddh")));
        profile.put("menu_lichct", toJsonArrayString(menuFields.get("menu_lichct")));
        profile.put("menu_chiasetep", toJsonArrayString(menuFields.get("menu_chiasetep")));
        profile.put("menu_hosoluutru", toJsonArrayString(menuFields.get("menu_hosoluutru")));
        profile.put("menu_tlch", toJsonArrayString(menuFields.get("menu_tlch")));
        profile.put("menu_phieutrinh", toJsonArrayString(menuFields.get("menu_phieutrinh")));
    }

    /** id số nguyên giả lập ổn định theo username (UserModel.id là number, không có id Domino thật để dùng). */
    private int stableId(String username) {
        return 1 + (username.hashCode() & 0x7fffffff);
    }

    /** menu_top dùng chuỗi nối ";" (có ";" cuối) — KHÁC các menu_* còn lại, xem javadoc buildProfile. */
    private String joinSemicolon(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return String.join(";", items) + ";";
    }

    /** Các menu_* (trừ menu_top) cần đúng 1 chuỗi JSON-encode mảng — xem javadoc buildProfile. */
    private String toJsonArrayString(List<String> items) {
        try {
            return OBJECT_MAPPER.writeValueAsString(items == null ? List.of() : items);
        } catch (Exception ex) {
            return "[]";
        }
    }

    /**
     * BL-117 (bugfix): {@code SUPER_ADMIN_ROLES} là danh sách quyền Domino cũ hard-code trước khi
     * có khái niệm role nền tảng — trước đây GHI ĐÈ HẲN roles thật của user bằng đúng hằng số này,
     * làm mất mọi role Keycloak THẬT của tài khoản (vd {@code PLATFORM_ADMIN}, gán qua Keycloak
     * Admin Console/realm-export, không nằm trong hằng số cứng) dù JWT gốc có đủ. Hợp nhất THÊM
     * role thật của user vào danh sách cố định này — tài khoản "quản trị tối cao" luôn có đủ mọi
     * quyền legacy CỘNG THÊM mọi role mới gán qua Keycloak trong tương lai, không cần sửa lại hằng
     * số này mỗi lần thêm 1 role nền tảng mới.
     */
    private String buildSuperAdminRoles(AuthenticatedUser user) {
        Set<String> merged = new LinkedHashSet<>(Arrays.asList(SUPER_ADMIN_ROLES.split(";")));
        merged.addAll(user.getRoles());
        merged.remove("");
        return String.join(";", merged) + ";";
    }

    /** Tài khoản quản trị tối cao: username {@code admin} hoặc có role Keycloak {@code ADMIN} — xem
     * javadoc {@link AccountProvisioningChecker#isSuperAdmin}. */
    private boolean isSuperAdmin(AuthenticatedUser user) {
        return accountProvisioningChecker.isSuperAdmin(user.getUsername(), user.getRoles());
    }

    /** {@code true} nếu {@code org} được suy ra từ 1 bản ghi {@code AppUser} thật (xem javadoc
     * GetUserOrganizationProfileUseCase#execute — trả DTO rỗng, username null, khi không tìm thấy). */
    private boolean appUserExists(UserOrganizationProfileDto org) {
        return org.getUsername() != null;
    }

    /**
     * Yêu cầu người dùng (22/09/2026): tài khoản xác thực Keycloak thành công nhưng CHƯA có bản ghi
     * {@code AppUser} tương ứng (chưa được khai báo/cấp quyền ở màn "Người dùng") KHÔNG được coi là
     * "đã đăng nhập" — dùng chung cho cả {@link #authen} và {@link #profile}. superAdmin (bootstrap
     * qua Keycloak Admin Console, xem {@code COHQUAN_THANH_HOA}) LUÔN được coi là provisioned dù
     * chưa có {@code AppUser} — giữ đúng hành vi demo/test cũ.
     */
    private boolean isAccountProvisioned(boolean superAdmin, UserOrganizationProfileDto org) {
        return superAdmin || appUserExists(org);
    }

    private static final String NOT_PROVISIONED_MESSAGE =
        "Tài khoản chưa được khai báo và cấp quyền truy cập trong hệ thống. Vui lòng liên hệ quản trị viên.";

    /** Response {@code AuthModel}-shaped nhưng KHÔNG có token hợp lệ — xem javadoc isAccountProvisioned. */
    private Map<String, Object> buildNotProvisionedAuthResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", null);
        response.put("refreshToken", "");
        response.put("refreshTokenExpiryTime", "");
        response.put("isAuthSuccessful", Boolean.FALSE);
        response.put("isTfaEnabled", Boolean.FALSE);
        response.put("accountProvisioned", Boolean.FALSE);
        response.put("message", NOT_PROVISIONED_MESSAGE);
        return response;
    }

    /**
     * 06/10/2026: tài khoản có AppUser nhưng đang bị Khoá ({@code isActive=false}). superAdmin miễn
     * trừ (tránh tự khoá mất quyền quản trị tối cao), cùng quy tắc với isAccountProvisioned.
     */
    private boolean isAccountLocked(boolean superAdmin, UserOrganizationProfileDto org) {
        return !superAdmin && appUserExists(org) && !org.isActive();
    }

    private static final String LOCKED_MESSAGE =
        "Tài khoản của bạn đã bị khoá. Vui lòng liên hệ quản trị viên để được mở khoá.";

    /** Cùng khuôn buildNotProvisionedAuthResponse - FE (Auth.tsx#getTDAuth) đọc "accountLocked". */
    private Map<String, Object> buildLockedAuthResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", null);
        response.put("refreshToken", "");
        response.put("refreshTokenExpiryTime", "");
        response.put("isAuthSuccessful", Boolean.FALSE);
        response.put("isTfaEnabled", Boolean.FALSE);
        response.put("accountLocked", Boolean.TRUE);
        response.put("message", LOCKED_MESSAGE);
        return response;
    }

    private Map<String, Object> buildLockedProfileResponse(String username) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username", username);
        profile.put("accountLocked", Boolean.TRUE);
        profile.put("message", LOCKED_MESSAGE);
        return profile;
    }

    /** Response {@code UserModel}-shaped tối giản, KHÔNG kèm tdtoken — xem javadoc isAccountProvisioned. */
    private Map<String, Object> buildNotProvisionedProfileResponse(String username) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username", username);
        profile.put("accountProvisioned", Boolean.FALSE);
        profile.put("message", NOT_PROVISIONED_MESSAGE);
        return profile;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}