package com.td.application.sharedcore;

import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Port TDSwitchUser (docs/analysis/19-checklist-port-tdswitchuser-domino-sang-java.md, 08/09/2026).
@ExtendWith(MockitoExtension.class)
class SwitchUserUseCaseTest {

    @Mock
    private AppUserRepository appUserRepository;

    private SwitchUserUseCase newUseCase() {
        return new SwitchUserUseCase(appUserRepository);
    }

    @Test
    void execute_blankUsername_returnsFailure() {
        var result = newUseCase().execute("   ");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_unknownUsername_returnsFailure() {
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("ghost")).thenReturn(Optional.empty());

        var result = newUseCase().execute("ghost");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_lockedTarget_returnsFailure() {
        var locked = new AppUser("kc-locked", "khoa01", "Khoa 1", null, null, null, false);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("khoa01")).thenReturn(Optional.of(locked));

        var result = newUseCase().execute("khoa01");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_activeTarget_returnsSuccess() {
        var active = new AppUser("kc-active", "nhanvien03", "Nhan vien 3", null, null, null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nhanvien03")).thenReturn(Optional.of(active));

        var result = newUseCase().execute("nhanvien03");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getUsername()).isEqualTo("nhanvien03");
    }
}
