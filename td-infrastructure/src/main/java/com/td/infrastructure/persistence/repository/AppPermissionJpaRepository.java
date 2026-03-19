package com.td.infrastructure.persistence.repository;

import com.td.domain.sharedcore.AppPermission;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppPermissionJpaRepository extends BaseRepository<AppPermission> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM AppPermission p WHERE p.code = :code AND p.id <> :id AND p.deletedOn IS NULL")
    boolean existsByCodeAndIdNotAndDeletedOnIsNull(@Param("code") String code, @Param("id") UUID id);

    @Query("SELECT p FROM AppPermission p WHERE p.id = :id AND p.deletedOn IS NULL")
    Optional<AppPermission> findByIdAndDeletedOnIsNull(@Param("id") UUID id);
}
