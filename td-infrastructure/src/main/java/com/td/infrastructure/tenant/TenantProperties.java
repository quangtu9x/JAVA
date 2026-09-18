package com.td.infrastructure.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình multi-tenant (BL-303, thu gọn theo BL-117) — database-per-tenant. Danh sách tenant
 * KHÔNG còn khai báo tĩnh ở đây (trước đây {@code app.multitenancy.tenants}, YAML map) — chuyển
 * hẳn sang bảng {@code tenants} động trong database "control-plane" (xem {@code Tenant},
 * {@code TenantDataSourceConfig}, {@code PlatformTenantsController}). Field còn lại ở đây chỉ là
 * cấu hình BOOTSTRAP tĩnh (đọc lúc khởi động, không đổi lúc runtime).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.multitenancy")
public class TenantProperties {

    /**
     * Tenant dùng khi không xác định được tenant từ JWT (claim thiếu/không hợp lệ) — đảm bảo
     * request của hệ thống hiện tại (chỉ có 1 tenant Thanh Hoá) không bị ảnh hưởng. PHẢI là 1 tenant
     * ACTIVE có thật trong bảng {@code tenants}, nếu không app từ chối khởi động (xem
     * {@code TenantDataSourceConfig}).
     */
    private String defaultTenantId = "H05";
}
