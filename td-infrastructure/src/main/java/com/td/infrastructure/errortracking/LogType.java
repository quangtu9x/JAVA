package com.td.infrastructure.errortracking;

/**
 * Phân loại log để quản trị lọc/tra vết trên UI: {@link #BUSINESS} (exception phát sinh từ 1
 * request/nghiệp vụ cụ thể) vs {@link #SYSTEM} (sự cố hạ tầng/vận hành: request chậm, deadlock...
 * không nhất thiết gắn với lỗi nghiệp vụ nào).
 */
public enum LogType {
    /** Exception đi qua GlobalExceptionHandler hoặc security handler (401/403) — gắn với 1 request cụ thể. */
    BUSINESS,
    /** Sự cố hệ thống: request chậm ({@code SlowRequestLoggingFilter}), deadlock ({@code DeadlockWatchdog})... */
    SYSTEM
}
