package com.td.infrastructure.persistence.identity;

import com.td.domain.sharedcore.UserDelegation;
import com.td.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDelegationJpaRepository extends BaseRepository<UserDelegation> {

    @Query("SELECT d FROM UserDelegation d WHERE d.id = :id AND d.deletedOn IS NULL")
    Optional<UserDelegation> findByIdAndDeletedOnIsNull(@Param("id") UUID id);

    @Query("SELECT d FROM UserDelegation d WHERE d.delegatorUserId = :userId AND d.deletedOn IS NULL "
        + "ORDER BY d.createdOn DESC")
    List<UserDelegation> findByDelegatorUserId(@Param("userId") UUID userId);

    @Query("SELECT d FROM UserDelegation d WHERE d.status = 'active' AND d.deletedOn IS NULL "
        + "AND d.startDate <= :day AND d.endDate >= :day ORDER BY d.createdOn DESC")
    List<UserDelegation> findActiveOn(@Param("day") LocalDate day);

    @Query("SELECT d FROM UserDelegation d WHERE lower(d.delegatorUsername) IN :usernames "
        + "AND d.status = 'active' AND d.deletedOn IS NULL AND d.startDate <= :day AND d.endDate >= :day "
        + "ORDER BY d.createdOn DESC")
    List<UserDelegation> findActiveByDelegatorUsernamesOn(
        @Param("usernames") Collection<String> lowerUsernames, @Param("day") LocalDate day);
}
