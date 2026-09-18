package com.td.application.messaging;

import java.util.Map;

/**
 * 1 unit độc lập bên trong message — khớp {@code MessageUnit} của {@code td-woker}
 * ({@code unitId, tenantId, payload, target, maxAttempts, deadline}), CHỈ giữ 2 field
 * ({@code maxAttempts}/{@code deadline}) làm optional vì producer hiếm khi cần override (Worker tự
 * dùng cấu hình mặc định nếu vắng).
 *
 * <p>{@code tenantId} là tenant ĐÍCH (quyết định Worker mở đúng pool DB nào xử lý unit này) —
 * KHÔNG nhất thiết là tenant hiện tại của producer (vd gửi văn bản liên đơn vị: unit chạy ở tenant
 * của ĐƠN VỊ NHẬN).</p>
 *
 * @param payload POJO/Map nghiệp vụ tuỳ task type — serialize JSON nguyên trạng, Worker tự
 *                deserialize theo đúng payload class của task đó (vd {@code VbdiDeliveryPayload}).
 */
public record MessageUnitSpec(
        String unitId,
        String tenantId,
        Object payload,
        Map<String, String> target) {

    public MessageUnitSpec(String unitId, String tenantId, Object payload) {
        this(unitId, tenantId, payload, Map.of());
    }
}
