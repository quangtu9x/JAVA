package com.td.infrastructure.security;

import com.td.application.sharedcore.AppUserRepository;
import com.td.application.sharedcore.GetUserRoleGroupConfigUseCase;
import com.td.application.sharedcore.UserRoleGroupConfigDto;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 28/10/2026 (rà soát bảo mật): khoá lại điều kiện "quản trị người dùng" — xem javadoc {@link UserAdministrationAccess}. */
@ExtendWith(MockitoExtension.class)
class UserAdministrationAccessTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private GetUserRoleGroupConfigUseCase getUserRoleGroupConfigUseCase;

    private UserAdministrationAccess newAccess() {
        return new UserAdministrationAccess(
            new AccountProvisioningChecker(appUserRepository), getUserRoleGroupConfigUseCase, appUserRepository);
    }

    private static Authentication auth(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, "n/a",
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private void givenMenu(String username, List<String> menuQlvb) {
        UserRoleGroupConfigDto config = new UserRoleGroupConfigDto();
        config.setMenuFields(Map.of("menu_qlvb", menuQlvb));
        when(getUserRoleGroupConfigUseCase.execute(username)).thenReturn(config);
    }

    @Test
    void regularUser_cannotManageUsers() {
        givenMenu("nhanvien01", List.of("Sổ văn bản"));
        assertThat(newAccess().canManageUsers(auth("nhanvien01", "ROLE_USER"))).isFalse();
    }

    @Test
    void adminRoleOrAdminAccount_canManageUsers() {
        assertThat(newAccess().canManageUsers(auth("x", "ROLE_USER", "ROLE_ADMIN"))).isTrue();
        assertThat(newAccess().canManageUsers(auth("admin", "ROLE_USER"))).isTrue();
    }

    @Test
    void quanTriDonViPermission_canManageUsers() {
        assertThat(newAccess().canManageUsers(auth("qtdv", "ROLE_USER", "ROLE_CVQUANTRIDV"))).isTrue();
    }

    @Test
    void roleGroupWithUserMenu_canManageUsers() {
        givenMenu("vanthu", List.of("Sổ văn bản", "Người dùng"));
        assertThat(newAccess().canManageUsers(auth("vanthu", "ROLE_USER"))).isTrue();
    }

    @Test
    void selfCanUploadOwnSignature_butNotOthers() {
        UUID ownId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        AppUser own = mock(AppUser.class);
        when(own.getUsername()).thenReturn("NhanVien01");
        AppUser other = mock(AppUser.class);
        when(other.getUsername()).thenReturn("giamdoc");
        when(appUserRepository.findByIdAndDeletedOnIsNull(ownId)).thenReturn(Optional.of(own));
        when(appUserRepository.findByIdAndDeletedOnIsNull(otherId)).thenReturn(Optional.of(other));
        givenMenu("nhanvien01", List.of());

        Authentication nhanvien = auth("nhanvien01", "ROLE_USER");
        assertThat(newAccess().isSelfOrCanManageUsers(nhanvien, ownId)).isTrue();
        assertThat(newAccess().isSelfOrCanManageUsers(nhanvien, otherId)).isFalse();
    }
}
