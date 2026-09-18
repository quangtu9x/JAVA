package com.td.worker.core.dispatch;

import com.td.worker.config.WorkerMetrics;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.checkpoint.CheckpointStore;
import com.td.worker.core.checkpoint.UnitRecord;
import com.td.worker.core.checkpoint.UnitStatus;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.TaskHandler;
import com.td.worker.core.task.TaskHandlerRegistry;
import com.td.worker.core.task.UnitResult;
import com.td.worker.infrastructure.tenant.WorkerTenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ điều phối unit: bounded executor + admission semaphore (backpressure), claim atomic có
 * lease (competing consumers an toàn khi nhiều Worker instance), timeout theo unit,
 * settlement complete/fail.
 *
 * <p>Điểm quan trọng về concurrency — docs/promt.txt mục Distributed Worker:
 * <ul>
 *   <li>Admission permit cấp TRƯỚC khi claim → không có 2 worker xử lý cùng unit khi lease
 *       chưa hết.</li>
 *   <li>Claim là UPDATE atomic có điều kiện → chỉ 1 worker thắng; unit DONE không xử lý lại.</li>
 *   <li>Executor queue giới hạn → backpressure về phía broker qua prefetch.</li>
 * </ul>
 */
@Slf4j
@Component
public class UnitDispatcher implements SmartLifecycle, DisposableBean {

    private final WorkerProperties props;
    private final CheckpointStore store;
    private final TaskHandlerRegistry registry;
    private final RetryPolicy retryPolicy;
    private final WorkerMetrics metrics;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object lifecycleLock = new Object();

    private volatile String workerId;
    private volatile ThreadPoolExecutor executor;
    private volatile Semaphore admission;

