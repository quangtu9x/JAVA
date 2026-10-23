package com.td.application.tonghop;

import com.td.application.common.models.Result;
import com.td.application.tenant.TenantRepository;
import com.td.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Phạm vi dữ liệu của người dùng hiện tại trên các màn /tonghop — FE dùng để ghi "Toàn tỉnh"/"Đơn vị: …" và ẩn nút
 * "Đồng bộ"; server vẫn tự áp {@link ProvinceScopePolicy} ở từng use case.
 */
@Service
@RequiredArgsConstructor
public class GetProvinceScopeUseCase {

    private final ProvinceScopePolicy provinceScopePolicy;
    private final TenantRepository tenantRepository;

    public Result<ProvinceScope> execute() {
        ProvinceScope scope = provinceScopePolicy.current();
        if (scope.getTenantId() != null) {
            try {
                scope.setTenantName(tenantRepository.findByTenantIdAndDeletedOnIsNull(scope.getTenantId())
                    .map(Tenant::getDisplayName).orElse(null));
            } catch (RuntimeException ignored) {
                // Tên đơn vị chỉ để hiển thị.
            }
        }
        return Result.success(scope);
    }
}
