package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAppPermissionUseCase {

    private final AppPermissionRepository appPermissionRepository;

    public Result<AppPermissionDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID quyền không được để trống");
            }

            var opt = appPermissionRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy quyền với ID: " + id);
            }

            return Result.success(AppPermissionDtoMapper.map(opt.get()));
        } catch (Exception ex) {
            return Result.failure("Lấy quyền thất bại: " + ex.getMessage());
        }
    }
}
