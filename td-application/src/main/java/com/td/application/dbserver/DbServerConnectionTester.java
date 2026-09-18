package com.td.application.dbserver;

/**
 * Cổng (port) mở thử 1 connection JDBC thô tới máy chủ Postgres (BL-117, bổ sung 19/08/2026) —
 * implement ở {@code td-infrastructure} ({@code PostgresDbServerConnectionTester}). Dùng database
 * "postgres" mặc định (maintenance DB, luôn tồn tại trên mọi server Postgres) vì DbServer chỉ đại
 * diện 1 SERVER (host+port), không gắn 1 database cụ thể như Tenant.
 */
public interface DbServerConnectionTester {

    /** Ném exception nếu kết nối thất bại. Không thay đổi state gì. */
    void testConnection(String host, int port, String username, String password);
}
