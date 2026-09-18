package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.domain.sharedcore.RoleGroupPermission;
import com.td.domain.sharedcore.RoleGroupRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(value = "identityTransactionManager", readOnly = true)
public class GetRoleGroupUseCase {

    private final RoleGroupRepository roleGroupRepository;
    private final RoleGroupRoleRepository roleGroupRoleRepository;
    private final RoleGroupPermissionRepository roleGroupPermissionRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;

    public Result<RoleGroupDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID nhóm vai trò không được để trống");
            }

            var opt = roleGroupRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy nhóm vai trò với ID: " + id);
            }
            var roleGroup = opt.get();

            List<UUID> roleIds = roleGroupRoleRepository.findByRoleGroupIdAndDeletedOnIsNull(id)
                .stream().map(RoleGroupRole::getRoleId).toList();
            var roles = roleIds.isEmpty() ? List.<com.td.domain.sharedcore.AppRole>of()
                : appRoleRepository.findByIdInAndDeletedOnIsNull(roleIds);

            List<UUID> permissionIds = roleGroupPermissionRepository.findByRoleGroupIdAndDeletedOnIsNull(id)
                .stream().map(RoleGroupPermission::getPermissionId).toList();
            var permissions = permissionIds.isEmpty() ? List.<com.td.domain.sharedcore.AppPermission>of()
                : appPermissionRepository.findByIdInAndDeletedOnIsNull(permissionIds);

            return Result.success(RoleGroupDtoMapper.map(roleGroup, roles, permissions));
        } catch (Exception ex) {
            log.error("Lấy nhóm vai trò thất bại", ex);
            return Result.failure("Lấy nhóm vai trò thất bại: " + ex.getMessage());
        }
    }
}
