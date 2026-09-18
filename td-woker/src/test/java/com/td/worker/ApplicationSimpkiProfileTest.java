package com.td.worker;

import com.td.worker.config.WorkerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Xác nhận profile {@code application-simpki.yml} (docs/18-trien-khai-test-simpki.md) override
 * ĐÚNG {@code worker.rabbit.queues} (kiểu List — KHÔNG override được qua biến môi trường đơn lẻ,
 * phải khai báo lại toàn bộ list ở property source ưu tiên cao hơn) + `unit-timeout`/`vbdi.enabled`/
 * `vbden.enabled`. Chỉ bind {@link WorkerProperties} qua {@code @EnableConfigurationProperties} trên
 * 1 config rỗng — KHÔNG khởi động {@code WorkerApplication} thật (tránh phụ thuộc DataSource/Flyway/
 * RabbitMQ thật, giữ test 100% offline).
 */
class ApplicationSimpkiProfileTest {

    @Configuration
    @EnableConfigurationProperties(WorkerProperties.class)
    static class MinimalConfig {
    }

    @Test
    void simpkiProfileOverridesRabbitQueuesAndTimeouts() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(MinimalConfig.class)
                .web(WebApplicationType.NONE)
                .profiles("simpki")
                .run()) {
            WorkerProperties props = ctx.getBean(WorkerProperties.class);

            assertThat(props.getRabbit().getQueues()).hasSize(1);
            WorkerProperties.QueueSpec queue = props.getRabbit().getQueues().get(0);
            assertThat(queue.getName()).isEqualTo("worker.simpki");
            assertThat(queue.getTaskType()).isEqualTo("simpki.sign");
            assertThat(queue.getConcurrency()).isEqualTo(2);
            assertThat(queue.getPrefetch()).isEqualTo(2);

            assertThat(props.getCore().getUnitTimeout()).isEqualTo(Duration.ofSeconds(330));
            assertThat(props.getCore().getClaimLeaseSeconds()).isEqualTo(360);

            assertThat(props.getTasks().getVbdi().isEnabled()).isFalse();
            assertThat(props.getTasks().getVbden().isEnabled()).isFalse();
        }
    }
}
