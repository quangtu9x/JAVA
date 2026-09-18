package com.td.infrastructure.persistence.identity;

import com.td.infrastructure.persistence.repository.BaseRepository;

import com.td.domain.sharedcore.RoleGroup;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoleGroupJpaRepository extends BaseRepository<RoleGroup> {

    boolean existsByNameAndDeletedOnIsNull(String name);

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN TRUE ELSE FALSE END FROM RoleGroup g WHERE g.name = :name AND g.id <> :id AND g.deletedOn IS NULL")
    boolean existsByNameAndIdNotAndDeletedOnIsNull(@Param("name") String name, @Param("id") UUID id);

    @Query("SELECT g FROM RoleGroup g WHERE g.id = :id AND g.deletedOn IS NULL")
    Optional<RoleGroup> findByIdAndDeletedOnIsNull(@Param("id") UUID id);
}
