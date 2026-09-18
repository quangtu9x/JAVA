package com.td.worker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Tạo schema cho database RIÊNG của Worker (checkpoint store):
 * <ul>
 *   <li>{@code worker.db.schema-management=flyway} (mặc định, production): chạy Flyway với
 *       migration {@code classpath:db/migration-worker} — đúng pattern
 *       {@code TenantFlywayMigrationService} của project tham khảo.</li>
 *   <li>{@code worker.db.schema-management=script} (test/H2): chạy script idempotent
 *       {@code db/worker-schema-h2.sql} hoặc {@code db/worker-schema-postgres.sql}.</li>
 * </ul>
 *
 * <p>Worker chỉ migrate database CỦA MÌNH — KHÔNG bao giờ migrate database tenant (td-web là
 * người duy nhất migrate tenant — docs/architecture/11 §13.4). Chạy trong
 * {@link #afterPropertiesSet()} để hoàn tất TRƯỚC khi listener bắt đầu nhận message.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerSchemaMigrator implements InitializingBean {

    private final WorkerProperties props;
    private final DataSource workerDataSource;

    @Override
    public void afterPropertiesSet() {
        migrate();
    }

    public void migrate() {
        switch (props.getDb().getSchemaManagement()) {
            case "flyway" -> migrateViaFlyway();
            case "script" -> migrateViaScript();
            default -> throw new IllegalStateException(
                "worker.db.schema-management không hợp lệ: " + props.getDb().getSchemaManagement()
                    + " (chỉ hỗ trợ 'flyway' | 'script')");
        }
    }

    private void migrateViaFlyway() {
        Flyway flyway = Flyway.configure()
            .dataSource(workerDataSource)
            .locations("classpath:db/migration-worker")
            .baselineOnMigrate(true)
            .load();
        var result = flyway.migrate();
        log.info("Flyway migrate database Worker (migration-worker): {} migration được áp dụng",
            result.migrationsExecuted);
    }

    private void migrateViaScript() {
        String url = props.getDb().getUrl();
        String script = url != null && url.contains("h2")
            ? "db/worker-schema-h2.sql"
            : "db/worker-schema-postgres.sql";
        log.info("Schema management=script: chạy {}", script);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource(script));
        populator.setContinueOnError(false);
        populator.execute(workerDataSource);
    }
}