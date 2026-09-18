package com.td.worker.core.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerMetrics;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.checkpoint.CheckpointStore;
import com.td.worker.core.checkpoint.JdbcCheckpointStore;
import com.td.worker.core.checkpoint.UnitStatus;
import com.td.worker.core.dispatch.RetryPolicy;
import com.td.worker.core.dispatch.UnitDispatcher;
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

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test lõi multi-unit flow: 1 message với 2 unit (2 tenant khác) — 1 DONE, 1 fail->RETRY;
 * redelivery (run lần nữa) chỉ xử lý unit chưa DONE (idempotent, không xử lý lại unit thành công).
 */
class MessageFlowRunnerTest {

    private DataSource dataSource;
    private CheckpointStore store;
    private UnitDispatcher dispatcher;
    private MessageFlowRunner runner;
    private WorkerProperties props;
    private RecordingHandler handler;

    static final class RecordingHandler implements TaskHandler {
        final CopyOnWriteArrayList<String> executed = new CopyOnWriteArrayList<>();
        final AtomicInteger failUnit1 = new AtomicInteger(1);

        @Override
        public String taskType() {
            return "test.task";
        }

        @Override
        public UnitResult execute(TaskContext ctx, JsonNode payload) {
            executed.add(ctx.unitId());
            if ("u1".equals(ctx.unitId()) && failUnit1.getAndDecrement() > 0) {
                return UnitResult.failure("tạm lỗi supplier");
            }
            return UnitResult.success();
        }
    }

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:flow-test-" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUsername("sa");
        ds.setPassword("");
        this.dataSource = ds;
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/worker-schema-h2.sql"));
        populator.execute(dataSource);

        this.props = new WorkerProperties();
        props.getCore().setRetriesPerUnit(3);
        props.getCore().setRetryInitialBackoff(Duration.ZERO);
        props.getCore().setUnitTimeout(Duration.ofSeconds(10));
        props.getCore().setMessageTimeout(Duration.ofSeconds(15));
        props.getCore().setMaxInflightUnits(10);
        props.getCore().setAckPolicy(WorkerProperties.AckPolicy.ALL_ACCOUNTED);

        this.store = new JdbcCheckpointStore(dataSource, new ObjectMapper().findAndRegisterModules());
        this.handler = new RecordingHandler();
        TaskHandlerRegistry registry = new TaskHandlerRegistry(List.of(handler));
        RetryPolicy retryPolicy = new RetryPolicy(props);
        WorkerMetrics metrics = new WorkerMetrics(new SimpleMeterRegistry());
        this.dispatcher = new UnitDispatcher(props, store, registry, retryPolicy, metrics);
        dispatcher.start();
        this.runner = new MessageFlowRunner(store, dispatcher, retryPolicy, props, metrics,
            new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        dispatcher.stop();
    }

    private MessageEnvelope twoUnits() {
        ObjectMapper om = new ObjectMapper();
        ObjectNode p1 = om.createObjectNode().put("x", 1);
        ObjectNode p2 = om.createObjectNode().put("x", 2);
        return MessageEnvelope.builder()
            .messageId("msg-flow")
            .taskType("test.task")
            .correlationId("corr-flow")
            .source("test")
            .units(List.of(
                MessageUnit.builder().unitId("u1").tenantId("H05").payload(p1).build(),
                MessageUnit.builder().unitId("u2").tenantId("H06").payload(p2).build()))
            .build();
    }

    @Test
    void multiUnitFlowDoneAndRetryThenRedeliveryCompletesOnlyRemainingUnit() throws Exception {
        // Lượt 1: u1 fail (RETRY - đã lên lịch trong DB), u2 DONE. ALL_ACCOUNTED: cả 2 đã "accounted".
        FlowResult first = runner.run(twoUnits());
        assertThat(first.canAck()).isTrue(); // DONE + RETRY(đã lên lịch) đều accounted
        assertThat(first.statusCounts().get(UnitStatus.DONE)).isEqualTo(1L);
        assertThat(first.statusCounts().get(UnitStatus.RETRY)).isEqualTo(1L);
        assertThat(handler.executed).containsExactlyInAnyOrder("u1", "u2");

        // Redelivery/cùng message lần nữa (idempotent — init bỏ u2 DONE).
        handler.failUnit1.set(0); // u1 sẽ succeed lượt này
        Thread.sleep(50); // đảm bảo next_retry_at (backoff 0 + jitter ~1ms) đã đủ hạn
        FlowResult second = runner.run(twoUnits());
        assertThat(second.statusCounts().get(UnitStatus.DONE)).isEqualTo(2L);
        long u1Executions = count("u1");
        long u2Executions = count("u2");
        assertThat(u2Executions).isEqualTo(1L); // KHÔNG xử lý lại unit DONE
        assertThat(u1Executions).isEqualTo(2L); // chỉ u1 chạy lại
    }

    @Test
    void allTerminalAckPolicyRequiresEveryUnitTerminal() throws Exception {
        props.getCore().setAckPolicy(WorkerProperties.AckPolicy.ALL_TERMINAL);
        FlowResult first = runner.run(twoUnits());
        assertThat(first.canAck()).isFalse();
    }

    private long count(String unitId) {
        return handler.executed.stream().filter(x -> x.equals(unitId)).count();
    }
}