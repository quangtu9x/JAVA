package com.td.infrastructure.errortracking;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật cơ chế {@code @Scheduled} cho toàn app — hiện chỉ dùng bởi {@link ErrorLogCleanupScheduler}
 * (dọn log cũ hơn retention) và {@code DeadlockWatchdog} (giám sát deadlock định kỳ). Đặt riêng 1
 * config nhỏ (thay vì trên class {@code @SpringBootApplication}) để rõ ràng đây là hạ tầng phục vụ
 * Error Tracking, không ảnh hưởng phần còn lại của app nếu sau này cần tách rời.
 */
@Configuration
@EnableScheduling
public class ErrorTrackingSchedulingConfig {
}
