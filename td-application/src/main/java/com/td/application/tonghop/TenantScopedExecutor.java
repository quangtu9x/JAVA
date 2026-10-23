package com.td.application.tonghop;

import java.util.function.Supplier;

/**
 * Chạy 1 thao tác nghiệp vụ trong ngữ cảnh tenant KHÁC tenant của request hiện tại (vd quản trị tỉnh
 * "Gửi lại" 1 văn bản đi thuộc DB của Sở). Hàm truyền vào phải tự mở transaction của nó (use case
 * {@code @Transactional}) — không được gọi khi đang có transaction mở (kết nối đã gắn tenant cũ).
 */
public interface TenantScopedExecutor {

    boolean isKnownTenant(String tenantId);

    <T> T runInTenant(String tenantId, Supplier<T> action);
}
