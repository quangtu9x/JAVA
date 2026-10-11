package com.td.infrastructure.persistence.identity;

import com.td.application.sharedcore.UserDelegationRepository;
import com.td.domain.sharedcore.UserDelegation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresUserDelegationRepository implements UserDelegationRepository {

    private final UserDelegationJpaRepository jpa;

    @Override
    public <S extends UserDelegation> S save(S entity) {
        return jpa.save(entity);
    }

    @Override
    public Optional<UserDelegation> findByIdAndDeletedOnIsNull(UUID id) {
        return jpa.findByIdAndDeletedOnIsNull(id);
    }

    @Override
    public List<UserDelegation> findByDelegatorUserId(UUID delegatorUserId) {
        return jpa.findByDelegatorUserId(delegatorUserId);
    }

    @Override
    public List<UserDelegation> findActiveOn(LocalDate day) {
        return jpa.findActiveOn(day);
    }

    @Override
    public List<UserDelegation> findActiveByDelegatorUsernamesOn(Collection<String> usernames, LocalDate day) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }
        List<String> lower = usernames.stream().filter(Objects::nonNull)
            .map(u -> u.trim().toLowerCase(Locale.ROOT)).filter(u -> !u.isEmpty()).distinct().toList();
        return lower.isEmpty() ? List.of() : jpa.findActiveByDelegatorUsernamesOn(lower, day);
    }
}
