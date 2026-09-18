package com.td.infrastructure.errortracking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Giám sát định kỳ phát hiện JVM deadlock (thread bị "treo" chờ nhau vô thời hạn, ví dụ 2 luồng
 * cùng khóa 2 tài nguyên theo thứ tự ngược nhau) — ghi 1 log {@link LogType#SYSTEM} mức
 * {@link Severity#CRITICAL} kèm thread dump khi phát hiện, giúp quản trị biết ngay hệ thống đang
 * "treo" thay vì phải chờ người dùng báo lỗi rồi mới đi tìm nguyên nhân.
 *
 * <p>Dùng {@link ThreadMXBean#findDeadlockedThreads()} (JMX chuẩn của JVM) — phát hiện được deadlock
 * kinh điển (monitor lock chờ nhau); KHÔNG phát hiện được "treo" do nguyên nhân khác (vd. tầng
 * ngoài — DB/API bên thứ ba phản hồi chậm nhưng không có lock nào bị giữ) — trường hợp đó được phủ
 * bởi {@code SlowRequestLoggingFilter} (ngưỡng CRITICAL cho request cực chậm).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeadlockWatchdog {

    private final ErrorTrackingService errorTrackingService;
    private final ErrorTrackingProperties errorTrackingProperties;

    @Scheduled(fixedDelayString = "${app.error-tracking.deadlock-watchdog.interval-ms:60000}")
    public void checkForDeadlocks() {
        if (!errorTrackingProperties.isEnabled() || !errorTrackingProperties.getDeadlockWatchdog().isEnabled()) {
            return;
        }

        try {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
            if (deadlockedThreadIds == null || deadlockedThreadIds.length == 0) {
                return;
            }

            ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(deadlockedThreadIds, true, true);
            String threadDump = Arrays.stream(threadInfos)
                .filter(java.util.Objects::nonNull)
                .map(ThreadInfo::toString)
                .collect(Collectors.joining("\n"));

            String message = "Phat hien " + deadlockedThreadIds.length
                + " thread bi deadlock - he thong co the dang bi treo, can kiem tra ngay";
            String errorId = errorTrackingService.captureSystemEvent(message, Severity.CRITICAL, threadDump);
            log.error("{} (Error ID: {})", message, errorId);
        } catch (Exception ex) {
            log.error("Deadlock watchdog gap loi khi kiem tra JVM thread", ex);
        }
    }
}
