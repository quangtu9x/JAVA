package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.domain.sharedcore.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-108: User Management — Core CRUD.
// Theo mẫu DeleteCategoryUseCase (dùng CurrentUserProvider để ghi đúng người xoá) — KHÔNG theo mẫu
// DeleteOrganizationUseCase (dùng UUID.randomUUID() cho deletedBy, xác nhận là sai khi đối chiếu).
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("identityTransactionManager")
public class DeleteAppUserUseCase {

    private final AppUserRepository appUserRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AppUserDeactivationGuard deactivationGuard;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = appUserRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy người dùng với ID: " + id);
            }

            var user = opt.get();
            String blocked = deactivationGuard.checkDelete(user);
            if (blocked != null) {
                return Result.failure(blocked);
            }
            user.markAsDeleted(resolveCurrentUserId());
            var saved = appUserRepository.save(user);

            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Xóa người dùng thất bại", ex);
            return Result.failure("Xóa người dùng thất bại: " + ex.getMessage());
        }
    }

    // Ghi đúng người xóa thật (BL-103 audit convention) — null khi không xác định được user hiện tại.
    private UUID resolveCurrentUserId() {
        return currentUserProvider.currentUsername()
            .flatMap(appUserRepository::findByUsernameAndDeletedOnIsNull)
            .map(AppUser::getId)
            .orElse(null);
    }
}
