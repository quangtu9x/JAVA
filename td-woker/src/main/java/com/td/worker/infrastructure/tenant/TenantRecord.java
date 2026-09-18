package com.td.worker.infrastructure.tenant;

/**
 * 1 dòng trong bảng {@code tenants} (database control-plane) — registry tenant
 * database-per-tenant (docs/architecture/06). Worker CHỈ ĐỌC bảng này để biết danh sách
 * tenant + credential khi cần mở connection pool tới database tenant.
 */
public record TenantRecord(
        String tenantId,
        String displayName,
        String host,
        int port,
        String databaseName,
        String username,
        String encryptedPassword,
        String driverClassName,
        boolean active) {

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
    }
}