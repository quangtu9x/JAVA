package com.td.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication Bridge (BL-004): cho phép request xác thực bằng header {@code Tdtoken} (JWT cũ)
 * song song với {@code Authorization: Bearer} (Keycloak, xử lý bởi
 * {@code BearerTokenAuthenticationFilter} có sẵn ngay sau filter này trong chain).
 *
 * <p>Quy tắc ưu tiên: nếu request có {@code Authorization} header, bỏ qua Tdtoken và để luồng
 * Keycloak xử lý — tránh 2 cơ chế xác thực tranh nhau trên cùng 1 request. Nếu không có
 * {@code Tdtoken} header, bỏ qua filter này hoàn toàn (request đi tiếp bình thường, kể cả khi
 * không có token nào — trường hợp đó sẽ bị chặn ở bước authorization phía sau với 401 chung).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TdTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String TDTOKEN_HEADER = "Tdtoken";
    private static final String ROLE_PREFIX = "ROLE_";

    private final TdTokenService tdTokenService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = request.getHeader(TDTOKEN_HEADER);
        if (!StringUtils.hasText(token) || StringUtils.hasText(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthenticatedUser user = tdTokenService.verify(token);
            Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toCollection(java.util.HashSet::new));
            // BL-118 (bugfix 19/08/2026): cùng lý do KeycloakJwtConverter — Tdtoken cũng chỉ chứa
            // quyền nghiệp vụ Domino cũ (QCapNhatVBDen...), không có "USER"; cấp thêm quyền sàn để
            // mọi Tdtoken hợp lệ đều qua được hasAnyRole('USER', ...) ở các controller danh mục.
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + "USER"));

            SecurityContextHolder.getContext()
                .setAuthentication(new TdTokenAuthenticationToken(user, token, authorities));
            filterChain.doFilter(request, response);
        } catch (TdTokenAuthenticationException ex) {
            SecurityContextHolder.clearContext();
            log.warn("Xac thuc Tdtoken that bai cho request {}: {}", request.getRequestURI(), ex.getMessage());
            authenticationEntryPoint.commence(request, response, ex);
        }
    }
}
