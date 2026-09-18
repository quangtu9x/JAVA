package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.RoleGroupPermission;
import com.td.domain.sharedcore.RoleGroupRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class UpdateRoleGroupUseCase {

    private final RoleGroupRepository roleGroupRepository;
    private final RoleGroupRoleRepository roleGroupRoleRepository;
    private final RoleGroupPermissionRepository roleGroupPermissionRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(UUID id, UpdateRoleGroupRequest request) {
        try {
            var opt = roleGroupRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy nhóm vai trò với ID: " + id);
            }
            var roleGroup = opt.get();

            String name = request.getName() != null
                ? TextNormalizer.normalizeAndSanitize(request.getName())
                : null;
            if (name != null && !name.equals(roleGroup.getName())
                    && roleGroupRepository.existsByNameAndIdNotAndDeletedOnIsNull(name, id)) {
                return Result.failure("Tên nhóm vai trò '" + name + "' đã được sử dụng");
            }

            roleGroup.update(name, request.getSortOrder(), request.getFullAccess(), request.getIsActive());
            var saved = roleGroupRepository.save(roleGroup);

            if (request.getRoleIds() != null) {
                var validation = validateRolesExist(request.getRoleIds());
                if (validation != null) {
                    return Result.failure(validation);
                }
                replaceRoles(id, request.getRoleIds());
            }
            if (request.getPermissionIds() != null) {
                var validation = validatePermissionsExist(request.getPermissionIds());
                if (validation != null) {
                    return Result.failure(validation);
                }
                replacePermissions(id, request.getPermissionIds());
            }

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Cập nhật nhóm vai trò thất bại", ex);
            return Result.failure("Cập nhật nhóm vai trò thất bại: " + ex.getMessage());
        }
    }

    private String validateRolesExist(List<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return null;
        }
        var found = appRoleRepository.findByIdInAndDeletedOnIsNull(roleIds);
        return found.size() == roleIds.stream().distinct().count()
            ? null
            : "Một hoặc nhiều vai trò không tồn tại";
    }

    private String validatePermissionsExist(List<UUID> permissionIds) {
        if (permissionIds.isEmpty()) {
            return null;
        }
        var found = appPermissionRepository.findByIdInAndDeletedOnIsNull(permissionIds);
        return found.size() == permissionIds.stream().distinct().count()
            ? null
            : "Một hoặc nhiều quyền không tồn tại";
    }

    // Thay TOÀN BỘ danh sách "vai trò" con của nhóm — xoá mềm liên kết không còn được nhắc tới,
    // tạo mới liên kết chưa có (không đụng tới liên kết vẫn còn giữ nguyên).
    private void replaceRoles(UUID roleGroupId, List<UUID> wantedRoleIds) {
        Set<UUID> wanted = Set.copyOf(wantedRoleIds);
        var existing = roleGroupRoleRepository.findByRoleGroupIdAndDeletedOnIsNull(roleGroupId);
        Set<UUID> existingRoleIds = existing.stream().map(RoleGroupRole::getRoleId).collect(Collectors.toSet());

        UUID currentUserId = resolveCurrentUserId();
        for (var row : existing) {
            if (!wanted.contains(row.getRoleId())) {
                row.markAsDeleted(currentUserId);
                roleGroupRoleRepository.save(row);
            }
        }
        for (UUID roleId : wanted) {
            if (!existingRoleIds.contains(roleId)) {
                roleGroupRoleRepository.save(new RoleGroupRole(roleGroupId, roleId, true));
            }
        }
    }

    private void replacePermissions(UUID roleGroupId, List<UUID> wantedPermissionIds) {
        Set<UUID> wanted = Set.copyOf(wantedPermissionIds);
        var existing = roleGroupPermissionRepository.findByRoleGroupIdAndDeletedOnIsNull(roleGroupId);
        Set<UUID> existingPermissionIds = existing.stream()
            .map(RoleGroupPermission::getPermissionId).collect(Collectors.toSet());

        UUID currentUserId = resolveCurrentUserId();
        for (var row : existing) {
            if (!wanted.contains(row.getPermissionId())) {
                row.markAsDeleted(currentUserId);
                roleGroupPermissionRepository.save(row);
            }
        }
        for (UUID permissionId : wanted) {
            if (!existingPermissionIds.contains(permissionId)) {
                roleGroupPermissionRepository.save(new RoleGroupPermission(roleGroupId, permissionId, true));
            }
        }
    }

    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
