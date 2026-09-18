package com.td.application.dbserver;

import com.td.application.common.crypto.CredentialCipher;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-117 (bổ sung 19/08/2026): trả credential ĐÃ GIẢI MÃ (username+password plaintext) — xem javadoc
// DbServerCredentialDto để biết lý do đây là ngoại lệ có chủ đích. Endpoint gọi use case này PHẢI vẫn
// giữ @PreAuthorize("hasRole('PLATFORM_ADMIN')") (đã có ở class PlatformDbServersController).
@Service
@RequiredArgsConstructor
@Transactional(value = "controlPlaneTransactionManager", readOnly = true)
public class GetDbServerCredentialUseCase {

    private final DbServerRepository dbServerRepository;
    private final CredentialCipher credentialCipher;

    public Result<DbServerCredentialDto> execute(UUID id) {
        var opt = dbServerRepository.findByIdAndDeletedOnIsNull(id);
        if (opt.isEmpty()) {
            return Result.failure("Không tìm thấy máy chủ CSDL với ID: " + id);
        }
        var entity = opt.get();
        if (!entity.hasCredential()) {
            return Result.failure("Máy chủ này chưa lưu thông tin đăng nhập");
        }
        var dto = new DbServerCredentialDto();
        dto.setUsername(entity.getUsername());
        dto.setPassword(credentialCipher.decrypt(entity.getEncryptedPassword()));
        return Result.success(dto);
    }
}
