package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.AppPermission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface AppPermissionRepository extends IRepository<AppPermission> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedOnIsNull(String code, UUID id);

    Optional<AppPermission> findByIdAndDeletedOnIsNull(UUID id);

    Page<AppPermission> search(SearchAppPermissionsRequest request, Pageable pageable);
}
