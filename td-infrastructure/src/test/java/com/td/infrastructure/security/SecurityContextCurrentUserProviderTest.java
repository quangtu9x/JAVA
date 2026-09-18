package com.td.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Bugfix 09/09/2026 (báo lỗi người dùng: số liệu CẤP CƠ QUAN của {@code SoLuongCanXuLy} - vd
 * "phaixuly_cuatochuc" - không bao giờ hiển thị dù user CÓ role hợp lệ - xem javadoc
 * {@link SecurityContextCurrentUserProvider#currentRoles()}): {@code CurrentUserContext.roles} có
 * thể ở NGUYÊN case gốc Domino (luồng Tdtoken, KHÁC luồng JWT/Keycloak đã uppercase sẵn) - mọi hằng
 * số role dùng để so khớp ({@code QlvbRoles}, {@code GetSoLuongCanXuLyUseCase}) đều viết HOA, nên
 * {@code currentRoles()} phải TỰ chuẩn hoá HOA, không phụ thuộc case nguồn.
 */
@ExtendWith(MockitoExtension.class)
class SecurityContextCurrentUserProviderTest {

    @Mock
    private CurrentUserContextResolver currentUserContextResolver;

    private SecurityContextCurrentUserProvider newProvider() {
        return new SecurityContextCurrentUserProvider(currentUserContextResolver);
    }

    @Test
    void currentRoles_withMixedCaseRolesFromTdTokenFlow_shouldNormalizeToUppercase() {
        // Mo phong dung bug that: luong Tdtoken giu nguyen case Domino goc (khong uppercase).
        CurrentUserContext context = CurrentUserContext.builder()
            .username("nguoidung01")
            .roles(Set.of("QCapNhatVBDen", "qphancong"))
            .build();
        when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.of(context));

        Set<String> roles = newProvider().currentRoles();

        assertThat(roles).containsExactlyInAnyOrder("QCAPNHATVBDEN", "QPHANCONG");
    }

    @Test
    void currentRoles_whenNoCurrentUser_shouldReturnEmptySet() {
        when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.empty());

        assertThat(newProvider().currentRoles()).isEmpty();
    }

    @Test
    void currentRoles_withBlankRoleEntry_shouldSkipIt() {
        CurrentUserContext context = CurrentUserContext.builder()
            .username("nguoidung01")
            .roles(Set.of("QPhanPhoi", " "))
            .build();
        when(currentUserContextResolver.resolveCurrentUserContext()).thenReturn(Optional.of(context));

        assertThat(newProvider().currentRoles()).containsExactly("QPHANPHOI");
    }
}
