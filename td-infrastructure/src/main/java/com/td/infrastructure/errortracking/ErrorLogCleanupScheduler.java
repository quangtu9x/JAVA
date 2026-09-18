package com.td.infrastructure.errortracking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Tự động dọn Error Tracking log cũ hơn {@code app.error-tracking.retention-days} (mặc định 30
 * ngày) — tránh DB Error Tracking phình to vô hạn theo thời gian; log chẩn đoán không cần giữ lâu
 * như dữ liệu nghiệp vụ.
 *
 * <p>Chạy theo cron {@code app.error-tracking.cleanup-cron} (mặc định 3h sáng hàng ngày). Xóa hàng
 * loạt bằng 1 câu lệnh bulk delete ({@link ErrorLogJdbcRepository#deleteByOccurredAtBefore}), không
 * load entity vào bộ nhớ — an toàn cho bảng lớn.</p>
 *
 * <p>Inject {@link ErrorLogJdbcRepository} (JDBC thuần) trực tiếp — an toàn cho việc khởi động app
 * dù DB Error Tracking chưa sẵn sàng, vì bean này chỉ giữ tham chiếu {@code JdbcTemplate}, không
 * chạm DB lúc tạo (xem javadoc {@code ErrorTrackingDataSourceConfig}/{@code ErrorTrackingService}).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ErrorLogCleanupScheduler {

    private final ErrorLogJdbcRepository errorLogRepository;
    private final ErrorTrackingProperties errorTrackingProperties;

    @Scheduled(cron = "${app.error-tracking.cleanup-cron:0 0 3 * * *}")
    public void cleanupOldLogs() {
        if (!errorTrackingProperties.isEnabled()) {
            log.debug("Error Tracking dang tat - bo qua cleanup");
            return;
        }

        int retentionDays = errorTrackingProperties.getRetentionDays();
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        try {
            int deletedCount = errorLogRepository.deleteByOccurredAtBefore(cutoff);
            log.info("Error Tracking cleanup: da xoa {} ban ghi cu hon {} ngay (truoc {})",
                deletedCount, retentionDays, cutoff);
        } catch (Exception ex) {
            // Khong duoc de cleanup that bai anh huong app chinh - se thu lai o lan chay ke tiep.
            log.error("Error Tracking cleanup that bai - se thu lai o lan chay ke tiep", ex);
        }
    }
}
