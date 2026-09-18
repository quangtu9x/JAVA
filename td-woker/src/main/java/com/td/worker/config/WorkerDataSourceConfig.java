package com.td.worker.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Connection pool cho database RIÊNG của Worker (checkpoint store). KHÔNG đụng database
 * business của td-web. Bảng schema do {@link WorkerSchemaMigrator} tạo.
 */
@Configuration
public class WorkerDataSourceConfig {

    @Bean(name = "workerDataSource", destroyMethod = "close")
    public DataSource workerDataSource(WorkerProperties props) {
        WorkerProperties.Db db = props.getDb();
        WorkerProperties.Hikari h = db.getHikari();
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("hikari-worker-checkpoint");
        cfg.setJdbcUrl(db.getUrl());
        cfg.setUsername(db.getUsername());
        cfg.setPassword(db.getPassword());
        cfg.setDriverClassName(db.getDriverClassName());
        cfg.setMinimumIdle(h.getMinimumIdle());
        cfg.setMaximumPoolSize(h.getMaximumPoolSize());
        cfg.setConnectionTimeout(h.getConnectionTimeoutMs());
        cfg.setValidationTimeout(h.getValidationTimeoutMs());
        cfg.setIdleTimeout(h.getIdleTimeoutMs());
        cfg.setMaxLifetime(h.getMaxLifetimeMs());
        cfg.setAutoCommit(h.isAutoCommit());
        return new HikariDataSource(cfg);
    }
}
