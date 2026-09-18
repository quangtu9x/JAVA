package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.AppRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppRoleRepository extends IRepository<AppRole> {

    boolean existsByCodeAndDeletedOnIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedOnIsNull(String code, UUID id);

    Optional<AppRole> findByIdAndDeletedOnIsNull(UUID id);

    // Dùng cho RoleGroup (BL-111) — nạp nhiều "vai trò" con theo id để enrich RoleGroupDto.
    List<AppRole> findByIdInAndDeletedOnIsNull(List<UUID> ids);

    Page<AppRole> search(SearchAppRolesRequest request, Pageable pageable);
}
