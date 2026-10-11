package com.td.application.sharedcore;

import com.td.application.common.security.CurrentUserProvider;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.UserDelegation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 09/10/2026 — màn "Ủy quyền" (bảng user_delegations). Stub tay, không Mockito. */
class ManageMyDelegationUseCaseTest {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AppUser me = new AppUser("kc-tp", "truongphong", "Trưởng phòng", null, null, null, true);
    private final AppUser pp = new AppUser("kc-pp", "phophong", "Phó phòng", null, null, null, true);
    private final AppUser locked = new AppUser("kc-k", "khoa", "Bị khoá", null, null, null, false);
    private final List<UserDelegation> store = new ArrayList<>();

    private final ManageMyDelegationUseCase useCase = new ManageMyDelegationUseCase(
        UserDelegationTestSupport.inMemoryRepository(store),
        (AppUserRepository) Proxy.newProxyInstance(AppUserRepository.class.getClassLoader(), new Class<?>[]{AppUserRepository.class},
            (proxy, method, args) -> method.getName().equals("findByUsernameAndDeletedOnIsNull")
                ? List.of(me, pp, locked).stream().filter(u -> u.getUsername().equals(args[0])).findFirst()
                : null),
        new CurrentUserProvider() {
            @Override
            public Optional<String> currentUsername() {
                return Optional.of("truongphong");
            }

            @Override
            public java.util.Set<String> currentRoles() {
                return java.util.Set.of();
            }
        });

    private SaveMyDelegationRequest request(String delegate, int fromOffset, int toOffset) {
        var today = UserDelegationResolver.today();
        var r = new SaveMyDelegationRequest();
        r.setDelegateUsername(delegate);
        r.setStartDate(today.plusDays(fromOffset).format(D));
        r.setEndDate(today.plusDays(toOffset).format(D));
        r.setScopes(List.of("vbden", "duthao", "khonghople"));
        r.setSubject("Đi công tác");
        return r;
    }

    @Test
    void saveReplacesPreviousDelegationAndResolverSeesNewOne() {
        assertThat(useCase.saveMine(request("phophong", 0, 3)).isSuccess()).isTrue();
        assertThat(useCase.saveMine(request("phophong", -1, 5)).isSuccess()).isTrue();

        assertThat(store).hasSize(2);
        assertThat(store.stream().filter(d -> UserDelegation.STATUS_ACTIVE.equals(d.getStatus()))).hasSize(1);
        assertThat(store.get(0).getScopes()).isEqualTo("vbden;duthao");

        var resolver = UserDelegationTestSupport.resolverWith(store);
        assertThat(resolver.isActiveDelegation("truongphong", "phophong", "vbden")).isTrue();
        Map<String, Object> current = useCase.getMine().getData();
        assertThat(((Map<?, ?>) current.get("current")).get("username")).isEqualTo("phophong");
        assertThat((List<?>) current.get("history")).hasSize(2);
    }

    @Test
    void rejectsSelfLockedAndPastEndDate() {
        assertThat(useCase.saveMine(request("truongphong", 0, 1)).getError()).contains("chính mình");
        assertThat(useCase.saveMine(request("khoa", 0, 1)).getError()).contains("khoá");
        assertThat(useCase.saveMine(request("phophong", -5, -1)).getError()).contains("đã qua");
        assertThat(store).isEmpty();
    }

    @Test
    void revokeStopsDelegation() {
        useCase.saveMine(request("phophong", 0, 3));
        var id = store.get(0).getId();

        assertThat(useCase.revokeMine(id).isSuccess()).isTrue();

        assertThat(UserDelegationTestSupport.resolverWith(store).activeFor("truongphong")).isEmpty();
        assertThat(useCase.getMine().getData().get("current")).isNull();
    }
}
