package com.td.application.tenant;

import com.td.domain.tenant.Tenant;

/**
 * Cổng (port) cho toàn bộ thao tác hạ tầng liên quan tới tenant mà {@code td-application} không
 * được phép biết chi tiết (mở JDBC connection thô, CREATE DATABASE, chạy Flyway, quản lý connection
 * pool routing, mã hoá mật khẩu) — implement ở {@code td-infrastructure}
 * ({@code PostgresTenantProvisioningService}, BL-117).
 */
public interface TenantProvisioningService {

    /**
     * Chuẩn bị 1 tenant sẵn sàng dùng được theo {@link TenantProvisionMode} của {@code command}, rồi
     * hot-add ngay 1 connection pool cho tenant này vào routing hiện tại (dùng được ngay, không cần
     * restart app).
     *
     * @return mật khẩu đã MÃ HOÁ 2 chiều (sẵn sàng lưu vào cột {@code encrypted_password}) — KHÔNG
     *         bao giờ trả plaintext ra khỏi method này.
     */
    String provision(ProvisionTenantCommand command);

    /** Mở thử 1 connection tới host/port/database/username/password trong {@code command}, ném exception nếu thất bại. Không thay đổi state gì. */
    void testConnection(ProvisionTenantCommand command);

    /** Đảm bảo tenant đã có 1 connection pool đang active trong routing hiện tại — hot-add nếu chưa có (giải mã mật khẩu từ chính {@code tenant.getEncryptedPassword()}), no-op nếu đã có. */
    void ensurePoolRegistered(Tenant tenant);

    /** Đánh dấu tenant ACTIVE trong registry runtime (TenantResolver dùng để quyết định chấp nhận/từ chối request). */
    void markActive(String tenantId);

    /** Đánh dấu tenant KHÔNG active — mọi request tiếp theo của tenant này bị TỪ CHỐI (không fallback về tenant mặc định, tránh rò rỉ dữ liệu chéo tenant). */
    void markInactive(String tenantId);
}
