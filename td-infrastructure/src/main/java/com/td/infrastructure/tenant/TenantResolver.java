package com.td.infrastructure.tenant;

import com.td.application.tenant.ResolveUserTenantUseCase;
import com.td.infrastructure.security.AuthenticatedUser;
import com.td.infrastructure.security.TdTokenAuthenticationToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Suy ra tenant hiện tại (BL-303) từ Authentication đã có sẵn ở SecurityContext, cho cả 2 luồng
 * xác thực (BL-004): Keycloak ({@link JwtAuthenticationToken}) và Tdtoken
 * ({@link TdTokenAuthenticationToken}).
 *
 * <p>Claim ưu tiên:
 * <ul>
 *   <li>{@code tenant_id} — claim chuẩn cho token mới (Keycloak), theo đúng thiết kế BL-303.</li>
 *   <li>{@code identifier} — claim tương đương ở Tdtoken cũ (mã đơn vị gốc/tỉnh, vd "H05" —
 *       xem ví dụ request trong {@code LegacyDanhMucController}), dùng khi {@code tenant_id}
 *       không có (token cũ chưa từng biết khái niệm tenant).</li>
 * </ul>
 * Nếu claim thiếu (không hợp lệ/không có trong registry), tra cứu tiếp qua
 * {@link ResolveUserTenantUseCase} (bridge theo username — bắt buộc từ BL-118, xem javadoc lớp đó:
 * Keycloak JWT thực tế KHÔNG BAO GIỜ mang claim {@code tenant_id} vì chưa cấu hình protocol mapper,
 * nên nhánh claim ở trên gần như luôn rỗng cho MỌI user). Chỉ khi CẢ 2 đều không xác định được mới
 * fallback về {@code defaultTenantId} — không chặn request (vd tài khoản Keycloak vừa tạo, chưa từng
 * có hồ sơ AppUser ở bất kỳ tenant nào).
 *
 * <p>BL-117: PHÂN BIỆT rõ với trường hợp tenant XÁC ĐỊNH ĐƯỢC nhưng đã bị {@code deactivate} — case
 * này KHÔNG được fallback (sẽ âm thầm phục vụ dữ liệu tenant mặc định cho user của tenant đã khoá,
 * rò rỉ dữ liệu chéo tenant) mà phải TỪ CHỐI hẳn qua {@link TenantNotActiveException} (bắt ở
 * {@link TenantFilter}, trả 403). Xem {@link TenantActiveRegistry}.
 *
 * <p><b>BL-118 (bugfix NGHIÊM TRỌNG, 20/08/2026)</b>: trước đây KHÔNG có nhánh
 * {@link ResolveUserTenantUseCase} này — vì Keycloak JWT không mang claim {@code tenant_id}, MỌI
 * request qua Keycloak (đường xác thực THẬT SỰ cho {@code /api/v1/*}, xem
 * {@code KeycloakJwtConverter}) LUÔN rơi vào nhánh fallback {@code defaultTenantId}, bất kể user
 * thuộc đơn vị/tenant nào — hậu quả: "Thêm mới văn bản" báo thành công nhưng lưu vào SAI database
 * (database mặc định thay vì database đúng của đơn vị user đó), rò rỉ dữ liệu chéo tenant NGAY CẢ
 * cho user của tenant hợp lệ/đang active.
 */
@Component
@RequiredArgsConstructor
public class TenantResolver {

    private static final String TENANT_CLAIM = "tenant_id";
    private static final String LEGACY_TENANT_CLAIM = "identifier";

    private final TenantProperties tenantProperties;
    private final TenantActiveRegistry tenantActiveRegistry;
    private final ResolveUserTenantUseCase resolveUserTenantUseCase;

    public String resolve(Authentication authentication) {
        String tenantId = extractTenantId(authentication);
        if (tenantId == null || tenantId.isBlank() || !tenantActiveRegistry.isKnown(tenantId)) {
            tenantId = resolveViaUserTenantBridge(authentication);
        }
        if (tenantId == null || tenantId.isBlank() || !tenantActiveRegistry.isKnown(tenantId)) {
            return tenantProperties.getDefaultTenantId();
        }
        if (!tenantActiveRegistry.isActive(tenantId)) {
            throw new TenantNotActiveException(tenantId);
        }
        return tenantId;
    }

    /** Bridge BL-118 — xem javadoc lớp. Chỉ dùng khi claim tenant_id/identifier không xác định được. */
    private String resolveViaUserTenantBridge(Authentication authentication) {
        String username = extractUsername(authentication);
        if (username == null) {
            return null;
        }
        return resolveUserTenantUseCase.execute(username).orElse(null);
    }

    private String extractUsername(Authentication authentication) {
        if (authentication instanceof TdTokenAuthenticationToken tdTokenAuthentication) {
            if (tdTokenAuthentication.getPrincipal() instanceof AuthenticatedUser user) {
                return user.getUsername();
            }
            return null;
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
            String subject = jwt.getSubject();
            return (subject == null || subject.isBlank()) ? null : subject;
        }

        return null;
    }

    private String extractTenantId(Authentication authentication) {
        if (authentication instanceof TdTokenAuthenticationToken tdTokenAuthentication) {
            Object principal = tdTokenAuthentication.getPrincipal();
            if (principal instanceof AuthenticatedUser user) {
                Map<String, Object> claims = user.getClaims();
                String tenantId = claimAsString(claims, TENANT_CLAIM);
                return tenantId != null ? tenantId : claimAsString(claims, LEGACY_TENANT_CLAIM);
            }
            return null;
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            return jwt.getClaimAsString(TENANT_CLAIM);
        }

        return null;
    }

    private String claimAsString(Map<String, Object> claims, String claimName) {
        if (claims == null) {
            return null;
        }
        Object value = claims.get(claimName);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
