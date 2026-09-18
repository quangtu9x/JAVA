package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("identityTransactionManager")
public class DeleteRoleGroupUseCase {

    private final RoleGroupRepository roleGroupRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = roleGroupRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy nhóm vai trò với ID: " + id);
            }

            var roleGroup = opt.get();
            roleGroup.markAsDeleted(resolveCurrentUserId());
            var saved = roleGroupRepository.save(roleGroup);

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Xóa nhóm vai trò thất bại", ex);
            return Result.failure("Xóa nhóm vai trò thất bại: " + ex.getMessage());
        }
    }

    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
