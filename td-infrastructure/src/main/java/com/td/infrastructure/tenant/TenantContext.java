package com.td.infrastructure.tenant;

/**
 * Giữ tenant hiện tại của request đang xử lý trên thread này (BL-303).
 * Được {@code TenantFilter} set khi bắt đầu request và LUÔN clear khi kết thúc — bắt buộc vì
 * server dùng thread pool (Tomcat), không clear sẽ làm rò rỉ tenant sang request khác dùng lại
 * cùng thread.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
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

    /**
     * Yêu cầu người dùng 04/09/2026 (outbox dispatcher đa tenant, docs/architecture/11 §7.2/§9 mục
     * 3 — "TenantContext.runAs(...)" từng ghi nhận là CẦN BỔ SUNG): chạy {@code action} với tenant
     * hiện tại = {@code tenantId}, LUÔN clear ở {@code finally} — dùng cho code chạy NGOÀI vòng đời
     * HTTP request (không đi qua {@code TenantFilter}), vd {@code @Scheduled}/
     * {@code @RabbitListener}/{@code @KafkaListener} sau này. Khôi phục lại tenant TRƯỚC ĐÓ (nếu
     * có) sau khi xong, thay vì luôn clear về {@code null} — an toàn nếu lỡ gọi lồng nhau.
     */
    public static void runAs(String tenantId, Runnable action) {
        String previous = CURRENT_TENANT.get();
        CURRENT_TENANT.set(tenantId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT_TENANT.remove();
            } else {
                CURRENT_TENANT.set(previous);
            }
        }
    }

    /**
     * Bổ sung (OnlyOffice content/callback, permitAll — xem {@code OnlyOfficeController}): biến thể
     * trả về giá trị của {@link #runAs(String, Runnable)}, dùng khi request KHÔNG đi qua
     * {@code TenantFilter} theo đường bình thường (Document Server bên ngoài gọi thẳng, không mang
     * {@code Authorization}/{@code Tdtoken} người dùng) mà tenant được xác định qua 1 token nội bộ
     * riêng — cùng nguyên tắc runAs (luôn khôi phục tenant TRƯỚC ĐÓ ở finally, an toàn nếu lỡ gọi
     * lồng nhau).
     */
    public static <T> T runAsWithResult(String tenantId, java.util.function.Supplier<T> action) {
        String previous = CURRENT_TENANT.get();
        CURRENT_TENANT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT_TENANT.remove();
            } else {
                CURRENT_TENANT.set(previous);
            }
        }
    }
}
