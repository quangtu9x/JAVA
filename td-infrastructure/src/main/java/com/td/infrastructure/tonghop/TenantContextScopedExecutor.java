package com.td.infrastructure.tonghop;

import com.td.application.tonghop.TenantScopedExecutor;
import com.td.infrastructure.tenant.TenantActiveRegistry;
import com.td.infrastructure.tenant.TenantContext;
import com.td.infrastructure.tenant.TenantRoutingDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class TenantContextScopedExecutor implements TenantScopedExecutor {

    private final TenantRoutingDataSource tenantRoutingDataSource;
    private final TenantActiveRegistry tenantActiveRegistry;

    @Override
    public boolean isKnownTenant(String tenantId) {
        return tenantId != null && tenantRoutingDataSource.hasTenant(tenantId) && tenantActiveRegistry.isActive(tenantId);
    }

    @Override
    public <T> T runInTenant(String tenantId, Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Kết nối của transaction đang mở đã gắn tenant cũ — đổi TenantContext lúc này không có tác dụng.
            throw new IllegalStateException("runInTenant khong duoc goi ben trong transaction dang mo");
        }
        return TenantContext.runAsWithResult(tenantId, action);
    }
}
