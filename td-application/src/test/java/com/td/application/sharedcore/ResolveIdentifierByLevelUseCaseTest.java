package com.td.application.sharedcore;

import com.td.application.common.security.CurrentUserProvider;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Bugfix 21/08/2026: {@code ResolveIdentifierByLevelUseCase} — tương đương
 * {@code getIdentifier_by_level()} (FE, {@code utils/global.jsx}) server-side. Thuật toán: tách
 * {@code identifiers} (LÁ → GỐC) thành mảng, duyệt TỪ CUỐI (gốc) VỀ ĐẦU (lá), đếm từ 1, trả về phần
 * tử có số thứ tự (tính từ gốc) == {@code level}.
 */
@ExtendWith(MockitoExtension.class)
class ResolveIdentifierByLevelUseCaseTest {

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private GetUserOrganizationProfileUseCase getUserOrganizationProfileUseCase;

    private ResolveIdentifierByLevelUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResolveIdentifierByLevelUseCase(
            currentUserProvider, appUserRepository, getUserOrganizationProfileUseCase);
    }

    // Vi du chinh trich tu UserOrganizationProfileDto.getIdentifiers() javadoc: chuoi la-goc thuc
    // te "G10.01.01;G10.06;;G10;" (4 cap: phong -> van phong bo -> "" (rong, 1 cap thieu du lieu) ->
    // tinh). level=1 (dem tu goc) phai tra ve "G10" (phan tu cuoi cung).
    @Test
    void resolveForUser_level1_shouldReturnRootMostIdentifier() {
        AppUser user = newUser("skhcn");
        stubProfile(user, "G10.01.01;G10.06;;G10;", "1");

        Optional<String> result = useCase.resolveForUser(user);

        assertThat(result).contains("G10");
    }

    @Test
    void resolveForUser_level4_shouldReturnLeafMostIdentifier() {
        AppUser user = newUser("skhcn");
        stubProfile(user, "H05.01;H05;;H;", "4");

        Optional<String> result = useCase.resolveForUser(user);

        assertThat(result).contains("H05.01");
    }

    @Test
    void resolveForUser_level2_shouldReturnSecondFromRoot() {
        AppUser user = newUser("skhcn");
        stubProfile(user, "H05.01;H05;H;", "2");

        Optional<String> result = useCase.resolveForUser(user);

        assertThat(result).contains("H05");
    }

    @Test
    void resolveForUser_blankIdentifiers_shouldReturnEmpty() {
        AppUser user = newUser("skhcn");
        stubProfile(user, "", "1");

        assertThat(useCase.resolveForUser(user)).isEmpty();
    }

    @Test
    void resolveForUser_nonNumericLevel_shouldReturnEmpty() {
        AppUser user = newUser("skhcn");
        stubProfile(user, "H05.01;H05;H;", "");

        assertThat(useCase.resolveForUser(user)).isEmpty();
    }

    @Test
    void resolveForUser_levelBeyondChainLength_shouldReturnEmpty() {
        AppUser user = newUser("skhcn");
        stubProfile(user, "H05.01;H05;H;", "10");

        assertThat(useCase.resolveForUser(user)).isEmpty();
    }

    @Test
    void resolveForUser_trailingSemicolon_shouldBeStrippedBeforeSplitting() {
        AppUser user = newUser("skhcn");
        stubProfile(user, "H05.01;H05;H;", "1");

        Optional<String> result = useCase.resolveForUser(user);

        assertThat(result).contains("H");
    }

    @Test
    void resolveForCurrentUser_whenUsernameMissing_shouldReturnEmpty() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        assertThat(useCase.resolveForCurrentUser()).isEmpty();
    }

    @Test
    void resolveForCurrentUser_whenAppUserNotFound_shouldReturnEmpty() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("skhcn"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn")).thenReturn(Optional.empty());

        assertThat(useCase.resolveForCurrentUser()).isEmpty();
    }

    @Test
    void resolveForCurrentUser_shouldDelegateToResolveForUser() {
        AppUser user = newUser("skhcn");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("skhcn"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("skhcn")).thenReturn(Optional.of(user));
        stubProfile(user, "H05.01;H05;H;", "1");

        assertThat(useCase.resolveForCurrentUser()).contains("H");
    }

    private void stubProfile(AppUser user, String identifiers, String level) {
        var dto = new UserOrganizationProfileDto();
        dto.setIdentifiers(identifiers);
        dto.setLevel(level);
        when(getUserOrganizationProfileUseCase.buildProfileForUser(user)).thenReturn(dto);
    }

    private AppUser newUser(String username) {
        return new AppUser(UUID.randomUUID().toString(), username, "Ten day du", username + "@example.com", null, null, true);
    }
}
