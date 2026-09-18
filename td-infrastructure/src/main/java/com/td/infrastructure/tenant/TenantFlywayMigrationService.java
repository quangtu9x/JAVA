package com.td.infrastructure.tenant;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Chạy Flyway ({@code classpath:db/migration}, bộ migration nghiệp vụ đầy đủ) cho 1 database
 * tenant CỤ THỂ (BL-117 — mỗi tenant giờ là 1 database Postgres vật lý riêng, schema {@code public}
 * mặc định là đủ, không còn cần {@code createSchemas}/multi-schema như BL-303 cũ).
 *
 * <p>Dùng chung bởi 2 nơi: {@code TenantDataSourceConfig} lúc khởi động (bắt kịp migration mới cho
 * MỌI tenant active đã có sẵn trong registry) và {@code PostgresTenantProvisioningService} lúc
 * "khởi tạo đơn vị" (tenant mới hoàn toàn, hoặc đăng ký 1 database đã có sẵn). Flyway tự idempotent
 * (theo dõi version đã áp dụng trong chính database đích) nên KHÔNG cần thêm cờ "đã chạy 1 lần" như
 * bản BL-303 cũ — gọi lại bao nhiêu lần cũng an toàn.
 */
@Service
@Slf4j
public class TenantFlywayMigrationService {

    @Value("${app.multitenancy.flyway-locations:classpath:db/migration}")
    private String flywayLocations;

    public void migrateTenant(String tenantId, String host, int port, String databaseName,
                               String username, String password) {
        String url = TenantPoolFactory.buildJdbcUrl(host, port, databaseName);
        log.info("Chay Flyway migration cho tenant '{}' (database={})", tenantId, databaseName);
        Flyway.configure()
            .dataSource(url, username, password)
            .locations(flywayLocations)
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .load()
            .migrate();
    }
}
