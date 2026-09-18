package com.td.infrastructure.errortracking;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình DB Error Tracking — HOÀN TOÀN TÁCH BIỆT khỏi {@code spring.datasource.*} (DB nghiệp vụ).
 * Mặc định trỏ tới 1 database Postgres khác (không phải schema khác của cùng DB nghiệp vụ như cơ chế
 * multi-tenant hiện có ở {@code TenantDataSourceConfig}) — xem {@link ErrorTrackingDataSourceConfig}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.error-tracking")
public class ErrorTrackingProperties {

    /**
     * Công tắc tắt toàn bộ tính năng Error Tracking (migrate schema + ghi log lỗi + cleanup) mà
     * không cần đổi code — dùng khi DB log tạm thời không sẵn sàng, đảm bảo KHÔNG ảnh hưởng API nghiệp vụ.
     */
    private boolean enabled = true;

    /** Số ngày giữ log — bản ghi cũ hơn bị {@link ErrorLogCleanupScheduler} tự động xóa. */
    private int retentionDays = 30;

    /** Cron chạy dọn dẹp log cũ (mặc định 3h sáng hàng ngày — giờ thấp điểm). */
    private String cleanupCron = "0 0 3 * * *";

    private Datasource datasource = new Datasource();

    private SlowRequest slowRequest = new SlowRequest();

    private DeadlockWatchdog deadlockWatchdog = new DeadlockWatchdog();

    @Data
    public static class Datasource {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "org.postgresql.Driver";
    }

    /** Ngưỡng phát hiện request chậm — ghi log SYSTEM khi vượt {@code warnThresholdMs}. */
    @Data
    public static class SlowRequest {
        private boolean enabled = true;
        /** Vượt ngưỡng này → log SYSTEM mức WARN ("chậm"). */
        private long warnThresholdMs = 3000;
        /** Vượt ngưỡng này → log SYSTEM mức CRITICAL (gần như "treo"). */
        private long criticalThresholdMs = 10000;
    }

    /** Watchdog định kỳ kiểm tra deadlock (JVM thread bị treo chờ nhau vô thời hạn). */
    @Data
    public static class DeadlockWatchdog {
        private boolean enabled = true;
        private long intervalMs = 60_000;
    }
}
