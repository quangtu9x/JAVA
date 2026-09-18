package com.td.infrastructure.persistence.identity;

import com.td.infrastructure.persistence.repository.BaseRepository;

import com.td.domain.sharedcore.AppRole;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppRoleJpaRepository extends BaseRepository<AppRole> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END FROM AppRole r WHERE r.code = :code AND r.id <> :id AND r.deletedOn IS NULL")
    boolean existsByCodeAndIdNotAndDeletedOnIsNull(@Param("code") String code, @Param("id") UUID id);

    @Query("SELECT r FROM AppRole r WHERE r.id = :id AND r.deletedOn IS NULL")
    Optional<AppRole> findByIdAndDeletedOnIsNull(@Param("id") UUID id);

    @Query("SELECT r FROM AppRole r WHERE r.id IN :ids AND r.deletedOn IS NULL")
    List<AppRole> findByIdInAndDeletedOnIsNull(@Param("ids") List<UUID> ids);
}
