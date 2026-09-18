package com.td.infrastructure.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.infrastructure.security.AuthenticatedUser;
import com.td.infrastructure.security.TdTokenAuthenticationToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // BL-118: bridge theo username - khong lien quan toi hanh vi cua chinh TenantFilter (da co
    // TenantResolverTest rieng kiem tra bridge nay) - stub luon rong de giu nguyen hanh vi cac test co.
    private ResolveUserTenantUseCase stubResolveUserTenantUseCase() {
        ResolveUserTenantUseCase useCase = mock(ResolveUserTenantUseCase.class);
        lenient().when(useCase.execute(anyString())).thenReturn(java.util.Optional.empty());
        return useCase;
    }

    @Test
    void doFilter_shouldSetTenantContext_duringChain_andClearAfter() throws Exception {
        TenantProperties properties = new TenantProperties();
        properties.setDefaultTenantId("H05");
        TenantActiveRegistry registry = new TenantActiveRegistry();
        registry.register("H05", true);

        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("nguyenvana", "n/a"));

        TenantFilter filter = new TenantFilter(
            new TenantResolver(properties, registry, stubResolveUserTenantUseCase()), new ObjectMapper());

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<String> tenantSeenDuringChain = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            tenantSeenDuringChain.set(TenantContext.getCurrentTenant());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(tenantSeenDuringChain.get()).isEqualTo("H05");
        assertThat(TenantContext.getCurrentTenant())
            .as("TenantContext phai duoc clear sau khi request ket thuc, tranh ro ri sang request khac dung chung thread")
            .isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldClearTenantContext_evenWhenChainThrows() {
        TenantProperties properties = new TenantProperties();
        properties.setDefaultTenantId("H05");
        TenantActiveRegistry registry = new TenantActiveRegistry();
        registry.register("H05", true);

        TenantFilter filter = new TenantFilter(
            new TenantResolver(properties, registry, stubResolveUserTenantUseCase()), new ObjectMapper());

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThat(TenantContext.getCurrentTenant()).isNull();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
            filter.doFilter(request, response, (req, res) -> {
                throw new RuntimeException("loi gia lap trong controller");
            }));

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    // BL-117: tenant bị deactivate -> 403 ngay tại filter, KHÔNG cho vào filter chain/controller.
    @Test
    void doFilter_shouldReturn403_andNotContinueChain_whenTenantIsDeactivated() throws Exception {
        TenantProperties properties = new TenantProperties();
        properties.setDefaultTenantId("H05");
        TenantActiveRegistry registry = new TenantActiveRegistry();
        registry.register("H05", true);
        registry.register("K05", false);

        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("nguyenvana")
            .roles(Set.of("USER"))
            .claims(Map.of("tenant_id", "K05"))
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new TdTokenAuthenticationToken(user, "raw-token", List.of()));

        TenantFilter filter = new TenantFilter(
            new TenantResolver(properties, registry, stubResolveUserTenantUseCase()), new ObjectMapper());

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
