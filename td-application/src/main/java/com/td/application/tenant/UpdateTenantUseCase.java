package com.td.application.tenant;

import com.td.application.common.TextNormalizer;
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
public class UpdateTenantUseCase {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;

    public Result<UUID> execute(UUID id, UpdateTenantRequest request) {
        try {
            var opt = tenantRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy tenant với ID: " + id);
            }
            var entity = opt.get();

            // Xoay vòng credential — PHẢI gửi đủ host+port+username+password cùng lúc (xem javadoc
            // UpdateTenantRequest) để tránh phải giải mã lại mật khẩu cũ khi chỉ đổi 1 phần.
            boolean rotatingCredential = request.getHost() != null || request.getPort() != null
                || request.getUsername() != null || request.getPassword() != null;

            String newEncryptedPassword = null;
            if (rotatingCredential) {
                if (request.getHost() == null || request.getHost().isBlank()
                        || request.getPort() == null
                        || request.getUsername() == null || request.getUsername().isBlank()
                        || request.getPassword() == null || request.getPassword().isBlank()) {
                    return Result.failure("Để xoay vòng credential phải gửi đủ host, port, username và password cùng lúc");
                }
                var command = new ProvisionTenantCommand(
                    entity.getTenantId(), request.getHost(), request.getPort(), entity.getDatabaseName(),
                    request.getUsername(), request.getPassword(), request.getDriverClassName(),
                    TenantProvisionMode.ROTATE_CREDENTIAL_ONLY);
                newEncryptedPassword = tenantProvisioningService.provision(command);
            }

            entity.update(
                TextNormalizer.normalizeAndSanitize(request.getDisplayName()),
                request.getHost(), request.getPort(), request.getUsername(),
                newEncryptedPassword, request.getDriverClassName());

            var saved = tenantRepository.save(entity);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Cập nhật tenant thất bại", ex);
            return Result.failure("Cập nhật tenant thất bại: " + ex.getMessage());
        }
    }
}
