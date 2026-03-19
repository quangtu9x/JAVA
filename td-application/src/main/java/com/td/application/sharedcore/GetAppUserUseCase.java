package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAppUserUseCase {

    private final AppUserRepository appUserRepository;

    public Result<AppUserDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID người dùng không được để trống");
            }

            var opt = appUserRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy người dùng với ID: " + id);
            }

            return Result.success(AppUserDtoMapper.map(opt.get()));
        } catch (Exception ex) {
            return Result.failure("Lấy người dùng thất bại: " + ex.getMessage());
        }
    }
}
