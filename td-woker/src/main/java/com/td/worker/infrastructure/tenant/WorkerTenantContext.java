package com.td.worker.infrastructure.tenant;

/**
 * Giữ tenant hiện tại trên thread đang xử lý unit (áp dụng cùng pattern
 * {@code TenantContext}/{@code TenantFilter} của td-infrastructure tham khảo).
 *
 * <p>Worker xử lý message trên thread của message listener + executor — KHÔNG có HTTP request
 * như td-web, nên mọi handler phải chạy TRONG {@link #runAs} để lập tenant đúng
 * (xem docs/architecture/11 §9 "TenantContext.runAs" của project tham khảo).</p>
 */
public final class WorkerTenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private WorkerTenantContext() {
    }

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /** Chạy action trong tenant chỉ định, sau đó LUÔN clear (tránh rò rỉ tenant giữa unit). */
    public static void runAs(String tenantId, Runnable action) {
        setCurrentTenant(tenantId);
        try {
            action.run();
        } finally {
            clear();
        }
    }

    /** Chạy action trả về giá trị trong tenant chỉ định. */
    public static <T> T runAs(String tenantId, java.util.concurrent.Callable<T> action) {
        setCurrentTenant(tenantId);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi khi chạy action trong tenant " + tenantId, e);
        } finally {
            clear();
        }
    }
}