package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAppRoleUseCase {

    private final AppRoleRepository appRoleRepository;

    public Result<AppRoleDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID vai trò không được để trống");
            }

            var opt = appRoleRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy vai trò với ID: " + id);
            }

            return Result.success(AppRoleDtoMapper.map(opt.get()));
        } catch (Exception ex) {
            return Result.failure("Lấy vai trò thất bại: " + ex.getMessage());
        }
    }
}
