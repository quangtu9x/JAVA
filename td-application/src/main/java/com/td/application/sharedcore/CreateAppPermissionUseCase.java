package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import com.td.domain.sharedcore.AppPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-111: Vai trò người dùng — CRUD "Quyền".
@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class CreateAppPermissionUseCase {

    private final AppPermissionRepository appPermissionRepository;

    public Result<UUID> execute(CreateAppPermissionRequest request) {
        try {
            String code = TextNormalizer.normalize(request.getCode());
            if (code.isBlank()) {
                return Result.failure("Mã quyền không được để trống");
            }
            if (appPermissionRepository.existsByCodeAndDeletedOnIsNull(code)) {
                return Result.failure("Mã quyền '" + code + "' đã tồn tại");
            }

            var permission = new AppPermission(
                code,
                TextNormalizer.normalizeAndSanitize(request.getName()),
                TextNormalizer.normalize(request.getModuleKey()),
                TextNormalizer.normalizeAndSanitize(request.getDescription()),
                request.getIsActive() == null || request.getIsActive()
            );

            var saved = appPermissionRepository.save(permission);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo quyền thất bại", ex);
            return Result.failure("Tạo quyền thất bại: " + ex.getMessage());
        }
    }
}
