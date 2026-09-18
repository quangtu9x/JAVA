package com.td.worker;

import com.td.worker.core.checkpoint.CheckpointStore;
import com.td.worker.core.dispatch.UnitDispatcher;
import com.td.worker.core.flow.MessageFlowRunner;
import com.td.worker.core.task.TaskHandlerRegistry;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Load toàn bộ Spring context của Worker (H2, broker off) — xác nhận wiring bean
 * (Core + infrastructure + transport conditional) không lỗi khi khởi động độc lập.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkerApplicationContextTest {

    @Autowired
    private CheckpointStore store;

    @Autowired
    private UnitDispatcher dispatcher;

    @Autowired
    private MessageFlowRunner flowRunner;

    @Autowired
    private TaskHandlerRegistry registry;

    @Autowired
    private TenantDataSourceManager tenantDataSourceManager;

    @Test
    void contextLoadsAndCoreBeansPresent() {
        assertThat(store).isNotNull();
        assertThat(dispatcher).isNotNull();
        assertThat(flowRunner).isNotNull();
        assertThat(registry).isNotNull();
        assertThat(tenantDataSourceManager).isNotNull();
        assertThat(registry.find("sample.echo")).isPresent();
        assertThat(registry.find("vbdi.delivery")).isPresent();
        assertThat(registry.find("vbdi.reply")).isPresent();
    }
}