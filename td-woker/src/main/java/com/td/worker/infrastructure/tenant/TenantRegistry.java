package com.td.worker.infrastructure.tenant;

import com.td.worker.config.WorkerProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry tenant (database-per-tenant) — CHỈ ĐỌC từ bảng {@code tenants} trong database
 * control-plane của td-web (docs/architecture/06). Worker cần danh sách tenant động (hot-add:
 * tenant mới được tạo qua API td-web phải được Worker biết sau chu kỳ refresh mà KHÔNG restart).
 */
@Slf4j
@Component
public class TenantRegistry implements InitializingBean {

    private final WorkerProperties props;
    private final Map<String, TenantRecord> tenants = new ConcurrentHashMap<>();

    private volatile HikariDataSource controlPlaneDataSource;
    private volatile TenantCredentialCipher cipher;
    private volatile boolean refreshFailed = false;

    public TenantRegistry(WorkerProperties props) {
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        refresh();
    }

    /** Nạp lại registry. Gọi khi khởi động + định kỳ (hot-add tenant mới). */
    public synchronized void refresh() {
        if (!props.getControlPlane().isEnabled()) {
            return;
        }
        try {
            ensureControlPlaneInfra();
            JdbcTemplate jdbc = new JdbcTemplate(controlPlaneDataSource);
            List<TenantRecord> rows = jdbc.query("""
                    SELECT tenant_id, display_name, host, port, database_name, username,
                           encrypted_password, driver_class_name, active
                    FROM tenants
                    WHERE deleted_on IS NULL
                    """, (rs, i) -> new TenantRecord(
                    rs.getString("tenant_id"),
                    rs.getString("display_name"),
                    rs.getString("host"),
                    rs.getInt("port"),
                    rs.getString("database_name"),
                    rs.getString("username"),
                    rs.getString("encrypted_password"),
                    rs.getString("driver_class_name"),
                    rs.getBoolean("active")));

            Map<String, TenantRecord> next = new ConcurrentHashMap<>();
            for (TenantRecord t : rows) {
                if (t.active()) {
                    next.put(t.tenantId(), t);
                }
            }
            tenants.clear();
            tenants.putAll(next);
            refreshFailed = false;
            log.info("TenantRegistry: nạp {} tenant active từ control-plane", rows.size());
        } catch (RuntimeException e) {
            refreshFailed = true;
            if (controlPlaneDataSource == null && isFirstFailure()) {
                log.warn("Chưa kết nối được control-plane ({}): registry tenant rỗng, Worker vẫn chạy "
                        + "- các unit cần database tenant sẽ fail khi xử lý. Lỗi: {}",
                    props.getControlPlane().getUrl(), e.getMessage());
            } else {
                log.warn("Refresh TenantRegistry thất bại: {}", e.getMessage());
            }
        }
    }

    private volatile boolean firstFailureLogged = false;

    private boolean isFirstFailure() {
        boolean was = firstFailureLogged;
        firstFailureLogged = true;
        return !was;
    }

    private void ensureControlPlaneInfra() {
        if (controlPlaneDataSource == null) {
            WorkerProperties.ControlPlane cp = props.getControlPlane();
            synchronized (this) {
                if (controlPlaneDataSource == null) {
                    controlPlaneDataSource = buildPool("hikari-control-plane",
                        cp.getUrl(), cp.getUsername(), cp.getPassword(), cp.getMaxPoolSize());
                    this.cipher = new TenantCredentialCipher(
                        cp.getCredentialEncryptionKey(), cp.getCredentialEncryptionSalt());
                }
            }
        }
    }

    private static HikariDataSource buildPool(String poolName, String url, String user, String pass, int maxPool) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName(poolName);
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(maxPool);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(30_000);
        return new HikariDataSource(cfg);
    }

    public Optional<TenantRecord> find(String tenantId) {
        return Optional.ofNullable(tenants.get(tenantId));
    }

    public boolean contains(String tenantId) {
        return tenants.containsKey(tenantId);
    }

    public Map<String, TenantRecord> all() {
        return Map.copyOf(tenants);
    }

    public Optional<TenantCredentialCipher> cipher() {
        return cipher == null ? Optional.empty() : Optional.of(cipher);
    }

    public boolean isRefreshFailed() {
        return refreshFailed;
    }

    public boolean isControlPlaneEnabled() {
        return props.getControlPlane().isEnabled();
    }

    /** Chu kỳ refresh định kỳ (hot-add tenant mới, cấu hình worker.control-plane.refresh-interval). */
    @Scheduled(fixedDelayString = "#{@workerScheduleIntervals.controlPlaneRefreshIntervalMs()}")
    public void scheduledRefresh() {
        refresh();
    }

    public long refreshIntervalMs() {
        return props.getControlPlane().getRefreshInterval().toMillis();
    }

    /** Đóng pool control-plane (worker shutdown). */
    public synchronized void close() {
        if (controlPlaneDataSource != null) {
            controlPlaneDataSource.close();
            controlPlaneDataSource = null;
        }
    }
}