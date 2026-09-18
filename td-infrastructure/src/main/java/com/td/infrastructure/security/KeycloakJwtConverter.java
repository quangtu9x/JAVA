package com.td.infrastructure.security;

import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converter to extract roles from Keycloak JWT and map them to Spring Security authorities
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakJwtConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";

    /** Quyền sàn tự động cấp cho MỌI request xác thực Keycloak thành công — xem javadoc lớp. */
    private static final String BASELINE_AUTHORITY = "ROLE_USER";

    private final GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // Extract realm roles
        Collection<String> realmRoles = extractRealmRoles(jwt);
        authorities.addAll(mapRolesToAuthorities(realmRoles));

        // Extract client roles (resource access)
        Collection<String> clientRoles = extractClientRoles(jwt);
        authorities.addAll(mapRolesToAuthorities(clientRoles));

        // BL-118 (bugfix 19/08/2026): AppUser/CreateAppUserUseCase KHÔNG provision tài khoản Keycloak
        // — role Keycloak (USER/ADMIN/...) chỉ tồn tại nếu ai đó gán TAY qua Admin Console. Phần lớn
        // tài khoản staff thật (vd "skhcn") không có role Keycloak nào cả, khiến MỌI endpoint
        // "danh mục quản trị" (14 controller, @PreAuthorize hasAnyRole('USER',...)) trả 403 dù JWT
        // hợp lệ và quyền nghiệp vụ thật (Nhóm vai trò/AppPermission) đã đủ. Cấp thêm ROLE_USER cho
        // MỌI JWT xác thực thành công (không đổi các role cao hơn/thật — ADMIN/PRODUCT_MANAGER/
        // BRAND_MANAGER vẫn phải gán tay đúng nghĩa "quyền nâng cao") — khớp quy ước
        // "USER = quyền sàn cho người đã đăng nhập" mà realm-export.json seed data ngầm định.
        authorities.add(new SimpleGrantedAuthority(BASELINE_AUTHORITY));

        // Bổ sung 20/08/2026 (bugfix NGHIÊM TRỌNG, tiếp BL-118): FE gửi CẢ HAI header `Authorization`
        // (Keycloak) lẫn `Tdtoken` cho mọi request (`AuthHelpers.ts#setupAxios`) — nhưng
        // `TdTokenAuthenticationFilter` LUÔN bỏ qua Tdtoken khi có `Authorization`
        // (`StringUtils.hasText(request.getHeader(HttpHeaders.AUTHORIZATION))` → true), nên đường đi
        // xác thực THẬT SỰ của MỌI request `/api/v1/*` là qua converter NÀY, không phải qua Tdtoken.
        // Việc nhúng quyền Nhóm vai trò vào claim "roles" của Tdtoken (đã sửa ở QLVBCompatAuthController)
        // do đó KHÔNG BAO GIỜ có tác dụng trong thực tế — token đó gần như không được dùng để phân
        // quyền cho `/api/v1/*`. Sửa tận gốc TẠI ĐÂY: tra cứu trực tiếp quyền Nhóm vai trò
        // (RoleGroup/AppPermission, BL-111) theo username thật trong JWT Keycloak, cấp thêm authority
        // tương ứng — không phụ thuộc Keycloak phải biết về các quyền nghiệp vụ QLVB (AppUser vốn
        // KHÔNG được provision trong Keycloak, xem bugfix 19/08/2026 ở trên).
        // Bổ sung 20/08/2026 (bugfix NGHIÊM TRỌNG, RÚT LẠI cơ chế tạm đổi TenantContext): AppUser/
        // RoleGroup (toàn bộ com.td.domain.sharedcore) là "danh mục dùng chung" — sống CỐ ĐỊNH ở 1
        // database duy nhất (identityEntityManagerFactory, xem MultiPersistenceUnitJpaConfig), độc
        // lập hoàn toàn với TenantContext của request đang xử lý — nên tra cứu ở đây KHÔNG còn cần
        // "đoán"/tạm đổi tenant trước khi gọi nữa, luôn tra đúng NGAY LẬP TỨC.
        String username = resolveUsername(jwt);
        if (username != null) {
            var roleGroupConfig = getUserRoleGroupConfigUseCase.execute(username);
            authorities.addAll(mapRolesToAuthorities(roleGroupConfig.getPermissionCodes()));
        }

        log.debug("Extracted authorities from JWT: {}", authorities);
        return authorities;
    }

    /** Cùng quy tắc lấy username với {@code QLVBCompatAuthController#resolvePrincipal} (ưu tiên preferred_username). */
    private String resolveUsername(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        String subject = jwt.getSubject();
        return (subject == null || subject.isBlank()) ? null : subject;
    }

    /**
     * Extract roles from realm_access claim
     */
    private Collection<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return Collections.emptyList();
        }

        Object roles = realmAccess.get(ROLES_CLAIM);
        if (roles instanceof Collection<?>) {
            return ((Collection<?>) roles).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }

    /**
     * Extract roles from resource_access claim for all clients
     */
    private Collection<String> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM);
        if (resourceAccess == null) {
            return Collections.emptyList();
        }

        return resourceAccess.values().stream()
                .filter(Map.class::isInstance)
                .map(client -> (Map<String, Object>) client)
                .map(client -> client.get(ROLES_CLAIM))
                .filter(Collection.class::isInstance)
                .flatMap(roles -> ((Collection<?>) roles).stream())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());
    }

    /**
     * Map role names to Spring Security authorities with ROLE_ prefix
     */
    private Collection<GrantedAuthority> mapRolesToAuthorities(Collection<String> roles) {
        return roles.stream()
                .map(role -> {
                    // Add ROLE_ prefix if not already present
                    String authority = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role.toUpperCase();
                    return new SimpleGrantedAuthority(authority);
                })
                .collect(Collectors.toSet());
    }
}