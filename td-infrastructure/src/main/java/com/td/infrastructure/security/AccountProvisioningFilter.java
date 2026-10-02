package com.td.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Yêu cầu người dùng (22/09/2026, "khóa triệt để"): trước bugfix này, {@link KeycloakJwtConverter}
 * cấp {@code ROLE_USER} (và mọi quyền RoleGroup tra được) cho MỌI JWT Keycloak xác thực thành
 * công — KHÔNG kiểm tra tài khoản có bản ghi {@code AppUser} hay không. Hậu quả: dù
 * {@code QLVBCompatAuthController#authen}/{@code #profile} đã bị chặn (patch trước), 1 tài khoản
 * Keycloak hợp lệ nhưng chưa khai báo VẪN gọi được thẳng mọi endpoint {@code /api/v1/*} khác bằng
 * chính JWT Keycloak gốc (FE luôn gửi kèm header {@code Authorization}, xem
 * {@code AuthHelpers.ts#setupAxios}) — 2 patch trước chỉ chặn được đúng 2 endpoint đăng nhập, không
 * chặn được toàn hệ thống.
 *
 * <p>Filter này là điểm chặn TRUNG TÂM, DUY NHẤT, áp dụng cho CẢ {@link JwtAuthenticationToken}
 * (Keycloak) LẪN {@link TdTokenAuthenticationToken} (phòng vệ sâu — Tdtoken phát hành TRƯỚC patch
 * accountProvisioned vẫn còn hiệu lực tới khi hết hạn tự nhiên, TTL mặc định 8 giờ, xem
 * {@code TdTokenService}, và KHÔNG được re-verify AppUser tại thời điểm xác thực chữ ký).
 *
 * <p>Đặt SAU {@code BearerTokenAuthenticationFilter} (Authentication đã sẵn sàng) nhưng TRƯỚC
 * {@code TenantFilter} (xem {@code SecurityConfig}) — tài khoản chưa khai báo KHÔNG được để lọt tới
 * bước resolve tenant, tránh rơi vào {@code TenantResolver} fallback {@code defaultTenantId} và thấy
 * được dữ liệu của tenant mặc định.
 *
 * <p><b>Miễn trừ có chủ đích</b> {@code /qlvb/authen}/{@code /qlvb/profile}: 2 endpoint này TỰ xử lý
 * case chưa khai báo theo hợp đồng riêng FE đã tích hợp (trả {@code accountProvisioned: false} thay
 * vì 403 thô, xem javadoc {@code QLVBCompatAuthController#isAccountProvisioned}) — nếu filter này
 * chặn trước, FE sẽ nhận 403 JSON chung chung thay vì thông báo rõ ràng, hỏng UX đã xây riêng cho 2
 * endpoint đó. Mọi endpoint {@code /api/v1/*} khác (không có hợp đồng riêng nào cho case này) đều bị
 * chặn — kể cả {@code /qlvb/danhmuc/GetCauHinh} (chỉ được FE gọi SAU khi profile đã xác nhận
 * provisioned, xem {@code Auth.tsx#fetchSystemConfig}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningFilter extends OncePerRequestFilter {

    private static final Set<String> EXEMPT_PATHS = Set.of("/qlvb/authen", "/qlvb/profile");

    private final AccountProvisioningChecker accountProvisioningChecker;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (EXEMPT_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = extractUsername(authentication);
        if (username != null && !accountProvisioningChecker.isProvisioned(username, extractRoles(authentication))) {
            log.warn("Chan request {} {}: tai khoan '{}' chua duoc khai bao trong danh muc nguoi dung (app_users)",
                request.getMethod(), request.getRequestURI(), username);
            SecurityResponseWriter.writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                "Tài khoản chưa được khai báo và cấp quyền truy cập trong hệ thống.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Cùng quy tắc lấy username với {@code KeycloakJwtConverter#resolveUsername}/
     * {@code QLVBCompatAuthController#resolvePrincipal} (ưu tiên preferred_username). Trả về
     * {@code null} cho request chưa xác thực (anonymous/permitAll) — filter bỏ qua, không chặn. */
    private String extractUsername(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String preferredUsername = jwtAuthentication.getToken().getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
            String subject = jwtAuthentication.getToken().getSubject();
            return (subject == null || subject.isBlank()) ? null : subject;
        }
        if (authentication instanceof TdTokenAuthenticationToken tdTokenAuthentication
                && tdTokenAuthentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getUsername();
        }
        return null;
    }

    /** {@code GrantedAuthority} luôn có tiền tố {@code ROLE_} (xem KeycloakJwtConverter/
     * TdTokenAuthenticationFilter) — bóc lại để khớp quy ước role THÔ mà AccountProvisioningChecker
     * dùng (cùng {@code AuthenticatedUser#getRoles()}). */
    private Set<String> extractRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority().startsWith("ROLE_")
                ? authority.getAuthority().substring("ROLE_".length())
                : authority.getAuthority())
            .collect(Collectors.toSet());
    }
}
