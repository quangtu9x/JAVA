package com.td.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BL-118 (bugfix 19/08/2026): xác nhận Tdtoken hợp lệ luôn nhận thêm quyền sàn {@code ROLE_USER},
 * kể cả khi token chỉ chứa quyền nghiệp vụ Domino cũ (không có "USER") — xem javadoc
 * {@link TdTokenAuthenticationFilter}.
 */
@ExtendWith(MockitoExtension.class)
class TdTokenAuthenticationFilterTest {

    @Mock
    private TdTokenService tdTokenService;
    @Mock
    private AuthenticationEntryPoint authenticationEntryPoint;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_validTdtokenWithoutUserRole_shouldStillGrantBaselineRoleUser() throws Exception {
        when(request.getHeader(TdTokenAuthenticationFilter.TDTOKEN_HEADER)).thenReturn("valid-token");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("skhcn")
            .name("SKHCN")
            .roles(Set.of("QCAPNHATVBDEN"))
            .build();
        when(tdTokenService.verify("valid-token")).thenReturn(user);

        new TdTokenAuthenticationFilter(tdTokenService, authenticationEntryPoint)
            .doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authorityValues(authentication)).containsExactlyInAnyOrder("ROLE_QCAPNHATVBDEN", "ROLE_USER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_invalidTdtoken_shouldNotSetAuthentication() throws Exception {
        when(request.getHeader(TdTokenAuthenticationFilter.TDTOKEN_HEADER)).thenReturn("bad-token");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        when(tdTokenService.verify("bad-token")).thenThrow(new TdTokenAuthenticationException("Sai token"));

        new TdTokenAuthenticationFilter(tdTokenService, authenticationEntryPoint)
            .doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authenticationEntryPoint).commence(any(), any(), any());
    }

    @Test
    void doFilter_authorizationHeaderPresent_shouldSkipTdtoken() throws Exception {
        when(request.getHeader(TdTokenAuthenticationFilter.TDTOKEN_HEADER)).thenReturn("some-token");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer keycloak-token");

        new TdTokenAuthenticationFilter(tdTokenService, authenticationEntryPoint)
            .doFilter(request, response, filterChain);

        verifyNoInteractions(tdTokenService);
        verify(filterChain).doFilter(request, response);
    }

    private Set<String> authorityValues(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    }
}
