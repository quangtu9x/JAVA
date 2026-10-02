package com.td.application.sharedcore;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.sharedcore.RoleGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface RoleGroupRepository extends IRepository<RoleGroup> {

    boolean existsByNameAndDeletedOnIsNull(String name);

    boolean existsByNameAndIdNotAndDeletedOnIsNull(String name, UUID id);

    Optional<RoleGroup> findByIdAndDeletedOnIsNull(UUID id);

    Page<RoleGroup> search(SearchRoleGroupsRequest request, Pageable pageable);

    // 25/09/2026: tra tên nhiều nhóm quyền 1 lần (cột "Nhóm quyền" ở Danh sách người dùng, tránh N+1).
    java.util.List<RoleGroup> findAllByIdInAndDeletedOnIsNull(java.util.Collection<UUID> ids);
}
