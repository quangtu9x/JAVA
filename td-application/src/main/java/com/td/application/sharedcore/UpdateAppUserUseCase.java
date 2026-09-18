package com.td.application.sharedcore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.AppUserRoleGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// BL-108: User Management — Core CRUD.
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class UpdateAppUserUseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleGroupRepository roleGroupRepository;
    private final AppUserRoleGroupRepository appUserRoleGroupRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(UUID id, UpdateAppUserRequest request) {
        try {
            var opt = appUserRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy người dùng với ID: " + id);
            }
            var current = opt.get();

            String username = request.getUsername() != null
                ? TextNormalizer.normalize(request.getUsername()).toLowerCase()
                : null;
            if (username != null && !username.equals(current.getUsername())
                    && appUserRepository.existsByUsernameAndIdNotAndDeletedOnIsNull(username, id)) {
                return Result.failure("Tên đăng nhập '" + username + "' đã tồn tại");
            }

            if (request.getOrganizationId() != null) {
                var orgOpt = organizationRepository.findByIdAndDeletedOnIsNull(request.getOrganizationId());
                if (orgOpt.isEmpty()) {
                    return Result.failure("Không tìm thấy tổ chức với ID: " + request.getOrganizationId());
                }
                if (!orgOpt.get().isActive()) {
                    return Result.failure("Tổ chức đang bị vô hiệu hóa, không thể gán cho người dùng");
                }
            }

            if (request.isUpdateRoleGroups()) {
                List<UUID> wantedRoleGroupIds = request.getRoleGroupIds() == null ? List.of() : request.getRoleGroupIds();
                String roleGroupError = validateRoleGroupsExistAndActive(wantedRoleGroupIds);
                if (roleGroupError != null) {
                    return Result.failure(roleGroupError);
                }
            }

            current.updateProfile(
                username,
                TextNormalizer.normalizeAndSanitize(request.getFullName()),
                TextNormalizer.normalize(request.getEmail()),
                request.getOrganizationId(),
                request.getPositionId(),
                request.getIsActive(),
                request.getLevel(),
                request.getSortOrder(),
                TextNormalizer.normalize(request.getPhoneNumber()),
                TextNormalizer.normalize(request.getGender()),
                request.getBirthdayDate(),
                TextNormalizer.normalizeAndSanitize(request.getAddress()),
                TextNormalizer.normalize(request.getUserIdNo()),
                TextNormalizer.normalize(request.getCapLanhDao()),
                TextNormalizer.normalize(request.getIdentifier()),
                request.getIsSim()
            );

            if (request.isUpdateRoleGroups()) {
                replaceRoleGroups(id, request.getRoleGroupIds() == null ? List.of() : request.getRoleGroupIds());
            }

            if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                // Merge vào field động hiện có (không xoá field cũ không được nhắc tới trong lần
                // sửa này) — cùng pattern đã dùng ở UpdateOrganizationUseCase (BL-109).
                Map<String, Object> existingFields =
                    dynamicDocumentFieldsService.parseDynamicFields(current.getDynamicFieldsJson());
                String newFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                    request.getDynamicFields(), request.getDateFields(),
                    request.getNumberFields(), request.getRichtextFields());
                Map<String, Object> newFields = dynamicDocumentFieldsService.parseDynamicFields(newFieldsJson);

                Map<String, Object> merged = new LinkedHashMap<>(existingFields);
                merged.putAll(newFields);
                current.replaceDynamicFields(toJson(merged));
            }

            var saved = appUserRepository.save(current);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Cập nhật người dùng thất bại", ex);
            return Result.failure("Cập nhật người dùng thất bại: " + ex.getMessage());
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String validateRoleGroupsExistAndActive(List<UUID> roleGroupIds) {
        for (UUID roleGroupId : roleGroupIds) {
            var groupOpt = roleGroupRepository.findByIdAndDeletedOnIsNull(roleGroupId);
            if (groupOpt.isEmpty()) {
                return "Không tìm thấy nhóm vai trò với ID: " + roleGroupId;
            }
            if (!groupOpt.get().isActive()) {
                return "Nhóm vai trò '" + groupOpt.get().getName() + "' đang bị vô hiệu hóa, không thể gán cho người dùng";
            }
        }
        return null;
    }

    // Thay TOÀN BỘ danh sách "Nhóm vai trò" của user (N:N, bổ sung 19/08/2026) — xoá mềm liên kết
    // không còn được nhắc tới, tạo mới liên kết chưa có (không đụng liên kết vẫn còn giữ nguyên).
    // Cùng pattern UpdateRoleGroupUseCase.replaceRoles()/replacePermissions().
    private void replaceRoleGroups(UUID appUserId, List<UUID> wantedRoleGroupIds) {
        Set<UUID> wanted = Set.copyOf(wantedRoleGroupIds);
        var existing = appUserRoleGroupRepository.findByAppUserIdAndDeletedOnIsNull(appUserId);
        Set<UUID> existingRoleGroupIds = existing.stream()
            .map(AppUserRoleGroup::getRoleGroupId).collect(Collectors.toSet());

        UUID currentUserId = resolveCurrentUserId();
        for (var row : existing) {
            if (!wanted.contains(row.getRoleGroupId())) {
                row.markAsDeleted(currentUserId);
                appUserRoleGroupRepository.save(row);
            }
        }
        for (UUID roleGroupId : wanted) {
            if (!existingRoleGroupIds.contains(roleGroupId)) {
                appUserRoleGroupRepository.save(new AppUserRoleGroup(appUserId, roleGroupId, true));
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
