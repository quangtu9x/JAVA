package com.td.infrastructure.tenant;

import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.infrastructure.security.AuthenticatedUser;
import com.td.infrastructure.security.TdTokenAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantResolverTest {

    private TenantProperties tenantProperties;
    private TenantActiveRegistry tenantActiveRegistry;
    private ResolveUserTenantUseCase resolveUserTenantUseCase;
    private TenantResolver tenantResolver;

    @BeforeEach
    void setUp() {
        tenantProperties = new TenantProperties();
        tenantProperties.setDefaultTenantId("H05");

        // BL-117: danh sach tenant gio la registry runtime (TenantActiveRegistry, nap tu bang
        // "tenants" database control-plane) thay vi YAML tinh (TenantProperties.tenants, da bo).
        tenantActiveRegistry = new TenantActiveRegistry();
        tenantActiveRegistry.register("H05", true);
        tenantActiveRegistry.register("K05", true);

        // BL-118: bridge theo username - mac dinh khong tim thay gi (Optional.empty), tung test rieng
        // se stub lai khi can kiem tra nhanh bridge nay.
        resolveUserTenantUseCase = mock(ResolveUserTenantUseCase.class);
        lenient().when(resolveUserTenantUseCase.execute(anyString())).thenReturn(Optional.empty());

        tenantResolver = new TenantResolver(tenantProperties, tenantActiveRegistry, resolveUserTenantUseCase);
    }

    @Test
    void resolve_shouldReturnDefaultTenant_whenAuthenticationIsNull() {
        assertThat(tenantResolver.resolve(null)).isEqualTo("H05");
    }

    @Test
    void resolve_shouldReadTenantIdClaim_fromKeycloakJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("tenant_id", "K05")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("K05");
    }

    @Test
    void resolve_shouldFallbackToDefault_whenKeycloakJwtHasNoTenantClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "user1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of());

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("H05");
    }

    @Test
    void resolve_shouldReadIdentifierClaim_fromLegacyTdtoken_whenNoTenantIdClaim() {
        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("nguyenvana")
            .roles(Set.of("USER"))
            .claims(Map.of("identifier", "K05", "dbpath", "_ubnd"))
            .build();
        TdTokenAuthenticationToken authentication = new TdTokenAuthenticationToken(user, "raw-token", List.of());

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("K05");
    }

    @Test
    void resolve_shouldPreferTenantIdClaim_overIdentifier_forTdtoken() {
        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("nguyenvana")
            .roles(Set.of("USER"))
            .claims(Map.of("tenant_id", "K05", "identifier", "H05"))
            .build();
        TdTokenAuthenticationToken authentication = new TdTokenAuthenticationToken(user, "raw-token", List.of());

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("K05");
    }

    @Test
    void resolve_shouldFallbackToDefault_whenClaimedTenantIsNotProvisioned() {
        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("nguyenvana")
            .roles(Set.of("USER"))
            .claims(Map.of("identifier", "UNKNOWN_TENANT"))
            .build();
        TdTokenAuthenticationToken authentication = new TdTokenAuthenticationToken(user, "raw-token", List.of());

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("H05");
    }

    @Test
    void resolve_shouldFallbackToDefault_whenTdtokenHasNoRelevantClaims() {
        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("nguyenvana")
            .roles(Set.of("USER"))
            .claims(Map.of())
            .build();
        TdTokenAuthenticationToken authentication = new TdTokenAuthenticationToken(user, "raw-token", List.of());

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("H05");
    }

    // BL-117: tenant XÁC ĐỊNH ĐƯỢC (biết) nhưng đã bị deactivate — PHẢI từ chối hẳn (không fallback
    // về tenant mặc định, tránh rò rỉ dữ liệu chéo tenant khi user của tenant bị khoá vẫn đăng nhập
    // được nhưng lại vô tình thấy dữ liệu của tenant mặc định).
    @Test
    void resolve_shouldThrowTenantNotActive_whenClaimedTenantIsKnownButDeactivated() {
        tenantActiveRegistry.markInactive("K05");

        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("nguyenvana")
            .roles(Set.of("USER"))
            .claims(Map.of("tenant_id", "K05"))
            .build();
        TdTokenAuthenticationToken authentication = new TdTokenAuthenticationToken(user, "raw-token", List.of());

        assertThatThrownBy(() -> tenantResolver.resolve(authentication))
            .isInstanceOf(TenantNotActiveException.class);
    }

    // BL-118 (bugfix NGHIÊM TRỌNG, 20/08/2026): Keycloak JWT THỰC TẾ không mang claim tenant_id (chưa
    // cấu hình protocol mapper) — trước đây nhánh này LUÔN fallback về tenant mặc định, gây "Thêm mới
    // báo thành công nhưng lưu sai database của đơn vị". Nay phải tra thêm qua ResolveUserTenantUseCase
    // theo username trước khi chịu fallback.
    @Test
    void resolve_shouldUseUserTenantBridge_whenKeycloakJwtHasNoTenantClaim() {
        when(resolveUserTenantUseCase.execute("nhanvien01")).thenReturn(Optional.of("K05"));
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "nhanvien01")
            .claim("preferred_username", "nhanvien01")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("K05");
    }

    @Test
    void resolve_shouldUseUserTenantBridge_forTdtoken_whenNoRelevantClaims() {
        when(resolveUserTenantUseCase.execute("nguyenvana")).thenReturn(Optional.of("K05"));
        AuthenticatedUser user = AuthenticatedUser.builder()
            .username("nguyenvana")
            .roles(Set.of("USER"))
            .claims(Map.of())
            .build();
        TdTokenAuthenticationToken authentication = new TdTokenAuthenticationToken(user, "raw-token", List.of());

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("K05");
    }

    @Test
    void resolve_shouldFallbackToDefault_whenBridgeAlsoFindsNothing() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "user-chua-tung-co-appuser")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of());

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("H05");
    }

    // Loai Authentication khong nhan dien duoc (khong phai Jwt/Tdtoken) - khong duoc nem loi, chi
    // khong xac dinh duoc username nen khong goi bridge, fallback ve mac dinh nhu truoc gio.
    @Test
    void resolve_shouldFallbackToDefault_forUnrecognizedAuthenticationType() {
        var authentication = new TestingAuthenticationToken("nguyenvana", "n/a");

        assertThat(tenantResolver.resolve(authentication)).isEqualTo("H05");
    }
}
