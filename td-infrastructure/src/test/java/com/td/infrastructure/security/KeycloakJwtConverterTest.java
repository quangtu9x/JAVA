package com.td.infrastructure.security;

import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.sharedcore.UserRoleGroupConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * BL-118 (bugfix 19/08/2026): xác nhận MỌI JWT Keycloak xác thực thành công đều nhận thêm quyền
 * sàn {@code ROLE_USER} — kể cả khi tài khoản chưa từng được gán role Keycloak nào (trường hợp phổ
 * biến với tài khoản staff thật, vd "skhcn" — xem javadoc {@link KeycloakJwtConverter}).
 *
 * <p>Bổ sung 20/08/2026 (bugfix NGHIÊM TRỌNG): đây MỚI là converter THẬT SỰ quyết định quyền cho
 * mọi request {@code /api/v1/*} (FE luôn gửi {@code Authorization} khiến {@code
 * TdTokenAuthenticationFilter} luôn bỏ qua Tdtoken) — nên quyền nghiệp vụ QLVB gán qua "Nhóm vai
 * trò" (RoleGroup/AppPermission, BL-111) PHẢI được tra cứu và cấp thêm NGAY TẠI ĐÂY, không phải chỉ
 * ở claim Tdtoken (vốn không có tác dụng thực tế cho luồng Keycloak).</p>
 *
 * <p>Bổ sung 20/08/2026 (tiếp, bugfix NGHIÊM TRỌNG khác): {@code GetUserRoleGroupConfigUseCase} tra
 * cứu qua {@code AppUserRepository}/RoleGroup — các entity này là "danh mục dùng chung", sống CỐ
 * ĐỊNH ở 1 database duy nhất ({@code identityEntityManagerFactory}), độc lập hoàn toàn với tenant của
 * request đang xử lý — nên converter này KHÔNG cần (và không còn) thao tác {@code TenantContext} gì
 * cả trước khi gọi, luôn tra đúng ngay lập tức.</p>
 */
@ExtendWith(MockitoExtension.class)
class KeycloakJwtConverterTest {

    @Mock
    private GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;

    private KeycloakJwtConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeycloakJwtConverter(getUserRoleGroupConfigUseCase);
        lenient().when(getUserRoleGroupConfigUseCase.execute(anyString())).thenReturn(new UserRoleGroupConfigDto());
    }

    @Test
    void convert_userWithNoKeycloakRoles_shouldStillGetBaselineRoleUser() {
        Jwt jwt = jwtWithClaims(Map.of("sub", "skhcn"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorityValues(authorities)).containsExactly("ROLE_USER");
    }

    @Test
    void convert_userWithRealmRoles_shouldMergeBaselineRoleUserOnTop() {
        Jwt jwt = jwtWithClaims(Map.of(
            "sub", "admin",
            "realm_access", Map.of("roles", List.of("ADMIN"))
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorityValues(authorities)).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void convert_userWithClientRoles_shouldMergeBaselineRoleUserOnTop() {
        Jwt jwt = jwtWithClaims(Map.of(
            "sub", "brand-manager",
            "resource_access", Map.of("td-webapi-client", Map.of("roles", List.of("BRAND_MANAGER")))
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorityValues(authorities)).containsExactlyInAnyOrder("ROLE_BRAND_MANAGER", "ROLE_USER");
    }

    @Test
    void convert_userAlreadyHavingRoleUser_shouldNotDuplicate() {
        Jwt jwt = jwtWithClaims(Map.of(
            "sub", "user1",
            "realm_access", Map.of("roles", List.of("USER"))
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorityValues(authorities)).containsExactly("ROLE_USER");
    }

    // Bổ sung 20/08/2026 (bugfix chính của đợt này).
    @Test
    void convert_userWithOnlyRoleGroupPermission_shouldGrantMatchingAuthority() {
        var roleGroupConfig = new UserRoleGroupConfigDto();
        roleGroupConfig.setPermissionCodes(List.of("QCapNhatVBDen"));
        when(getUserRoleGroupConfigUseCase.execute("nhanvien01")).thenReturn(roleGroupConfig);

        Jwt jwt = jwtWithClaims(Map.of(
            "sub", "nhanvien01",
            "preferred_username", "nhanvien01"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorityValues(authorities)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_QCAPNHATVBDEN");
    }

    @Test
    void convert_shouldLookUpRoleGroupConfig_byPreferredUsername_notSub() {
        var roleGroupConfig = new UserRoleGroupConfigDto();
        roleGroupConfig.setPermissionCodes(List.of("QPhanCong"));
        when(getUserRoleGroupConfigUseCase.execute("real.username")).thenReturn(roleGroupConfig);

        Jwt jwt = jwtWithClaims(Map.of(
            "sub", "keycloak-internal-uuid",
            "preferred_username", "real.username"
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorityValues(authorities)).contains("ROLE_QPHANCONG");
    }

    private Set<String> authorityValues(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        return Jwt.withTokenValue("token-value")
            .header("alg", "none")
            .claims(c -> c.putAll(claims))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    }
}
