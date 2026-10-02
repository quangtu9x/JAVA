package com.td.infrastructure.security;

import com.td.application.sharedcore.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Yêu cầu người dùng (22/09/2026, "khóa triệt để" tiếp theo bugfix accountProvisioned ở
 * {@code QLVBCompatAuthController}): định nghĩa DÙNG CHUNG cho "tài khoản quản trị tối cao" (miễn
 * trừ luật chưa khai báo) và "tài khoản đã được khai báo/cấp quyền" (có bản ghi {@code AppUser}) —
 * trước đây 2 khái niệm này chỉ tồn tại RIÊNG bên trong {@code QLVBCompatAuthController}
 * ({@code isSuperAdmin}/{@code appUserExists}), không dùng lại được cho {@link AccountProvisioningFilter}
 * (chặn MỌI request {@code /api/v1/*}, không chỉ 2 endpoint đăng nhập). Tách ra bean riêng để 2 nơi
 * LUÔN đồng nhất định nghĩa, tránh lệch nhau về sau.
 */
@Component
@RequiredArgsConstructor
public class AccountProvisioningChecker {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String SUPER_ADMIN_USERNAME = "admin";

    private final AppUserRepository appUserRepository;

    /** Tài khoản quản trị tối cao: username {@code admin} hoặc có role Keycloak {@code ADMIN} (không phân biệt hoa/thường). */
    public boolean isSuperAdmin(String username, Set<String> roles) {
        if (SUPER_ADMIN_USERNAME.equalsIgnoreCase(username)) {
            return true;
        }
        return roles != null && roles.stream().anyMatch(role -> ADMIN_ROLE.equalsIgnoreCase(role));
    }

    /**
     * {@code true} nếu tài khoản được phép vào hệ thống: superAdmin (bootstrap qua Keycloak Admin
     * Console, chưa từng cần có {@code AppUser}) HOẶC có bản ghi {@code AppUser} tương ứng (đã được
     * khai báo/cấp quyền ở màn "Người dùng"). Dùng {@code existsByUsernameAndDeletedOnIsNull} (1
     * query {@code EXISTS} rẻ) thay vì tải cả hồ sơ tổ chức — {@link AccountProvisioningFilter} gọi
     * hàm này cho MỌI request, không có sẵn {@code UserOrganizationProfileDto} như
     * {@code QLVBCompatAuthController} (nơi đó vẫn tự suy "found" từ profile đã tải, không gọi lại
     * đây, tránh 1 query thừa).
     */
    public boolean isProvisioned(String username, Set<String> roles) {
        if (username == null || username.isBlank()) {
            return false;
        }
        if (isSuperAdmin(username, roles)) {
            return true;
        }
        return appUserRepository.existsByUsernameAndDeletedOnIsNull(username);
    }
}
