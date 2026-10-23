package com.td.worker.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Expose các khoảng thời gian lịch biểu dưới dạng bean để @Scheduled SpEL tham chiếu —
 * tránh tự tham chiếu bean (gây vòng lặp) và tránh phụ thuộc tên bean của
 * {@link WorkerProperties} (được đăng ký dưới FQCN bởi @EnableConfigurationProperties).
 */
@Component
@RequiredArgsConstructor
public class WorkerScheduleIntervals {

    private final WorkerProperties props;

    /** Chu kỳ retry-poller (ms). */
    public long retryPollIntervalMs() {
        return props.getCore().getRetryPollInterval().toMillis();
    }

    /** Chu kỳ refresh registry tenant (ms). */
    public long controlPlaneRefreshIntervalMs() {
        return props.getControlPlane().getRefreshInterval().toMillis();
    }

    /** Cron job dọn dẹp tệp đính kèm dùng chung hết tham chiếu. */
    public String fileRegistryGcCron() {
        return props.getFileRegistry().getGcCron();
    }
}