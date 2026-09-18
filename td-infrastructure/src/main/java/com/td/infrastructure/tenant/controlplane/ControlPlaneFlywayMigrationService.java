package com.td.infrastructure.tenant.controlplane;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Chạy Flyway cho database "control-plane" (BL-117) — gọi ĐỒNG BỘ ngay trong
 * {@link ControlPlaneDataSourceConfig#controlPlaneDataSource()} (trước khi
 * {@code controlPlaneEntityManagerFactory} được tạo), đúng pattern đã dùng ở
 * {@code TenantFlywayMigrationService} cho database nghiệp vụ.
 *
 * <p>Khác {@code ErrorTrackingSchemaMigrator} (nuốt lỗi, không chặn khởi động) — ở đây lỗi PHẢI ném
 * thẳng ra ngoài: database control-plane là hạ tầng LÕI (chứa registry tenant + danh mục dùng
 * chung), không có nó thì KHÔNG THỂ route bất kỳ request nào — fail-fast lúc khởi động là đúng.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneFlywayMigrationService {

    @Value("${app.multitenancy.control-plane.flyway-locations:classpath:db/migration-controlplane}")
    private String flywayLocations;

    private volatile boolean migrated = false;

    /** Idempotent — an toàn khi gọi lại, chỉ thực sự chạy 1 lần. */
    public synchronized void migrate(javax.sql.DataSource dataSource) {
        if (migrated) {
            return;
        }

        log.info("Chay Flyway migration cho database control-plane (BL-117)");
        Flyway.configure()
            .dataSource(dataSource)
            .locations(flywayLocations)
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .load()
            .migrate();

        migrated = true;
    }
}
