package com.td.infrastructure.persistence.repository;

import com.td.domain.recordsstorage.RecordsStorageCatalog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RecordsStorageCatalogJpaRepository extends BaseRepository<RecordsStorageCatalog> {

    @Query("SELECT r FROM RecordsStorageCatalog r WHERE r.id = :id AND r.deletedOn IS NULL")
    Optional<RecordsStorageCatalog> findByIdAndDeletedOnIsNull(@Param("id") UUID id);
}
