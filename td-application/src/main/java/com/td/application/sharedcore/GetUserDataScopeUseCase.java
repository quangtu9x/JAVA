package com.td.application.sharedcore;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserDataScopeUseCase {

    private final UserDataScopeRepository userDataScopeRepository;

    public Result<UserDataScopeDto> execute(UUID id) {
        try {
            if (id == null) {
                return Result.failure("ID phạm vi dữ liệu không được để trống");
            }

            var opt = userDataScopeRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy phạm vi dữ liệu với ID: " + id);
            }

            return Result.success(UserDataScopeDtoMapper.map(opt.get()));
        } catch (Exception ex) {
            return Result.failure("Lấy phạm vi dữ liệu thất bại: " + ex.getMessage());
        }
    }
}
