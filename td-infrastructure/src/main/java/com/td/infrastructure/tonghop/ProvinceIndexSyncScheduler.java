package com.td.infrastructure.tonghop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lịch đồng bộ kho tổng hợp toàn tỉnh — xem {@link ProvinceIndexSyncService}. Tắt bằng
 * {@code PROVINCE_SYNC_ENABLED=false} (vd môi trường dev không cần).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.province.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProvinceIndexSyncScheduler {

    private final ProvinceIndexSyncService provinceIndexSyncService;

    @Scheduled(initialDelayString = "${app.province.sync.initial-delay-ms:60000}",
        fixedDelayString = "${app.province.sync.interval-ms:300000}")
    public void incremental() {
        run(false);
    }

    @Scheduled(cron = "${app.province.sync.full-cron:0 30 1 * * *}")
    public void full() {
        run(true);
    }

    private void run(boolean full) {
        try {
            provinceIndexSyncService.run(full);
        } catch (RuntimeException ex) {
            log.error("Dong bo tong hop toan tinh ({}) that bai - se thu lai o chu ky ke tiep", full ? "toan bo" : "tang dan", ex);
        }
    }
}
