package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.sharedcore.AppUser;
import com.td.domain.sharedcore.AppUserRoleGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// BL-108: User Management — Core CRUD.
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class CreateAppUserUseCase {

    private final AppUserRepository appUserRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleGroupRepository roleGroupRepository;
    private final AppUserRoleGroupRepository appUserRoleGroupRepository;
    private final DynamicDocumentFieldsService dynamicDocumentFieldsService;

    public Result<UUID> execute(CreateAppUserRequest request) {
        try {
            String username = TextNormalizer.normalize(request.getUsername()).toLowerCase();
            String fullName = TextNormalizer.normalizeAndSanitize(request.getFullName());

            if (username.isBlank()) {
                return Result.failure("Tên đăng nhập không được để trống");
            }
            if (appUserRepository.existsByUsernameAndDeletedOnIsNull(username)) {
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

            // Gap #2 (docs/analysis/18-checklist-port-kiem-nhiem-domino-sang-java.md, 08/09/2026):
            // validate parentUserId (kiêm nhiệm) — trước đây API không tự bảo vệ (an toàn trong thực
            // tế vì FE luôn gửi đúng id người dùng chính đang mở), nay chặn tận API để tránh cụm mồ
            // côi/lồng nhau nếu có nguồn gọi khác ngoài FE hiện tại. KHÔNG cần chặn tự-trỏ-chính-mình
            // vì id bản ghi đang tạo chưa tồn tại tại thời điểm này, và UpdateAppUserRequest CỐ Ý
            // không có field parentUserId (liên kết kiêm nhiệm bất biến sau khi tạo — xem tài liệu
            // 05 §4.2) nên không có đường nào tạo self-reference sau này.
            if (request.getParentUserId() != null) {
                var parentOpt = appUserRepository.findByIdAndDeletedOnIsNull(request.getParentUserId());
                if (parentOpt.isEmpty()) {
                    return Result.failure("Không tìm thấy người dùng cha (kiêm nhiệm) với ID: " + request.getParentUserId());
                }
                if (parentOpt.get().getParentUserId() != null) {
                    return Result.failure("Người dùng cha đang là một hồ sơ kiêm nhiệm khác, không thể tạo kiêm nhiệm lồng nhau");
                }
            }

            List<UUID> roleGroupIds = request.getRoleGroupIds() == null ? List.of() : request.getRoleGroupIds();
            String roleGroupError = validateRoleGroupsExistAndActive(roleGroupIds);
            if (roleGroupError != null) {
                return Result.failure(roleGroupError);
            }

            var user = new AppUser(
                null, // keycloak_subject: gắn ở đợt tích hợp Keycloak riêng, xem plan BL-108
                username,
                fullName,
                TextNormalizer.normalize(request.getEmail()),
                request.getOrganizationId(),
                request.getPositionId(),
                request.getIsActive() == null || request.getIsActive(),
                request.getLevel(),
                request.getSortOrder() == null ? CreateAppUserRequest.DEFAULT_SORT_ORDER : request.getSortOrder(),
                TextNormalizer.normalize(request.getPhoneNumber()),
                TextNormalizer.normalize(request.getGender()),
                request.getBirthdayDate(),
                TextNormalizer.normalizeAndSanitize(request.getAddress()),
                TextNormalizer.normalize(request.getUserIdNo()),
                TextNormalizer.normalize(request.getCapLanhDao()),
                TextNormalizer.normalize(request.getIdentifier()),
                request.getIsSim() != null && request.getIsSim(),
                request.getParentUserId()
            );

            if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                String dynamicFieldsJson = dynamicDocumentFieldsService.buildDynamicFieldsJson(
                    request.getDynamicFields(), request.getDateFields(),
                    request.getNumberFields(), request.getRichtextFields());
                user.replaceDynamicFields(dynamicFieldsJson);
            }

            var saved = appUserRepository.save(user);

            // "Nhóm vai trò" (BL-111, N:N — bổ sung 19/08/2026): user vừa tạo, chưa có liên kết nào
            // trước đó nên chỉ cần INSERT, không cần xoá gì (khác UpdateAppUserUseCase).
            for (UUID roleGroupId : Set.copyOf(roleGroupIds)) {
                appUserRoleGroupRepository.save(new AppUserRoleGroup(saved.getId(), roleGroupId, true));
            }

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo người dùng thất bại", ex);
            return Result.failure("Tạo người dùng thất bại: " + ex.getMessage());
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
}
