package com.td.infrastructure.security;

import com.td.application.common.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    private final CurrentUserContextResolver currentUserContextResolver;

    @Override
    public Optional<String> currentUsername() {
        return currentUserContextResolver.resolveCurrentUserContext()
            .map(CurrentUserContext::getUsername);
    }

    /**
     * Bugfix 09/09/2026 (báo lỗi người dùng: "phaixuly_cuatochuc" - và mọi field CẤP CƠ QUAN khác -
     * không bao giờ hiển thị số liệu dù user CÓ role hợp lệ): {@code CurrentUserContext.roles} KHÔNG
     * đồng nhất chữ hoa/thường giữa 2 luồng xác thực — luồng JWT/Keycloak trích từ
     * {@code authentication.getAuthorities()} (ĐÃ uppercase bởi {@code KeycloakJwtConverter}), còn
     * luồng {@code Tdtoken} đọc THẲNG {@code AuthenticatedUser.getRoles()} (giữ NGUYÊN VĂN chữ hoa/
     * thường gốc từ field {@code roles} hồ sơ Domino — xem {@code CurrentUserContextResolver#buildTdTokenContext},
     * {@code TdTokenService#parseRoles} không hề chuẩn hoá case) — trong khi
     * {@code authentication.getAuthorities()} của CHÍNH luồng Tdtoken lại CÓ uppercase (xem
     * {@code TdTokenAuthenticationFilter} dòng ~57, dùng cho {@code @PreAuthorize}) nên 2 nguồn
     * "roles" của cùng 1 request lệch nhau. Mọi hằng số role dùng để so khớp ({@code QlvbRoles}, và
     * mọi use case gọi {@link #currentRoles()}) đều viết HOA — chuẩn hoá HOA ở nguồn DUY NHẤT này để
     * đảm bảo hợp đồng nhất quán cho mọi consumer hiện tại/tương lai, thay vì bắt từng use case tự
     * chuẩn hoá.
     */
    @Override
    public Set<String> currentRoles() {
        return currentUserContextResolver.resolveCurrentUserContext()
            .map(CurrentUserContext::getRoles)
            .map(roles -> roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet()))
            .orElse(Set.of());
    }
}
