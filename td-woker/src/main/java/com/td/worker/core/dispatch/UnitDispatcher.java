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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
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
 *
 * <p><b>23/10/2026 — chạy nhiều máy chủ không xử lý kép</b>: trước đây lease cố định 120s không gia
 * hạn, còn timeout unit chỉ {@code cancel(true)} Future rồi trả unit về RETRY trong khi thread handler
 * (đang chờ JDBC/HTTP, không nhận interrupt) VẪN chạy ⇒ máy khác nhận lại và chạy song song. Nay:
 * <ul>
 *   <li><b>Heartbeat</b>: unit đang chạy được gia hạn lease mỗi {@code lease-renew-interval} — lease
 *       chỉ hết khi tiến trình chết thật.</li>
 *   <li><b>Watchdog timeout</b> áp cho MỌI đường (broker + RetryPoller): quá {@code unit-timeout} thì
 *       interrupt thread; unit vẫn PROCESSING tới khi handler THỰC SỰ thoát rồi mới settle — không nhả
 *       claim sớm. Quá {@code max-processing-time} (thread treo, bỏ qua interrupt) thì ngừng gia hạn để
 *       lease hết hạn, máy khác nhận lại.</li>
 *   <li>Kết quả {@code complete/fail/defer} được kiểm tra — mất claim thì log cảnh báo, không báo DONE.</li>
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
    private volatile ScheduledExecutorService leaseKeeper;

    /** Unit đang chạy trên instance này (uniqueKey → thông tin) — nguồn cho heartbeat + watchdog. */
    private final ConcurrentHashMap<String, InFlightUnit> inFlight = new ConcurrentHashMap<>();

    private record InFlightUnit(String uniqueKey, String unitId, String taskType, Thread thread,
                                Instant softDeadline, Instant hardDeadline,
                                AtomicBoolean interrupted, AtomicBoolean abandoned, AtomicBoolean finished) {
    }

    /** Interrupt chỉ khi handler CHƯA thoát — khoá cùng monitor với bước đánh dấu finished ở runUnit. */
    private static void interruptIfRunning(InFlightUnit u) {
        synchronized (u) {
            if (!u.finished().get()) {
                u.thread().interrupt();
            }
        }
    }

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
            this.leaseKeeper = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("worker-lease"));
            long renewMs = leaseRenewInterval().toMillis();
            leaseKeeper.scheduleWithFixedDelay(this::safeRenewLeases, renewMs, renewMs, TimeUnit.MILLISECONDS);
            leaseKeeper.scheduleWithFixedDelay(this::safeEnforceTimeouts, 1, 1, TimeUnit.SECONDS);
            this.running.set(true);
            log.info("UnitDispatcher khởi động: instanceId={}, pool={} thread, maxInflight={}, lease={}s "
                    + "(gia hạn mỗi {}ms), unitTimeout={}, maxProcessingTime={}",
                workerId, max, max, props.getCore().getClaimLeaseSeconds(), renewMs,
                props.getCore().getUnitTimeout(), props.getCore().getMaxProcessingTime());
        }
    }

    private Duration leaseRenewInterval() {
        Duration configured = props.getCore().getLeaseRenewInterval();
        int leaseSeconds = Math.max(1, props.getCore().getClaimLeaseSeconds());
        Duration fallback = Duration.ofMillis(Math.max(1000L, leaseSeconds * 1000L / 3));
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return fallback;
        }
        // Gia hạn thưa hơn lease thì lease hết trước khi kịp gia hạn - ép về mặc định an toàn.
        return configured.toSeconds() >= leaseSeconds ? fallback : configured;
    }

    private void safeRenewLeases() {
        try {
            renewLeases();
        } catch (RuntimeException e) {
            log.warn("Gia hạn lease thất bại lượt này (sẽ thử lại): {}", e.getMessage());
        }
    }

    private void safeEnforceTimeouts() {
        try {
            enforceTimeouts(Instant.now());
        } catch (RuntimeException e) {
            log.warn("Watchdog timeout lỗi lượt này: {}", e.getMessage());
        }
    }

    /** Heartbeat: gia hạn lease cho mọi unit đang chạy chưa vượt trần {@code max-processing-time}. */
    void renewLeases() {
        List<String> keys = new ArrayList<>();
        for (InFlightUnit u : inFlight.values()) {
            if (!u.abandoned().get()) {
                keys.add(u.uniqueKey());
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        List<String> lost = store.renewLeases(workerId, keys, props.getCore().getClaimLeaseSeconds());
        for (String key : lost) {
            InFlightUnit u = inFlight.get(key);
            if (u != null) {
                log.warn("Unit {} (task {}) đã MẤT claim trong lúc đang xử lý (lease hết hạn/worker khác nhận lại) "
                    + "- kết quả lượt này sẽ không được ghi nhận", u.unitId(), u.taskType());
            }
        }
    }

    /** Watchdog: quá unit-timeout → interrupt; quá max-processing-time → ngừng gia hạn lease. */
    void enforceTimeouts(Instant now) {
        for (InFlightUnit u : inFlight.values()) {
            if (now.isAfter(u.softDeadline()) && u.interrupted().compareAndSet(false, true)) {
                log.warn("Unit {} (task {}) quá unit-timeout - interrupt thread xử lý; unit giữ PROCESSING "
                    + "tới khi handler thoát", u.unitId(), u.taskType());
                interruptIfRunning(u);
            }
            if (now.isAfter(u.hardDeadline()) && u.abandoned().compareAndSet(false, true)) {
                log.error("Unit {} (task {}) vượt max-processing-time mà handler chưa thoát - ngừng gia hạn "
                    + "lease, worker khác sẽ nhận lại sau khi lease hết hạn", u.unitId(), u.taskType());
                interruptIfRunning(u);
            }
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
            // Dừng heartbeat SAU khi drain: unit còn dở (nếu có) sẽ hết lease và được máy khác nhận lại.
            leaseKeeper.shutdownNow();
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
        Instant started = Instant.now();
        Duration unitTimeout = retryPolicy.unitTimeout(ctx);
        Duration hardLimit = props.getCore().getMaxProcessingTime();
        Duration minHard = unitTimeout.plusSeconds(Math.max(1, props.getCore().getClaimLeaseSeconds()));
        if (hardLimit == null || hardLimit.compareTo(minHard) < 0) {
            hardLimit = minHard;
        }
        inFlight.put(claimed.uniqueKey(), new InFlightUnit(claimed.uniqueKey(), claimed.unitId(), ctx.taskType(),
            Thread.currentThread(), started.plus(unitTimeout), started.plus(hardLimit),
            new AtomicBoolean(false), new AtomicBoolean(false), new AtomicBoolean(false)));
        try {
            UnitResult result = invokeHandler(handler, ctx);
            // Danh dau xong (watchdog khong interrupt nua) roi xoa co interrupt TRUOC khi settle - neu con co,
            // Hikari tu choi cap connection va unit ket PROCESSING toi khi lease het han.
            InFlightUnit self = inFlight.remove(claimed.uniqueKey());
            if (self != null) {
                synchronized (self) {
                    self.finished().set(true);
                }
            }
            if (Thread.interrupted()) {
                log.debug("Unit {} đã bị interrupt do quá unit-timeout; handler trả về {}", claimed.unitId(),
                    result.isSuccess() ? "thành công" : "lỗi");
            }
            boolean finalAttempt = claimed.attempts() >= claimed.maxAttempts();
            if (result.isSuccess()) {
                if (store.complete(claimed.uniqueKey(), worker)) {
                    metrics.unitFinal(ctx.taskType(), UnitStatus.DONE);
                    log.info("Unit {} (task {}, tenant {}, attempt {}/{}) DONE",
                        claimed.unitId(), ctx.taskType(), ctx.tenantId(),
                        claimed.attempts(), claimed.maxAttempts());
                } else {
                    logLostClaim(claimed, ctx, "DONE");
                }
            } else if (result.isWaitingDependency()) {
                Instant next = Instant.now().plus(props.getCore().getDependencyRetryInterval());
                if (store.defer(claimed.uniqueKey(), worker, result.error(), next)) {
                    log.warn("Unit {} (task {}) WAITING_DEPENDENCY; kiểm tra lại lúc {}; error={}",
                        claimed.unitId(), ctx.taskType(), next, shorten(result.error()));
                } else {
                    logLostClaim(claimed, ctx, "WAITING_DEPENDENCY");
                }
            } else {
                Instant next = retryPolicy.nextRetryAt(claimed.attempts());
                UnitStatus status = finalAttempt ? UnitStatus.FAILED : UnitStatus.RETRY;
                if (store.fail(claimed.uniqueKey(), worker, result.error(), next)) {
                    metrics.unitFinal(ctx.taskType(), status);
                    log.warn("Unit {} (task {}, tenant {}, attempt {}/{}) -> {}; error={}; nextRetryAt={}",
                        claimed.unitId(), ctx.taskType(), ctx.tenantId(),
                        claimed.attempts(), claimed.maxAttempts(), status, shorten(result.error()), next);
                } else {
                    logLostClaim(claimed, ctx, status.name());
                }
            }
            return result;
        } catch (RuntimeException e) {
            log.error("Lỗi nội bộ khi xử lý unit {}: {}", claimed.unitId(), e.getMessage(), e);
            return UnitResult.failure("internal: " + e.getMessage());
        } finally {
            inFlight.remove(claimed.uniqueKey());
            metrics.stopUnitTimer(sample, ctx.taskType());
            metrics.inflightUnits.decrementAndGet();
            admission.release();
        }
    }

    private void logLostClaim(UnitRecord claimed, TaskContext ctx, String intended) {
        log.warn("Unit {} (task {}, tenant {}) xử lý xong nhưng KHÔNG ghi được {} - claim đã mất (lease hết hạn, "
                + "worker khác nhận lại hoặc đã settle); trạng thái do lượt xử lý kia quyết định",
            claimed.unitId(), ctx.taskType(), ctx.tenantId(), intended);
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
