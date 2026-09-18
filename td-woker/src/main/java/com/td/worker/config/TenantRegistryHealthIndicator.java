package com.td.worker.config;

import com.td.worker.infrastructure.tenant.TenantRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health cho registry tenant (control-plane là dữ liệu động — hot-add tenant từ td-web).
 * Worker vẫn khởi động/phục vụ kể cả khi control-plane tạm mất (đúng quyết định
 * docs/architecture/11 §13.5 "chịu được lỗi kết nối tạm thời") — trả {@code UP} kèm mức độ
 * {registry_ready, tenants}. Khi chưa có tenant nào đăng ký → báo {@code OUT_OF_SERVICE} để
 * giám sát biết (nhưng không crash).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantRegistryHealthIndicator implements HealthIndicator {

    private final TenantRegistry registry;

    @Override
    public Health health() {
        int count = registry.all().size();
        Health.Builder builder = Health.up()
            .withDetail("tenants", count)
            .withDetail("controlPlaneEnabled", registry.isControlPlaneEnabled());
        if (registry.isControlPlaneEnabled() && count == 0) {
            builder = Health.outOfService()
                .withDetail("reason", "Control-plane reachable nhưng chưa có tenant active nào");
        }
        if (registry.isRefreshFailed()) {
            builder = Health.down()
                .withDetail("reason", "Refresh control-plane thất bại (lần gần nhất)");
        }
        return builder.build();
    }
}