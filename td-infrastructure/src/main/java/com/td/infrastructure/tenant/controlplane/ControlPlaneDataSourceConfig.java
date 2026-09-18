package com.td.infrastructure.tenant.controlplane;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * DataSource RIÊNG cho database "control-plane" (BL-117) — 1 database Postgres cố định, KHÔNG
 * thuộc tenant nào, chứa bảng {@code categories} (dùng chung mọi tenant, trước đây ở schema
 * `shared` cùng database với tenant — xem lịch sử BL-115) và {@code tenants} (registry tenant thật).
 *
 * <p>KHÔNG đánh dấu {@code @Primary} — {@code TenantDataSourceConfig} vẫn giữ {@code @Primary} cho
 * {@code DataSource} nghiệp vụ (routing theo tenant), tránh xung đột.
 *
 * <p>Đây là hạ tầng LÕI (mọi request đều cần route tenant qua registry ở đây) nên KHÔNG áp dụng kỹ
 * thuật "cho phép khởi động dù chưa kết nối được" như {@code ErrorTrackingDataSourceConfig} — nếu
 * DB control-plane không kết nối được, app PHẢI dừng khởi động ngay (fail-fast), không được chạy nửa
 * vời.
 */
@Configuration
@RequiredArgsConstructor
public class ControlPlaneDataSourceConfig {

    private final ControlPlaneFlywayMigrationService controlPlaneFlywayMigrationService;

    @Value("${app.multitenancy.control-plane.url}")
    private String url;

    @Value("${app.multitenancy.control-plane.username}")
    private String username;

    @Value("${app.multitenancy.control-plane.password}")
    private String password;

    @Value("${app.multitenancy.control-plane.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean(name = "controlPlaneDataSource")
    public DataSource controlPlaneDataSource() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-control-plane");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(10);
        // BAT BUOC false - HikariCP mac dinh autoCommit=true, xung dot voi JpaTransactionManager/
        // Hibernate tu goi connection.commit() tuong minh trong 1 @Transactional (Postgres tu choi
        // voi loi "Cannot commit when autoCommit is enabled" neu connection dang o che do autoCommit).
        // Da xay ra that (sua ngay tai day) - TenantPoolFactory (DataSource nghiep vu) da lam dung
        // tu dau (doc tu spring.datasource.hikari.auto-commit, mac dinh false), rieng DataSource
        // control-plane nay bi thieu dong nay khi viet moi.
        config.setAutoCommit(false);
        // BL-101: cho phep bind String param vao cot JSONB (categories.dynamic_fields) - giu dung
        // 1 dong config nay da dung o TenantDataSourceConfig.
        config.addDataSourceProperty("stringtype", "unspecified");
        HikariDataSource dataSource = new HikariDataSource(config);

        // Chay Flyway TRUOC khi tra DataSource - dung pattern TenantDataSourceConfig.dataSource():
        // controlPlaneEntityManagerFactory (Hibernate ddl-auto: validate) phu thuoc bean nay.
        controlPlaneFlywayMigrationService.migrate(dataSource);

        return dataSource;
    }
}
