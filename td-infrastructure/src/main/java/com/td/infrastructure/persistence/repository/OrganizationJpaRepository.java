package com.td.infrastructure.persistence.repository;

import com.td.domain.sharedcore.Organization;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationJpaRepository extends BaseRepository<Organization> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN TRUE ELSE FALSE END FROM Organization o WHERE o.code = :code AND o.id <> :id AND o.deletedOn IS NULL")
    boolean existsByCodeAndIdNotAndDeletedOnIsNull(@Param("code") String code, @Param("id") UUID id);

    @Query("SELECT o FROM Organization o WHERE o.id = :id AND o.deletedOn IS NULL")
    Optional<Organization> findByIdAndDeletedOnIsNull(@Param("id") UUID id);
}
