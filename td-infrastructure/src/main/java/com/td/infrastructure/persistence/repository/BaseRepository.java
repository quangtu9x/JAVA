package com.td.infrastructure.persistence.repository;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.common.contracts.BaseEntity;
import com.td.domain.common.contracts.ISoftDelete;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity<UUID>> 
    extends JpaRepository<T, UUID>, JpaSpecificationExecutor<T>, IRepository<T> {

    @Query("SELECT e FROM #{#entityName} e WHERE e.deletedOn IS NULL")
    List<T> findAllActive();

    @Query("SELECT e FROM #{#entityName} e WHERE e.deletedOn IS NULL")
    Page<T> findAllActive(Pageable pageable);

    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.deletedOn IS NULL")
    Optional<T> findByIdActive(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE #{#entityName} e SET e.deletedOn = :deletedOn, e.deletedBy = :deletedBy WHERE e.id = :id")
    int softDeleteById(@Param("id") UUID id, @Param("deletedOn") LocalDateTime deletedOn, @Param("deletedBy") UUID deletedBy);

    default Page<T> findAll(Specification<T> spec, Pageable pageable, boolean includeDeleted) {
        if (!includeDeleted) {
            // Add soft delete filter
            Specification<T> notDeletedSpec = (root, query, cb) -> 
                cb.isNull(root.get("deletedOn"));
            spec = spec == null ? notDeletedSpec : Specification.where(spec).and(notDeletedSpec);
        }
        return findAll(spec, pageable);
    }
}