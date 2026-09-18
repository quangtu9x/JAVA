package com.td.infrastructure.tenant;

import com.td.application.common.security.CurrentTenantProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adapter đọc {@link TenantContext} (ThreadLocal, set bởi {@code TenantFilter} cho request, hoặc
 * {@link TenantContext#runAs} cho code chạy ngoài request — vd {@code OutboxDispatcherScheduler}).
 */
@Component
public class TenantContextCurrentTenantProvider implements CurrentTenantProvider {

    @Override
    public Optional<String> currentTenantId() {
        return Optional.ofNullable(TenantContext.getCurrentTenant());
    }
}
