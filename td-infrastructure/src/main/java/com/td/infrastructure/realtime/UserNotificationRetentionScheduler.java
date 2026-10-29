package com.td.infrastructure.realtime;

import com.td.application.realtime.UserNotificationRepository;
import com.td.infrastructure.tenant.TenantActiveRegistry;
import com.td.infrastructure.tenant.TenantContext;
import com.td.infrastructure.tenant.TenantRoutingDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Dọn hộp thư thông báo hằng đêm, lần lượt từng DB đơn vị (cùng cách {@code OutboxDispatcherScheduler}): xoá
 * mềm thông báo ĐÃ ĐỌC quá {@code retention-read-days} (mặc định 180) hoặc MỌI thông báo quá
 * {@code retention-all-days} (mặc định 365). Index chỉ trên dòng chưa xoá nên truy vấn hộp thư luôn nhỏ.
 *
 * <p>Theo lô {@code batch-size} để mỗi câu UPDATE ngắn (không khoá lâu, WAL nhỏ). Idempotent - nhiều instance
 * cùng chạy chỉ là việc thừa, không sai dữ liệu. {@code retention-read-days <= 0} = tắt job.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserNotificationRetentionScheduler {

    private static final int MAX_BATCHES_PER_TENANT = 200;

    private final TenantRoutingDataSource tenantRoutingDataSource;
    private final TenantActiveRegistry tenantActiveRegistry;
    private final UserNotificationRepository userNotificationRepository;

    @Value("${app.realtime.inbox.retention-read-days:180}")
    private int retentionReadDays;

    @Value("${app.realtime.inbox.retention-all-days:365}")
    private int retentionAllDays;

    @Value("${app.realtime.inbox.cleanup-batch-size:5000}")
    private int batchSize;

    @Scheduled(cron = "${app.realtime.inbox.cleanup-cron:0 30 2 * * *}")
    public void purgeAllTenants() {
        if (retentionReadDays <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime readBefore = now.minusDays(retentionReadDays);
        LocalDateTime anyBefore = now.minusDays(Math.max(retentionReadDays, retentionAllDays));
        int limit = Math.max(100, batchSize);
        for (String tenantId : tenantRoutingDataSource.knownTenantIds()) {
            if (!tenantActiveRegistry.isActive(tenantId)) {
                continue;
            }
            try {
                int total = TenantContext.runAsWithResult(tenantId, () -> purgeTenant(readBefore, anyBefore, limit));
                if (total > 0) {
                    log.info("Dọn hộp thư thông báo tenant '{}': {} dòng", tenantId, total);
                }
            } catch (Exception ex) {
                // 1 tenant lỗi (vd chưa migrate V1.0.78) không làm hỏng các tenant còn lại.
                log.warn("Dọn hộp thư thông báo tenant '{}' lỗi: {}", tenantId, ex.getMessage());
            }
        }
    }

    int purgeTenant(LocalDateTime readBefore, LocalDateTime anyBefore, int limit) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_TENANT; batch++) {
            int deleted = userNotificationRepository.softDeleteExpired(readBefore, anyBefore, limit);
            total += deleted;
            if (deleted < limit) {
                break;
            }
        }
        return total;
    }
}
