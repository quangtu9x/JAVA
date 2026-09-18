package com.td.worker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * TaskScheduler cho {@code @Scheduled} (RetryPoller, TenantRegistry refresh...) — đúng tinh
 * thần "Thread/executor pool" của docs/promt.txt: pool riêng, kích thước cấu hình được, tên
 * thread có prefix rõ ràng để giám sát.
 */
@Configuration
public class WorkerSchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler(WorkerProperties props) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, props.getCore().getSchedulerPoolSize()));
        scheduler.setThreadNamePrefix("worker-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}