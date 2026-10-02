package com.td.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Khóa triệt để" (22/09/2026) — xem javadoc {@link AccountProvisioningFilter}: chặn MỌI request
 * (Keycloak lẫn Tdtoken) từ tài khoản chưa có {@code AppUser}, trừ 2 endpoint đăng nhập tự xử lý
 * riêng ({@code /qlvb/authen}/{@code /qlvb/profile}).
 */
@ExtendWith(MockitoExtension.class)
class AccountProvisioningFilterTest {

    @Mock
    private AccountProvisioningChecker accountProvisioningChecker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AccountProvisioningFilter newFilter() {
        return new AccountProvisioningFilter(accountProvisioningChecker, objectMapper);
    }

    private Jwt jwtFor(String username) {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("preferred_username", username)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }

    @Test
    void doFilter_forExemptLoginPaths_shouldSkipCheckEvenIfNotProvisioned() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtFor("chuakhaibao01"), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/qlvb/authen");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        newFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(accountProvisioningChecker, never()).isProvisioned(any(), any());
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_withNoAuthentication_shouldPassThrough() throws Exception {
        SecurityContextHolder.clearContext();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/shared-core/users");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        newFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_forProvisionedKeycloakUser_shouldPassThrough() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtFor("nhanvien01"), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(accountProvisioningChecker.isProvisioned("nhanvien01", Set.of("USER"))).thenReturn(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/shared-core/users");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        newFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_forNotProvisionedKeycloakUser_shouldReturn403_andNotContinueChain() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtFor("chuakhaibao02"), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(accountProvisioningChecker.isProvisioned("chuakhaibao02", Set.of("USER"))).thenReturn(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/shared-core/users");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);

        newFilter().doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_forNotProvisionedTdToken_shouldReturn403_andNotContinueChain() throws Exception {
        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("chuakhaibao03")
            .roles(Set.of("USER"))
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new TdTokenAuthenticationToken(user, "raw-token", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(accountProvisioningChecker.isProvisioned("chuakhaibao03", Set.of("USER"))).thenReturn(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/shared-core/users");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);

        newFilter().doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_forSuperAdmin_shouldPassThroughEvenWithoutAppUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthenticationToken(jwtFor("admin"), List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(accountProvisioningChecker.isProvisioned("admin", Set.of("ADMIN"))).thenReturn(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/shared-core/users");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        newFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        SecurityContextHolder.clearContext();
    }
}
