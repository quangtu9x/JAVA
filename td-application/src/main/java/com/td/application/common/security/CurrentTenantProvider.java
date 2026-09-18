package com.td.application.common.security;

import java.util.Optional;

/**
 * Yêu cầu người dùng 04/09/2026 ("hoàn thiện Gửi văn bản... publish RabbitMQ/Kafka"): cổng (port)
 * lấy id tenant hiện tại (đơn vị đang xử lý request) — dùng cho envelope {@code senderTenantId}
 * (xem {@code docs/architecture/11 §4.1}) mà không để {@code td-application} phụ thuộc ngược vào
 * {@code td-infrastructure} (nơi thực sự đọc {@code TenantContext}) — cùng nguyên tắc
 * {@link CurrentUserProvider}.
 */
public interface CurrentTenantProvider {

    Optional<String> currentTenantId();
}
