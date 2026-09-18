package com.td.worker.infrastructure.tenant;

import com.td.worker.config.WorkerMetrics;
import com.td.worker.config.WorkerProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý connection pool HikariCP cho TỪNG database tenant (database-per-tenant, tạo LƯỜI,
 * giới hạn {@code worker.tenant.max-pools} qua eviction LRU). Đúng pattern
 * {@code TenantPoolFactory}/{@code TenantRoutingDataSource} của project tham khảo nhưng KHÔNG
 * migrate schema tenant (xem docs/architecture/11 §13.4: td-web là người duy nhất migrate).
 */
@Slf4j
@Component
public class TenantDataSourceManager {

    private final WorkerProperties props;
    private final TenantRegistry registry;
    private final WorkerMetrics metrics;

    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastAccess = new ConcurrentHashMap<>();

    public TenantDataSourceManager(WorkerProperties props, TenantRegistry registry, WorkerMetrics metrics) {
        this.props = props;
        this.registry = registry;
        this.metrics = metrics;
    }

    /** Lấy (hoặc tạo) pool của tenant. Empty nếu tenant không có trong registry/không active. */
    public Optional<DataSource> getDataSource(String tenantId) {
        HikariDataSource existing = pools.get(tenantId);
        if (existing != null) {
            lastAccess.put(tenantId, Instant.now());
            return Optional.of(existing);
        }
        Optional<TenantRecord> record = registry.find(tenantId);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> password = registry.cipher().map(c -> c.decrypt(record.get().encryptedPassword()));
        if (password.isEmpty()) {
            log.error("Không có cipher để giải mã mật khẩu tenant '{}' - kiểm tra TENANT_CREDENTIAL_ENCRYPTION_KEY/SALT", tenantId);
            return Optional.empty();
        }
        HikariDataSource created = buildPool(tenantId, record.get(), password.get());
        HikariDataSource race = pools.putIfAbsent(tenantId, created);
        if (race != null) {
            created.close();
            lastAccess.put(tenantId, Instant.now());
            return Optional.of(race);
        }
        lastAccess.put(tenantId, Instant.now());
        metrics.tenantPoolCount.set(pools.size());
        evictIfNeeded();
        log.info("Tạo mới connection pool cho tenant '{}' (jdbc: {}:{} / {})",
            tenantId, record.get().host(), record.get().port(), record.get().databaseName());
        return Optional.of(created);
    }

    /** Trả pool hiện có (không tự tạo). */
    public Optional<DataSource> getExisting(String tenantId) {
        return Optional.ofNullable(pools.get(tenantId));
    }

    /** Evict pool tenant cũ khi chạm max-pools (LRU theo lần truy cập gần nhất). */
    private synchronized void evictIfNeeded() {
        int maxPools = props.getTenant().getMaxPools();
        while (pools.size() > maxPools) {
            String victim = lastAccess.entrySet().stream()
                .min(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
            if (victim == null) {
                break;
            }
            HikariDataSource ds = pools.remove(victim);
            lastAccess.remove(victim);
            metrics.tenantPoolCount.set(pools.size());
            if (ds != null) {
                ds.close();
                log.info("Evict pool tenant '{}' do vượt worker.tenant.max-pools={}", victim, maxPools);
            }
        }
    }

    private HikariDataSource buildPool(String tenantId, TenantRecord record, String password) {
        WorkerProperties.Tenant t = props.getTenant();
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("hikari-worker-tenant-" + tenantId);
        cfg.setJdbcUrl(record.jdbcUrl());
        cfg.setUsername(record.username());
        cfg.setPassword(password);
        cfg.setDriverClassName(
            (record.driverClassName() != null && !record.driverClassName().isBlank())
                ? record.driverClassName() : "org.postgresql.Driver");
        // Cho phép bind String param vào cột JSONB (đúng quyết định BL-101 của project tham khảo).
        cfg.addDataSourceProperty("stringtype", "unspecified");
        cfg.setMinimumIdle(t.getPoolMinimumIdle());
        cfg.setMaximumPoolSize(t.getPoolMaximumSize());
        cfg.setConnectionTimeout(t.getPoolConnectionTimeoutMs());
        cfg.setValidationTimeout(t.getPoolValidationTimeoutMs());
        cfg.setIdleTimeout(t.getPoolIdleTimeoutMs());
        cfg.setMaxLifetime(t.getPoolMaxLifetimeMs());
        cfg.setAutoCommit(t.isPoolAutoCommit());
        return new HikariDataSource(cfg);
    }

    /** Đóng toàn bộ pool tenant khi shutdown. */
    public synchronized void closeAll() {
        int count = pools.size();
        pools.values().forEach(ds -> {
            try {
                ds.close();
            } catch (RuntimeException e) {
                log.warn("Lỗi khi đóng pool tenant: {}", e.getMessage());
            }
        });
        pools.clear();
        lastAccess.clear();
        metrics.tenantPoolCount.set(0);
        log.info("Đã đóng {} connection pool tenant", count);
    }
}
