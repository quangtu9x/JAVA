package com.td.application.realtime;

/**
 * Chạy việc gửi thông báo trên luồng NỀN (implementation ở td-infrastructure: pool có giới hạn + chạy tại chỗ
 * khi quá tải). Cố ý KHÔNG dùng bean {@code java.util.concurrent.Executor}: Spring Boot chỉ tự tạo
 * {@code applicationTaskExecutor} khi chưa có bean Executor nào - thêm 1 bean như vậy sẽ âm thầm thay đổi
 * {@code @Async}/MVC async của cả ứng dụng.
 */
public interface RealtimeTaskDispatcher {

    void dispatch(Runnable task);
}
