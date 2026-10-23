package com.td.application.tonghop;

import com.td.application.common.models.Result;
import com.td.application.tenant.TenantRepository;
import com.td.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Danh sách đơn vị (tenant) cho ô "Lọc theo đơn vị" ở các màn /tonghop — chỉ trong phạm vi người dùng
 * ({@link ProvinceScopePolicy}): toàn tỉnh → mọi tenant đang hoạt động; đơn vị → chỉ tenant mình; không xác định → rỗng.
 */
@Service
@RequiredArgsConstructor
public class ListProvinceTenantsUseCase {

    private final ProvinceScopePolicy provinceScopePolicy;
    private final TenantRepository tenantRepository;

    public Result<List<TenantOption>> execute() {
        ProvinceScope scope = provinceScopePolicy.current();
        if (!scope.isResolved()) {
            return Result.success(List.of());
        }
        List<TenantOption> options = tenantRepository.findAllByDeletedOnIsNull().stream()
            .filter(Tenant::isActive)
            .filter(t -> scope.covers(t.getTenantId()))
            .map(t -> new TenantOption(t.getTenantId(), t.getDisplayName()))
            .sorted(Comparator.comparing(TenantOption::tenantId))
            .toList();
        return Result.success(options);
    }

    public record TenantOption(String tenantId, String name) {
    }
}
