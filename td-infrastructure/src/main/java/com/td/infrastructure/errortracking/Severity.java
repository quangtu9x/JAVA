package com.td.infrastructure.errortracking;

/**
 * Mức độ nghiêm trọng — dùng để quản trị lọc/ưu tiên xử lý trên UI.
 */
public enum Severity {
    /** Lỗi client (4xx thông thường) hoặc request chậm vượt ngưỡng cảnh báo. */
    WARN,
    /** Lỗi server (5xx) — mặc định cho mọi exception không xác định rõ mức độ. */
    ERROR,
    /** Sự cố nghiêm trọng: deadlock/treo hệ thống, request cực chậm vượt ngưỡng nguy cấp. */
    CRITICAL
}
