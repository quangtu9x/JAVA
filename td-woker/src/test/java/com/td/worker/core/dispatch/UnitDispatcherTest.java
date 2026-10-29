package com.td.worker.core.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerMetrics;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.checkpoint.JdbcCheckpointStore;
import com.td.worker.core.checkpoint.UnitStatus;
import com.td.worker.core.envelope.MessageEnvelope;
import com.td.worker.core.envelope.MessageUnit;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.TaskHandler;
import com.td.worker.core.task.TaskHandlerRegistry;
import com.td.worker.core.task.UnitResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 23/10/2026 — chạy nhiều máy chủ không xử lý kép: heartbeat gia hạn lease khi handler chạy lâu hơn
 * lease; watchdog interrupt khi quá unit-timeout nhưng GIỮ claim tới khi handler thực sự thoát.
 */
class UnitDispatcherTest {

    private JdbcCheckpointStore store;
    private UnitDispatcher dispatcher;
    private WorkerProperties props;
    private Function<TaskContext, UnitResult> behaviour;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:dispatcher-test-" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUsername("sa");
        ds.setPassword("");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/worker-schema-h2.sql"));
        populator.execute(ds);

        props = new WorkerProperties();
        props.getCore().setMaxInflightUnits(4);
        props.getCore().setClaimLeaseSeconds(2);
        props.getCore().setLeaseRenewInterval(Duration.ofMillis(300));
        props.getCore().setUnitTimeout(Duration.ofSeconds(30));
        props.getCore().setRetryInitialBackoff(Duration.ofSeconds(30));

        store = new JdbcCheckpointStore(ds, new ObjectMapper().findAndRegisterModules());
        TaskHandler handler = new TaskHandler() {
            @Override
            public String taskType() {
                return "test.slow";
            }

            @Override
            public UnitResult execute(TaskContext ctx, JsonNode payload) {
                return behaviour.apply(ctx);
            }
        };
        dispatcher = new UnitDispatcher(props, store, new TaskHandlerRegistry(List.of(handler)),
            new RetryPolicy(props), new WorkerMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void tearDown() {
        dispatcher.stop();
    }

    private TaskContext init(String unitId) {
        ObjectNode payload = new ObjectMapper().createObjectNode().put("x", 1);
        store.initializeUnits(MessageEnvelope.builder()
            .messageId("m1").taskType("test.slow").correlationId("c1").source("test")
            .units(List.of(MessageUnit.builder().unitId(unitId).tenantId("H05").payload(payload).maxAttempts(3).build()))
            .build());
        return new TaskContext("test.slow", "m1", unitId, "c1", "H05", payload, Map.of(), 3, null, Map.of());
    }

    @Test
    void longRunningHandlerKeepsLeaseSoOtherWorkerCannotClaim() throws Exception {
        dispatcher.start();
        CountDownLatch release = new CountDownLatch(1);
        behaviour = ctx -> {
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return UnitResult.success();
        };
        TaskContext ctx = init("u1");
        String key = "H05|m1|u1";
        var future = dispatcher.dispatch(key, ctx, null).orElseThrow();

        Thread.sleep(3500); // > lease 2s: khong gia han thi worker khac da gianh duoc
        assertThat(store.claim(key, "worker-other", 120)).isEmpty();
        assertThat(store.requeueExpiredLeases(Duration.ZERO, 10)).isZero();

        release.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS).isSuccess()).isTrue();
        assertThat(store.findByUniqueKey(key).orElseThrow().status()).isEqualTo(UnitStatus.DONE);
    }

    @Test
    void unitTimeoutInterruptsHandlerButKeepsClaimUntilHandlerExits() throws Exception {
        props.getCore().setUnitTimeout(Duration.ofSeconds(1));
        dispatcher.start();
        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch interruptedSeen = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        behaviour = ctx -> {
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                interruptedSeen.countDown();
            }
            // Mo phong handler "khong nghe" interrupt: con lam tiep 1 luc roi moi thoat.
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // khong dung den
            }
            return UnitResult.failure("bi interrupt do qua unit-timeout");
        };
        TaskContext ctx = init("u2");
        String key = "H05|m1|u2";
        var future = dispatcher.dispatch(key, ctx, null).orElseThrow();

        assertThat(interruptedSeen.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted.get()).isTrue();
        Thread.sleep(2500); // qua lease ban dau: van PROCESSING, khong ai gianh duoc
        assertThat(store.findByUniqueKey(key).orElseThrow().status()).isEqualTo(UnitStatus.PROCESSING);
        assertThat(store.claim(key, "worker-other", 120)).isEmpty();

        release.countDown();
        assertThat(future.get(10, TimeUnit.SECONDS).isSuccess()).isFalse();
        assertThat(store.findByUniqueKey(key).orElseThrow().status()).isEqualTo(UnitStatus.RETRY);
    }

    @Test
    void instanceIdIsUniquePerProcessAndStable() {
        WorkerProperties a = new WorkerProperties();
        WorkerProperties b = new WorkerProperties();
        a.setInstanceId("worker-1");
        b.setInstanceId("worker-1");
        assertThat(a.resolveInstanceId()).startsWith("worker-1-").isEqualTo(a.resolveInstanceId());
        assertThat(a.resolveInstanceId()).isNotEqualTo(b.resolveInstanceId());
        WorkerProperties blank = new WorkerProperties();
        blank.setInstanceId("");
        assertThat(blank.resolveInstanceId()).isEqualTo(blank.resolveInstanceId()).isNotBlank();
    }
}
