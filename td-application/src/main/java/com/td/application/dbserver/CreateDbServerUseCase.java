package com.td.application.dbserver;

import com.td.application.common.TextNormalizer;
import com.td.application.common.crypto.CredentialCipher;
import com.td.application.common.models.Result;
import com.td.domain.dbserver.DbServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("controlPlaneTransactionManager")
public class CreateDbServerUseCase {

    private final DbServerRepository dbServerRepository;
    private final CredentialCipher credentialCipher;

    public Result<UUID> execute(CreateDbServerRequest request) {
        try {
            String name = TextNormalizer.normalizeAndSanitize(request.getName());
            String host = TextNormalizer.normalize(request.getHost());
            String description = TextNormalizer.normalizeAndSanitize(request.getDescription());
            String username = TextNormalizer.normalize(request.getUsername());
            String password = request.getPassword();

            if (name == null || name.isBlank()) {
                return Result.failure("Tên máy chủ không được để trống");
            }
            if (host == null || host.isBlank()) {
                return Result.failure("Host không được để trống");
            }
            if (request.getPort() == null || request.getPort() < 1 || request.getPort() > 65535) {
                return Result.failure("Port không hợp lệ");
            }
            if (dbServerRepository.existsByHostAndPortAndDeletedOnIsNull(host, request.getPort())) {
                return Result.failure("Máy chủ với host '" + host + "' và port " + request.getPort() + " đã tồn tại");
            }
            // BL-117 (bổ sung 19/08/2026): credential TUỲ CHỌN nhưng KHÔNG được lưu "nửa vời" (chỉ
            // username hoặc chỉ password) — vô nghĩa cho mục đích autofill.
            boolean hasUsername = username != null && !username.isBlank();
            boolean hasPassword = password != null && !password.isBlank();
            if (hasUsername != hasPassword) {
                return Result.failure("Phải nhập đủ Username và Password, hoặc để trống cả hai");
            }
            String encryptedPassword = hasPassword ? credentialCipher.encrypt(password) : null;

            var entity = new DbServer(name, host, request.getPort(), description,
                hasUsername ? username : null, encryptedPassword);
            var saved = dbServerRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Tạo máy chủ CSDL thất bại", ex);
            return Result.failure("Tạo máy chủ CSDL thất bại: " + ex.getMessage());
        }
    }
}
