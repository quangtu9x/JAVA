package com.td.application.sharedcore;

import com.td.application.common.TextNormalizer;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-108: hợp nhất 2 cơ chế kiểm trùng username không nhất quán của Domino (endpoint riêng
// KiemTrungNguoiDung + 1 query formula thô khác ở modal kiêm nhiệm) thành 1 cách kiểm tra đúng,
// dùng repository method đã có sẵn.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(value = "identityTransactionManager", readOnly = true)
public class CheckUsernameExistsUseCase {

    private final AppUserRepository appUserRepository;

    public Result<Boolean> execute(String username, UUID excludeId) {
        try {
            String normalized = TextNormalizer.normalize(username).toLowerCase();
            if (normalized.isBlank()) {
                return Result.failure("Tên đăng nhập không được để trống");
            }

            boolean exists = excludeId != null
                ? appUserRepository.existsByUsernameAndIdNotAndDeletedOnIsNull(normalized, excludeId)
                : appUserRepository.existsByUsernameAndDeletedOnIsNull(normalized);

            return Result.success(exists);
        } catch (Exception ex) {
            log.error("Kiểm tra trùng tên đăng nhập thất bại", ex);
            return Result.failure("Kiểm tra trùng tên đăng nhập thất bại: " + ex.getMessage());
        }
    }
}
