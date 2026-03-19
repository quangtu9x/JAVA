package com.td.infrastructure.persistence.repository;

import com.td.domain.sharedcore.AppUser;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppUserJpaRepository extends BaseRepository<AppUser> {

    boolean existsByUsernameAndDeletedOnIsNull(String username);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM AppUser u WHERE u.username = :username AND u.id <> :id AND u.deletedOn IS NULL")
    boolean existsByUsernameAndIdNotAndDeletedOnIsNull(@Param("username") String username, @Param("id") UUID id);

    boolean existsByKeycloakSubjectAndDeletedOnIsNull(String keycloakSubject);

    @Query("SELECT u FROM AppUser u WHERE u.id = :id AND u.deletedOn IS NULL")
    Optional<AppUser> findByIdAndDeletedOnIsNull(@Param("id") UUID id);

    @Query("SELECT u FROM AppUser u WHERE u.keycloakSubject = :keycloakSubject AND u.deletedOn IS NULL")
    Optional<AppUser> findByKeycloakSubjectAndDeletedOnIsNull(@Param("keycloakSubject") String keycloakSubject);
}
