package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.sharedcore.RoleGroup;
import com.td.domain.sharedcore.RoleGroupPermission;
import com.td.domain.sharedcore.RoleGroupRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// BL-111: Vai trò người dùng — CRUD "Nhóm vai trò".
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class CreateRoleGroupUseCase {

    private final RoleGroupRepository roleGroupRepository;
    private final RoleGroupRoleRepository roleGroupRoleRepository;
    private final RoleGroupPermissionRepository roleGroupPermissionRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;

    public Result<UUID> execute(CreateRoleGroupRequest request) {
        try {
            String name = TextNormalizer.normalizeAndSanitize(request.getName());
            if (name == null || name.isBlank()) {
                return Result.failure("Tên nhóm vai trò không được để trống");
            }
            if (roleGroupRepository.existsByNameAndDeletedOnIsNull(name)) {
                return Result.failure("Tên nhóm vai trò '" + name + "' đã tồn tại");
            }

            List<UUID> roleIds = request.getRoleIds() == null ? List.of() : request.getRoleIds();
            if (!roleIds.isEmpty()) {
                var found = appRoleRepository.findByIdInAndDeletedOnIsNull(roleIds);
                if (found.size() != roleIds.stream().distinct().count()) {
                    return Result.failure("Một hoặc nhiều vai trò không tồn tại");
                }
            }
            List<UUID> permissionIds = request.getPermissionIds() == null ? List.of() : request.getPermissionIds();
            if (!permissionIds.isEmpty()) {
                var found = appPermissionRepository.findByIdInAndDeletedOnIsNull(permissionIds);
                if (found.size() != permissionIds.stream().distinct().count()) {
                    return Result.failure("Một hoặc nhiều quyền không tồn tại");
                }
            }

            var roleGroup = new RoleGroup(
                name,
                request.getSortOrder() == null ? 0 : request.getSortOrder(),
                request.getFullAccess() == null || request.getFullAccess(),
                request.getIsActive() == null || request.getIsActive()
            );
            var saved = roleGroupRepository.save(roleGroup);

            for (UUID roleId : roleIds.stream().distinct().toList()) {
                roleGroupRoleRepository.save(new RoleGroupRole(saved.getId(), roleId, true));
            }
            for (UUID permissionId : permissionIds.stream().distinct().toList()) {
                roleGroupPermissionRepository.save(new RoleGroupPermission(saved.getId(), permissionId, true));
            }

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo nhóm vai trò thất bại", ex);
            return Result.failure("Tạo nhóm vai trò thất bại: " + ex.getMessage());
        }
    }
}
