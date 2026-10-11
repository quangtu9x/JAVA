package com.td.application.sharedcore;

import com.td.domain.sharedcore.UserDelegation;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 09/10/2026 — stub tay (không Mockito) cho test cần {@link UserDelegationResolver}/{@link UserDelegationRepository}. */
public final class UserDelegationTestSupport {

    private UserDelegationTestSupport() {
    }

    /** Bộ giải không có ủy quyền nào. */
    public static UserDelegationResolver emptyResolver() {
        return new UserDelegationResolver(inMemoryRepository(new ArrayList<>()));
    }

    public static UserDelegationResolver resolverWith(List<UserDelegation> delegations) {
        return new UserDelegationResolver(inMemoryRepository(delegations));
    }

    /** Repository trong bộ nhớ trên {@code store} (danh sách có thể thay đổi). */
    public static UserDelegationRepository inMemoryRepository(List<UserDelegation> store) {
        return (UserDelegationRepository) Proxy.newProxyInstance(
            UserDelegationRepository.class.getClassLoader(), new Class<?>[]{UserDelegationRepository.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "save" -> {
                    UserDelegation d = (UserDelegation) args[0];
                    if (!store.contains(d)) {
                        store.add(0, d);
                    }
                    yield d;
                }
                case "findByIdAndDeletedOnIsNull" -> store.stream().filter(d -> d.getId().equals(args[0])).findFirst();
                case "findByDelegatorUserId" -> store.stream().filter(d -> d.getDelegatorUserId().equals(args[0])).toList();
                case "findActiveOn" -> store.stream().filter(d -> d.isActiveOn((java.time.LocalDate) args[0])).toList();
                case "findActiveByDelegatorUsernamesOn" -> store.stream()
                    .filter(d -> ((java.util.Collection<?>) args[0]).stream()
                        .anyMatch(u -> String.valueOf(u).equalsIgnoreCase(d.getDelegatorUsername())))
                    .filter(d -> d.isActiveOn((java.time.LocalDate) args[1]))
                    .toList();
                case "toString" -> "InMemoryUserDelegationRepository";
                default -> Optional.empty();
            });
    }
}
