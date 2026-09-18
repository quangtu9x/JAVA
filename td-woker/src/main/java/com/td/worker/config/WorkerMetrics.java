package com.td.worker.config;

import com.td.worker.core.checkpoint.UnitStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics Worker (Micrometer -> Prometheus qua /actuator/prometheus). Bổ sung các meter
 * Worker chuyên biệt bên cạnh meter hệ thống mặc định của Boot:
 * <ul>
 *   <li>{@code worker_messages_received_total} — message nhận từ broker.</li>
 *   <li>{@code worker_units_final_total{status,task_type}} — unit về trạng thái tận cùng.</li>
 *   <li>{@code worker_retry_due} — số unit RETRY đã tới hạn (queue backlog).</li>
 *   <li>{@code worker_unit_execution_seconds} — thời gian xử lý unit.</li>
 *   <li>{@code worker_inflight_units} / {@code worker_tenant_pools} — gauge.</li>
 * </ul>
 */
@Slf4j
@Component
public class WorkerMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> unitFinalCounters = new ConcurrentHashMap<>();
    private final Timer unitExecutionTimer;

    public final AtomicLong messagesReceived = new AtomicLong();
    public final AtomicLong messagesFailedParse = new AtomicLong();
    public final AtomicLong retryDue = new AtomicLong();
    public final AtomicLong inflightUnits = new AtomicLong();
    public final AtomicLong tenantPoolCount = new AtomicLong();

    public WorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.unitExecutionTimer = Timer.builder("worker_unit_execution_seconds")
            .description("Thời gian xử lý 1 unit (từ claim đến complete/fail)")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);
        registry.gauge("worker_messages_received_total", messagesReceived, AtomicLong::get);
        registry.gauge("worker_messages_failed_parse_total", messagesFailedParse, AtomicLong::get);
        registry.gauge("worker_retry_due", retryDue, AtomicLong::get);
        registry.gauge("worker_inflight_units", inflightUnits, AtomicLong::get);
        registry.gauge("worker_tenant_pools", tenantPoolCount, AtomicLong::get);
    }

    public void messageReceived() {
        messagesReceived.incrementAndGet();
    }

    public void messageParseFailed() {
        messagesFailedParse.incrementAndGet();
    }

    public void unitFinal(String taskType, UnitStatus status) {
        if (status != UnitStatus.DONE && status != UnitStatus.FAILED) {
            return;
        }
        String key = taskType + "|" + status;
        unitFinalCounters.computeIfAbsent(key, k -> Counter.builder("worker_units_final_total")
            .tag("task_type", taskType)
            .tag("status", status.name().toLowerCase())
            .register(registry)).increment();
    }

    public Timer.Sample startUnitTimer() {
        return Timer.start(registry);
    }

    public void stopUnitTimer(Timer.Sample sample, String taskType) {
        sample.stop(unitExecutionTimer);
    }

    public void recordRetryDuration(Duration duration) {
        registry.timer("worker_retry_poll_duration_seconds").record(duration);
    }
}