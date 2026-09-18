package com.td.worker.config;

import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.infrastructure.tenant.TenantRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Shutdown hook cho Worker: đóng connection pool tenant + pool control-plane theo thứ tự
 * ngược với vòng đời. Graceful shutdown — các unit đang xử lý được drain bởi UnitDispatcher
 * (SmartLifecycle stop trước khi bean này đóng pool).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerShutdownHooks {

    private final TenantDataSourceManager tenantDataSourceManager;
    private final TenantRegistry tenantRegistry;

    @PreDestroy
    public void shutdown() {
        log.info("Worker shutdown: đóng connection pool tenant...");
        tenantDataSourceManager.closeAll();
        tenantRegistry.close();
        log.info("Worker shutdown hoàn tất");
    }
}