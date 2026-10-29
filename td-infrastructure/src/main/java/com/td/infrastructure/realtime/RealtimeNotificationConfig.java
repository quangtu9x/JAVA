package com.td.infrastructure.realtime;

import com.td.application.realtime.RealtimeTaskDispatcher;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Luồng NỀN cho {@code RealtimeNotifier}: ghi hộp thư (có thể sang DB đơn vị người nhận) + đẩy socket chạy ngoài
 * luồng request -> thao tác nghiệp vụ không chờ thêm INSERT/HTTP.
 *
 * <p>Hàng đợi có giới hạn + {@code CallerRunsPolicy}: quá tải (vd phát hành tới hàng trăm đơn vị liên tục) thì
 * luồng gọi tự chạy thay - chậm lại chứ KHÔNG bỏ thông báo, không phình bộ nhớ. Tắt ứng dụng chờ tối đa 15 giây
 * cho việc đang dở. Pool KHÔNG đăng ký làm bean Executor (xem {@link RealtimeTaskDispatcher}).</p>
 */
@Configuration
@EnableScheduling
public class RealtimeNotificationConfig {

    @Component
    static class PooledRealtimeTaskDispatcher implements RealtimeTaskDispatcher, DisposableBean {

        private final ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();

        PooledRealtimeTaskDispatcher(
                @Value("${app.realtime.executor.core-size:2}") int coreSize,
                @Value("${app.realtime.executor.max-size:4}") int maxSize,
                @Value("${app.realtime.executor.queue-capacity:2000}") int queueCapacity) {
            pool.setThreadNamePrefix("realtime-notify-");
            pool.setCorePoolSize(Math.max(1, coreSize));
            pool.setMaxPoolSize(Math.max(Math.max(1, coreSize), maxSize));
            pool.setQueueCapacity(Math.max(0, queueCapacity));
            pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
            pool.setWaitForTasksToCompleteOnShutdown(true);
            pool.setAwaitTerminationSeconds(15);
            pool.initialize();
        }

        @Override
        public void dispatch(Runnable task) {
            pool.execute(task);
        }

        @Override
        public void destroy() {
            pool.shutdown();
        }
    }
}
