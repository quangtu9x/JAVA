package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.AppRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface AppRoleRepository extends IRepository<AppRole> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedOnIsNull(String code, UUID id);

    Optional<AppRole> findByIdAndDeletedOnIsNull(UUID id);

    Page<AppRole> search(SearchAppRolesRequest request, Pageable pageable);
}
