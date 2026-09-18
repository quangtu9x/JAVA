package com.td.application.tenant;

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
public class ActivateTenantUseCase {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = tenantRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy tenant với ID: " + id);
            }
            var entity = opt.get();
            entity.activate();
            var saved = tenantRepository.save(entity);
            tenantProvisioningService.markActive(saved.getTenantId());
            // Trường hợp tenant chưa từng được hot-add (vd. bị deactivate từ lúc app khởi động, pool
            // chưa bao giờ tồn tại) — hot-add lại ngay; no-op nếu pool đã có sẵn.
            tenantProvisioningService.ensurePoolRegistered(saved);
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Kích hoạt tenant thất bại", ex);
            return Result.failure("Kích hoạt tenant thất bại: " + ex.getMessage());
        }
    }
}
