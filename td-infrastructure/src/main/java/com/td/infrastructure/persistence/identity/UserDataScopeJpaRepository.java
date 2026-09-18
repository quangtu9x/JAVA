package com.td.infrastructure.persistence.identity;

import com.td.infrastructure.persistence.repository.BaseRepository;

import com.td.domain.sharedcore.UserDataScope;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserDataScopeJpaRepository extends BaseRepository<UserDataScope> {

    @Query("SELECT s FROM UserDataScope s WHERE s.id = :id AND s.deletedOn IS NULL")
    Optional<UserDataScope> findByIdAndDeletedOnIsNull(@Param("id") UUID id);
}
