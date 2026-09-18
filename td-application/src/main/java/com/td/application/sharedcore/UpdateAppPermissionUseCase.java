package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional("identityTransactionManager")
@Slf4j
public class UpdateAppPermissionUseCase {

    private final AppPermissionRepository appPermissionRepository;

    public Result<UUID> execute(UUID id, UpdateAppPermissionRequest request) {
        try {
            var opt = appPermissionRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy quyền với ID: " + id);
            }

            var permission = opt.get();
            permission.update(
                TextNormalizer.normalizeAndSanitize(request.getName()),
                TextNormalizer.normalize(request.getModuleKey()),
                TextNormalizer.normalizeAndSanitize(request.getDescription()),
                request.getIsActive()
            );

            var saved = appPermissionRepository.save(permission);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Cập nhật quyền thất bại", ex);
            return Result.failure("Cập nhật quyền thất bại: " + ex.getMessage());
        }
    }
}
