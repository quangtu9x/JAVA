package com.td.infrastructure.tenant;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry runtime (in-memory, ThreadSafe) ghi nhớ trạng thái active/inactive của MỌI tenant đã
 * biết (BL-117) — nạp lúc khởi động từ bảng {@code tenants} (control-plane) bởi
 * {@code TenantDataSourceConfig}, cập nhật đồng bộ bởi {@code Activate}/{@code DeactivateTenantUseCase}
 * (qua {@code PostgresTenantProvisioningService}).
 *
 * <p>Phân biệt RÕ 2 trường hợp — quan trọng để tránh rò rỉ dữ liệu chéo tenant (xem
 * {@code TenantResolver}):
 * <ul>
 *   <li>{@link #isKnown(String)} == false — tenant KHÔNG tồn tại/không xác định được từ claim →
 *       {@code TenantResolver} fallback về tenant mặc định (hành vi cũ, giữ nguyên).</li>
 *   <li>{@link #isKnown(String)} == true nhưng {@link #isActive(String)} == false — tenant tồn tại
 *       nhưng ĐÃ BỊ KHOÁ → {@code TenantResolver} phải TỪ CHỐI request (KHÔNG fallback), tránh phục
 *       vụ nhầm dữ liệu tenant mặc định cho người dùng của tenant đã khoá.</li>
 * </ul>
 */
@Component
public class TenantActiveRegistry {

    private final Map<String, Boolean> activeByTenantId = new ConcurrentHashMap<>();

    public void register(String tenantId, boolean active) {
        activeByTenantId.put(tenantId, active);
    }

    public void markActive(String tenantId) {
        activeByTenantId.put(tenantId, true);
    }

    public void markInactive(String tenantId) {
        activeByTenantId.put(tenantId, false);
    }

    public boolean isKnown(String tenantId) {
        return activeByTenantId.containsKey(tenantId);
    }

    public boolean isActive(String tenantId) {
        return Boolean.TRUE.equals(activeByTenantId.get(tenantId));
    }
}
