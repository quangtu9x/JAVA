package com.td.application.duthao;

import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Yêu cầu người dùng 25/09/2026 — xem javadoc {@link DraftProcessActorFields}. */
class DraftProcessActorFieldsTest {

    @Test
    void put_storesFullNameAsCreator_andUsernameAsUserCreator() {
        AppUserRepository repo = mock(AppUserRepository.class);
        when(repo.findByUsernameAndDeletedOnIsNull("skhcn"))
            .thenReturn(Optional.of(new AppUser("kc", "skhcn", "Văn thư Sở KHCN", null, null, null, true)));
        Map<String, String> fields = new HashMap<>();

        new DraftProcessActorFields(repo).put(fields, "skhcn");

        assertThat(fields).containsEntry("creator", "Văn thư Sở KHCN").containsEntry("user_creator", "skhcn");
    }

    @Test
    void put_fallsBackToUsername_whenProfileNotFound() {
        AppUserRepository repo = mock(AppUserRepository.class);
        when(repo.findByUsernameAndDeletedOnIsNull("system")).thenReturn(Optional.empty());
        Map<String, Object> fields = new HashMap<>();

        new DraftProcessActorFields(repo).put(fields, "system");

        assertThat(fields).containsEntry("creator", "system").containsEntry("user_creator", "system");
    }
}
