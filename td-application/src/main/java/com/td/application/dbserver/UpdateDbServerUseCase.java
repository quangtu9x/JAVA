package com.td.application.dbserver;

import com.td.application.common.TextNormalizer;
import com.td.application.common.crypto.CredentialCipher;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("controlPlaneTransactionManager")
public class UpdateDbServerUseCase {

    private final DbServerRepository dbServerRepository;
    private final CredentialCipher credentialCipher;

    public Result<UUID> execute(UUID id, UpdateDbServerRequest request) {
        try {
            var opt = dbServerRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy máy chủ CSDL với ID: " + id);
            }
            var entity = opt.get();

            String host = TextNormalizer.normalize(request.getHost());
            if (host != null && !host.equalsIgnoreCase(entity.getHost())
                    || (request.getPort() != null && !request.getPort().equals(entity.getPort()))) {
                boolean duplicated = dbServerRepository.existsByHostAndPortAndDeletedOnIsNull(
                    host != null ? host : entity.getHost(),
                    request.getPort() != null ? request.getPort() : entity.getPort());
                if (duplicated) {
                    return Result.failure("Máy chủ với host/port này đã tồn tại");
                }
            }

            entity.update(
                TextNormalizer.normalizeAndSanitize(request.getName()),
                host, request.getPort(),
                TextNormalizer.normalizeAndSanitize(request.getDescription()));

            // BL-117 (bổ sung 19/08/2026): CẢ HAI trống = giữ nguyên credential cũ (không đụng gì);
            // gửi đủ cả hai = thay thế; chỉ gửi 1 trong 2 = lỗi (nửa vời, vô nghĩa).
            String username = TextNormalizer.normalize(request.getUsername());
            String password = request.getPassword();
            boolean hasUsername = username != null && !username.isBlank();
            boolean hasPassword = password != null && !password.isBlank();
            if (hasUsername != hasPassword) {
                return Result.failure("Phải nhập đủ Username và Password để đổi credential, hoặc để trống cả hai để giữ nguyên");
            }
            if (hasUsername) {
                entity.updateCredential(username, credentialCipher.encrypt(password));
            }

            var saved = dbServerRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Cập nhật máy chủ CSDL thất bại", ex);
            return Result.failure("Cập nhật máy chủ CSDL thất bại: " + ex.getMessage());
        }
    }
}
