package com.td.application.tenant;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// BL-117: KHÔNG hard-delete tenant (huỷ database thật quá nguy hiểm/khó đảo ngược) — chỉ vô hiệu
// hoá. Cố tình KHÔNG đóng/gỡ connection pool khỏi TenantRoutingDataSource lúc deactivate (giữ pool
// nhàn rỗi, rẻ, an toàn hơn hẳn việc mutate map routing đang có thể được đọc đồng thời bởi request
// khác) — chỉ chặn ở TenantResolver (registry runtime) nên request của tenant bị khoá bị TỪ CHỐI
// ngay từ bước resolve tenant, không bao giờ chạm tới DataSource.
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional("controlPlaneTransactionManager")
public class DeactivateTenantUseCase {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;

    public Result<UUID> execute(UUID id) {
        try {
            var opt = tenantRepository.findByIdAndDeletedOnIsNull(id);
            if (opt.isEmpty()) {
                return Result.failure("Không tìm thấy tenant với ID: " + id);
            }
            var entity = opt.get();
            entity.deactivate();
            var saved = tenantRepository.save(entity);
            tenantProvisioningService.markInactive(saved.getTenantId());
            return Result.success(saved.getId());
        } catch (Exception ex) {
            log.error("Vô hiệu hoá tenant thất bại", ex);
            return Result.failure("Vô hiệu hoá tenant thất bại: " + ex.getMessage());
        }
    }
}
