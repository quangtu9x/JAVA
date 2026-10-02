package com.td.infrastructure.security;

import com.td.application.sharedcore.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountProvisioningCheckerTest {

    @Mock
    private AppUserRepository appUserRepository;

    private AccountProvisioningChecker newChecker() {
        return new AccountProvisioningChecker(appUserRepository);
    }

    @Test
    void isSuperAdmin_withUsernameAdmin_shouldReturnTrue() {
        assertThat(newChecker().isSuperAdmin("admin", Set.of())).isTrue();
        assertThat(newChecker().isSuperAdmin("ADMIN", Set.of())).isTrue();
    }

    @Test
    void isSuperAdmin_withAdminRole_shouldReturnTrue() {
        assertThat(newChecker().isSuperAdmin("nhanvien01", Set.of("ADMIN"))).isTrue();
    }

    @Test
    void isSuperAdmin_withRegularUser_shouldReturnFalse() {
        assertThat(newChecker().isSuperAdmin("nhanvien01", Set.of("USER"))).isFalse();
    }

    @Test
    void isProvisioned_forSuperAdmin_shouldReturnTrueWithoutQueryingRepository() {
        boolean result = newChecker().isProvisioned("admin", Set.of());

        assertThat(result).isTrue();
        verify(appUserRepository, never()).existsByUsernameAndDeletedOnIsNull(anyString());
    }

    @Test
    void isProvisioned_forRegularUserWithAppUser_shouldReturnTrue() {
        when(appUserRepository.existsByUsernameAndDeletedOnIsNull("nhanvien01")).thenReturn(true);

        assertThat(newChecker().isProvisioned("nhanvien01", Set.of("USER"))).isTrue();
    }

    @Test
    void isProvisioned_forRegularUserWithoutAppUser_shouldReturnFalse() {
        when(appUserRepository.existsByUsernameAndDeletedOnIsNull("chuakhaibao01")).thenReturn(false);

        assertThat(newChecker().isProvisioned("chuakhaibao01", Set.of("USER"))).isFalse();
    }

    @Test
    void isProvisioned_withBlankUsername_shouldReturnFalse() {
        assertThat(newChecker().isProvisioned("", Set.of())).isFalse();
        assertThat(newChecker().isProvisioned(null, Set.of())).isFalse();
    }
}
