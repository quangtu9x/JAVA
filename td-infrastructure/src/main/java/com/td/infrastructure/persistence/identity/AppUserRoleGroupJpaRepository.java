package com.td.infrastructure.persistence.identity;

import com.td.infrastructure.persistence.repository.BaseRepository;

import com.td.domain.sharedcore.AppUserRoleGroup;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AppUserRoleGroupJpaRepository extends BaseRepository<AppUserRoleGroup> {

    @Query("SELECT l FROM AppUserRoleGroup l WHERE l.appUserId = :appUserId AND l.deletedOn IS NULL")
    List<AppUserRoleGroup> findByAppUserIdAndDeletedOnIsNull(@Param("appUserId") UUID appUserId);

    @Query("SELECT l FROM AppUserRoleGroup l WHERE l.appUserId IN :appUserIds AND l.deletedOn IS NULL")
    List<AppUserRoleGroup> findByAppUserIdInAndDeletedOnIsNull(@Param("appUserIds") List<UUID> appUserIds);
}
