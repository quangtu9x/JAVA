package com.td.infrastructure.persistence.repository;

import com.td.domain.categories.Category;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CategoryJpaRepository extends BaseRepository<Category> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM Category c WHERE c.code = :code AND c.id <> :id AND c.deletedOn IS NULL")
    boolean existsByCodeAndIdNotAndDeletedOnIsNull(@Param("code") String code, @Param("id") UUID id);

    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.deletedOn IS NULL")
    Optional<Category> findByIdAndDeletedOnIsNull(@Param("id") UUID id);
}