    public UnitDispatcher(WorkerProperties props, CheckpointStore store,
                          TaskHandlerRegistry registry, RetryPolicy retryPolicy,
                          WorkerMetrics metrics) {
        this.props = props;
        this.store = store;
        this.registry = registry;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (running.get()) {
                return;
            }
            this.workerId = props.resolveInstanceId();
            int max = Math.max(1, props.getCore().getMaxInflightUnits());
            ThreadFactory named = new NamedThreadFactory("worker-unit");
            this.executor = new ThreadPoolExecutor(
                max, max,
                60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(max),
                named,
                new ThreadPoolExecutor.AbortPolicy());
            this.admission = new Semaphore(max);
            this.running.set(true);
            log.info("UnitDispatcher khởi động: instanceId={}, pool={} thread, maxInflight={}",
                workerId, max, max);
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            if (!running.get()) {
                return;
            }
            running.set(false);
            log.info("Graceful shutdown UnitDispatcher: ~{} unit đang xử lý...", metrics.inflightUnits.get());
            executor.shutdown();
            try {
                long graceMs = props.getCore().getShutdownGrace().toMillis();
                if (!executor.awaitTermination(graceMs, TimeUnit.MILLISECONDS)) {
                    log.warn("Quá hạn graceful ({}) - hủy tác vụ còn lại; unit lease hết hạn sẽ do "
                            + "worker khác / retry-poller thực hiện tiếp", props.getCore().getShutdownGrace());
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
            log.info("UnitDispatcher đã dừng");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return 0; // pha thấp: start TRƯỚC listener (Rabbit/Kafka), stop SAU chúng
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void destroy() {
        stop();
    }

    public String workerId() {
        return workerId;
    }

    /**
     * Điều phối 1 unit (message gốc hoặc retry).
     *
     * @return empty = unit chưa điều phối lượt này (backpressure đầy — retry-poller xử lý sau);
     *     completed future = đã claim/settle xong ngay; future thật = đang xử lý.
     */
    public Optional<Future<UnitResult>> dispatch(String uniqueKey, TaskContext ctx, Instant dispatchDeadline) {
        if (!running.get()) {
            return Optional.empty();
        }
        Optional<TaskHandler> handlerOpt = registry.find(ctx.taskType());
        if (handlerOpt.isEmpty()) {
            // Task có thể được deploy ở version kế tiếp: giữ lại để tự nhận sau khi handler xuất hiện.
            Optional<UnitRecord> claimed = store.claim(uniqueKey, workerId, props.getCore().getClaimLeaseSeconds());
            claimed.ifPresent(rec -> {
                store.defer(uniqueKey, workerId,
                    "Không có TaskHandler cho taskType '" + ctx.taskType() + "'",
                    Instant.now().plus(props.getCore().getDependencyRetryInterval()));
                log.warn("Unit {} (task {}) WAITING_DEPENDENCY: thiếu TaskHandler", uniqueKey, ctx.taskType());
            });
            return Optional.of(CompletableFuture.completedFuture(UnitResult.failure("no handler for " + ctx.taskType())));
        }
        TaskHandler handler = handlerOpt.get();

        if (!tryAcquireAdmission()) {
            metrics.retryDue.incrementAndGet();
            log.warn("Backpressure: không lấy được admission trong hạn - unit {} bỏ qua lượt này", uniqueKey);
            return Optional.empty();
        }

        final Optional<UnitRecord> claimedOpt;
        try {
            claimedOpt = store.claim(uniqueKey, workerId, props.getCore().getClaimLeaseSeconds());
        } catch (RuntimeException e) {
            admission.release();
            log.error("Claim unit {} thất bại (lỗi cơ sở dữ liệu): {}", uniqueKey, e.getMessage());
            metrics.unitFinal(ctx.taskType(), UnitStatus.RETRY);
            return Optional.empty();
        }
        if (claimedOpt.isEmpty()) {
            admission.release();
            log.debug("Unit {} không claim được - do worker khác giữ hoặc đã DONE/FAILED", uniqueKey);
            return Optional.of(CompletableFuture.completedFuture(UnitResult.success()));
        }
        UnitRecord claimed = claimedOpt.get();

        final String worker = this.workerId;
        return Optional.of(executor.submit(() -> runUnit(handler, ctx, claimed, worker)));
    }

    private boolean tryAcquireAdmission() {
        try {
            // Chờ tối đa 5s - quá hạn coi là backpressure, nhường cho retry-poller.
            return admission.tryAcquire(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private UnitResult runUnit(TaskHandler handler, TaskContext ctx, UnitRecord claimed, String worker) {
        metrics.inflightUnits.incrementAndGet();
        var sample = metrics.startUnitTimer();
        try {
            UnitResult result = invokeHandler(handler, ctx);
            boolean finalAttempt = claimed.attempts() >= claimed.maxAttempts();
            if (result.isSuccess()) {
                store.complete(claimed.uniqueKey(), worker);
                metrics.unitFinal(ctx.taskType(), UnitStatus.DONE);
                log.info("Unit {} (task {}, tenant {}, attempt {}/{}) DONE",
                    claimed.unitId(), ctx.taskType(), ctx.tenantId(),
                    claimed.attempts(), claimed.maxAttempts());
            } else if (result.isWaitingDependency()) {
                Instant next = Instant.now().plus(props.getCore().getDependencyRetryInterval());
                store.defer(claimed.uniqueKey(), worker, result.error(), next);
                log.warn("Unit {} (task {}) WAITING_DEPENDENCY; kiểm tra lại lúc {}; error={}",
                    claimed.unitId(), ctx.taskType(), next, shorten(result.error()));
            } else {
                Instant next = retryPolicy.nextRetryAt(claimed.attempts());
                store.fail(claimed.uniqueKey(), worker, result.error(), next);
                UnitStatus status = finalAttempt ? UnitStatus.FAILED : UnitStatus.RETRY;
                metrics.unitFinal(ctx.taskType(), status);
                log.warn("Unit {} (task {}, tenant {}, attempt {}/{}) -> {}; error={}; nextRetryAt={}",
                    claimed.unitId(), ctx.taskType(), ctx.tenantId(),
                    claimed.attempts(), claimed.maxAttempts(), status, shorten(result.error()), next);
            }
            return result;
        } catch (RuntimeException e) {
            log.error("Lỗi nội bộ khi xử lý unit {}: {}", claimed.unitId(), e.getMessage(), e);
            return UnitResult.failure("internal: " + e.getMessage());
        } finally {
            metrics.stopUnitTimer(sample, ctx.taskType());
            metrics.inflightUnits.decrementAndGet();
            admission.release();
        }
    }

    private UnitResult invokeHandler(TaskHandler handler, TaskContext ctx) {
        try {
            return WorkerTenantContext.runAs(ctx.tenantId(), () -> handler.execute(ctx, ctx.payload()));
        } catch (com.td.worker.core.task.TaskDependencyException e) {
            return UnitResult.waitingDependency(e.getMessage());
        } catch (Throwable t) {
            return UnitResult.failure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static String shorten(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    /** Thread factory cho executor unit — tên thread có tiền tố worker-unit để dễ giám sát. */
    private static final class NamedThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger();
        private final String prefix;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.incrementAndGet());
            t.setDaemon(false);
            return t;
        }
    }
}
