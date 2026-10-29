package com.td.infrastructure.security;

import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 28/10/2026 (rà soát bảo mật): ai được thêm/sửa người dùng ở màn "Người dùng" — dùng trong
 * {@code @PreAuthorize("@userAdministrationAccess.canManageUsers(authentication)")}.
 *
 * <p>Trước đây {@code POST/PUT /api/v1/shared-core/users} chỉ cần ROLE_USER — mà MỌI tài khoản đăng nhập
 * đều có ROLE_USER ({@code KeycloakJwtConverter} BL-118) — nên bất kỳ ai cũng gửi được
 * {@code PUT /users/{id của mình}} với {@code updateRoleGroups=true, roleGroupIds=[nhóm quản trị]} để
 * TỰ NÂNG QUYỀN, hoặc sửa đơn vị/khoá/định danh của người khác; ảnh chữ ký chính (tự đóng lên văn bản
 * ký số) của người khác cũng bị thay được.
 *
 * <p>Điều kiện "quản trị người dùng" bám đúng điều kiện FE đang dùng để hiện màn này
 * ({@code AppRoutes.tsx}/{@code PrivateRoutes.tsx}) — không suy diễn thêm, tránh khoá nhầm quản trị đơn vị:
 * <ul>
 *   <li>superAdmin ({@link AccountProvisioningChecker#isSuperAdmin}: role ADMIN hoặc tài khoản {@code admin}) —
 *   FE coi là {@code access == "Manager"};</li>
 *   <li>quyền Nhóm vai trò {@code CVQuanTri}/{@code CVQuanTriDV} (authority {@code ROLE_CVQUANTRI}/
 *   {@code ROLE_CVQUANTRIDV} — lưu ý authority luôn VIẾT HOA);</li>
 *   <li>Nhóm vai trò có mục menu {@code menu_qlvb} = "Người dùng".</li>
 * </ul>
 */
@Component("userAdministrationAccess")
@RequiredArgsConstructor
public class UserAdministrationAccess {

    static final String USER_MENU_FIELD = "menu_qlvb";
    static final String USER_MENU_ITEM = "Người dùng";
    private static final Set<String> ADMIN_AUTHORITIES = Set.of(
        "ROLE_ADMIN", "ROLE_PLATFORM_ADMIN", "ROLE_CVQUANTRI", "ROLE_CVQUANTRIDV");

    private final AccountProvisioningChecker accountProvisioningChecker;
    private final GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;
    private final AppUserRepository appUserRepository;

    public boolean canManageUsers(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String username = authentication.getName();
        if (accountProvisioningChecker.isSuperAdmin(username, Set.of())) {
            return true;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (ADMIN_AUTHORITIES.contains(authority.getAuthority())) {
                return true;
            }
        }
        if (username == null || username.isBlank()) {
            return false;
        }
        var config = getUserRoleGroupConfigUseCase.execute(username);
        List<String> menu = config == null || config.getMenuFields() == null
            ? null : config.getMenuFields().get(USER_MENU_FIELD);
        return menu != null && menu.contains(USER_MENU_ITEM);
    }

    /** Chính chủ tài khoản {@code userId} (vd. tự gắn ảnh chữ ký của mình) hoặc quản trị người dùng. */
    public boolean isSelfOrCanManageUsers(Authentication authentication, UUID userId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (userId != null && authentication.getName() != null) {
            boolean self = appUserRepository.findByIdAndDeletedOnIsNull(userId)
                .map(user -> authentication.getName().equalsIgnoreCase(user.getUsername()))
                .orElse(false);
            if (self) {
                return true;
            }
        }
        return canManageUsers(authentication);
    }
}
