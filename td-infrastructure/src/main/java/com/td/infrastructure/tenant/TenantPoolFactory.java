package com.td.infrastructure.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Dựng 1 {@link HikariDataSource} trỏ tới database Postgres RIÊNG của 1 tenant (BL-117) — dùng
 * chung bởi {@code TenantDataSourceConfig} (nạp lúc khởi động) và
 * {@code PostgresTenantProvisioningService} (hot-add lúc runtime), tránh trùng lặp cấu hình pool.
 *
 * <p>Cố tình hardcode {@code jdbc:postgresql://} (không đọc từ {@code driverClassName} của tenant) —
 * hệ thống hiện chỉ hỗ trợ Postgres cho database-per-tenant; {@code driverClassName} lưu sẵn trong
 * bảng {@code tenants} chỉ mang tính khai báo/dự phòng cho tương lai, chưa có nhánh xử lý khác.
 */
@Component
public class TenantPoolFactory {

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int hikariMinimumIdle;

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int hikariMaximumPoolSize;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long hikariConnectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long hikariIdleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long hikariMaxLifetime;

    @Value("${spring.datasource.hikari.auto-commit:false}")
    private boolean hikariAutoCommit;

    public DataSource build(String tenantId, String host, int port, String databaseName,
                             String username, String password, String driverClassName) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-tenant-" + tenantId);
        config.setJdbcUrl(buildJdbcUrl(host, port, databaseName));
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName((driverClassName != null && !driverClassName.isBlank())
            ? driverClassName : "org.postgresql.Driver");
        // BL-101: cho phep bind String param vao cot JSONB ma khong can Postgres ep kieu tuong minh.
        config.addDataSourceProperty("stringtype", "unspecified");
        config.setMinimumIdle(hikariMinimumIdle);
        config.setMaximumPoolSize(hikariMaximumPoolSize);
        config.setConnectionTimeout(hikariConnectionTimeout);
        config.setIdleTimeout(hikariIdleTimeout);
        config.setMaxLifetime(hikariMaxLifetime);
        config.setAutoCommit(hikariAutoCommit);
        return new HikariDataSource(config);
    }

    public static String buildJdbcUrl(String host, int port, String databaseName) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
    }
}
