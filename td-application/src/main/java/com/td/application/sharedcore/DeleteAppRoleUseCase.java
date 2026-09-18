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
public class DeleteAppRoleUseCase {

    private final AppRoleRepository appRoleRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository appUserRepository;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = appRoleRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy vai trò với ID: " + id);
            }

            var role = opt.get();
            role.markAsDeleted(resolveCurrentUserId());
            var saved = appRoleRepository.save(role);

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Xóa vai trò thất bại", ex);
            return Result.failure("Xóa vai trò thất bại: " + ex.getMessage());
        }
    }

    // Ghi đúng người xóa thật (BL-103 audit) — null (không phải UUID ngẫu nhiên) khi không xác định được user
    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
