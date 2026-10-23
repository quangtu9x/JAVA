package com.td.worker.search;

import com.td.worker.search.ElasticsearchHttpClient.ElasticsearchUnavailableException;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Điều phối đồng bộ THEO TENANT, kích hoạt bởi sự kiện:
 * <ul>
 *   <li>{@link #request} — có thông báo (LISTEN/NOTIFY), quét dự phòng, hoặc tới hạn thử lại.</li>
 *   <li>Mỗi tenant tối đa 1 luồng; yêu cầu đến khi tenant đang chạy được GỘP thành 1 lượt chạy tiếp ngay sau
 *   (không mất, không chạy trùng). Nhiều tenant chạy song song (tối đa {@code maxParallelTenants}).</li>
 *   <li>Tenant hết lượt mà còn việc → xếp lại cuối hàng (nhường tenant khác); có dòng chờ thử lại → hẹn giờ
 *   đúng lúc đến hạn; ES không kết nối được → hẹn {@code unavailableRetryDelay}.</li>
 * </ul>
 */
@Slf4j
public class SearchSyncCoordinator implements AutoCloseable {

    private static final int IDLE = 0;
    private static final int RUNNING = 1;
    private static final int RUNNING_RERUN = 2;

    /** Phần xử lý 1 tenant (thực tế: {@link SearchIndexSyncService#syncTenant(String, boolean)}). */
    @FunctionalInterface
    public interface TenantSyncTask {
        Duration sync(String tenantId, boolean sweep) throws Exception;
    }

    private final TenantSyncTask task;
    private final Duration unavailableRetryDelay;
    private final ExecutorService workers;
    private final ScheduledExecutorService timer;
    private final Map<String, AtomicInteger> states = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pendingSweep = new ConcurrentHashMap<>();
    /** Mốc đánh thức sớm nhất đã hẹn cho từng tenant — tránh hẹn chồng nhiều lần cùng 1 việc. */
    private final Map<String, AtomicLong> scheduledWake = new ConcurrentHashMap<>();
    private final AtomicLong lastUnavailableLog = new AtomicLong();

    public SearchSyncCoordinator(TenantSyncTask task, int maxParallelTenants, Duration unavailableRetryDelay) {
        this.task = task;
        this.unavailableRetryDelay = unavailableRetryDelay;
        AtomicInteger counter = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(Math.max(1, maxParallelTenants), r -> {
            Thread thread = new Thread(r, "search-sync-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "search-sync-timer");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Yêu cầu xử lý tenant ngay (gộp nếu đang chạy). */
    public void request(String tenantId, boolean sweep) {
        if (sweep) {
            pendingSweep.put(tenantId, Boolean.TRUE);
        }
        AtomicInteger state = states.computeIfAbsent(tenantId, k -> new AtomicInteger(IDLE));
        while (true) {
            int current = state.get();
            if (current == IDLE) {
                if (state.compareAndSet(IDLE, RUNNING)) {
                    try {
                        workers.execute(() -> run(tenantId, state));
                    } catch (RuntimeException rejected) {
                        state.set(IDLE); // đang tắt
                    }
                    return;
                }
            } else if (current == RUNNING) {
                if (state.compareAndSet(RUNNING, RUNNING_RERUN)) {
                    return;
                }
            } else {
                return; // đã có 1 lượt chạy tiếp đang chờ
            }
        }
    }

    /** Hẹn xử lý tenant sau {@code delay} (chỉ giữ mốc sớm nhất). */
    public void requestLater(String tenantId, Duration delay) {
        long wakeAt = System.currentTimeMillis() + Math.max(0, delay.toMillis());
        AtomicLong scheduled = scheduledWake.computeIfAbsent(tenantId, k -> new AtomicLong(Long.MAX_VALUE));
        while (true) {
            long current = scheduled.get();
            boolean stale = current <= System.currentTimeMillis();
            if (!stale && current <= wakeAt) {
                return; // đã có hẹn sớm hơn/bằng
            }
            if (scheduled.compareAndSet(current, wakeAt)) {
                break;
            }
        }
        try {
            timer.schedule(() -> {
                scheduled.compareAndSet(wakeAt, Long.MAX_VALUE);
                request(tenantId, false);
            }, Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
        } catch (RuntimeException rejected) {
            // đang tắt
        }
    }

    private void run(String tenantId, AtomicInteger state) {
        while (true) {
            boolean sweep = pendingSweep.remove(tenantId) != null;
            try {
                Duration next = task.sync(tenantId, sweep);
                if (next != null) {
                    if (next.isZero()) {
                        // Còn việc: nhả luồng rồi xếp lại cuối hàng để tenant khác được chạy.
                        state.set(IDLE);
                        request(tenantId, false);
                        return;
                    }
                    requestLater(tenantId, next);
                }
            } catch (ElasticsearchUnavailableException ex) {
                long now = System.currentTimeMillis();
                long last = lastUnavailableLog.get();
                if (now - last > 60_000 && lastUnavailableLog.compareAndSet(last, now)) {
                    log.warn("Elasticsearch chưa sẵn sàng, giữ nguyên hàng đợi, thử lại sau {}: {}",
                        unavailableRetryDelay, ex.getMessage());
                }
                requestLater(tenantId, unavailableRetryDelay);
            } catch (Throwable ex) {
                log.warn("Đồng bộ Elasticsearch tenant {} lỗi, thử lại sau {}: {}", tenantId, unavailableRetryDelay, ex.getMessage());
                requestLater(tenantId, unavailableRetryDelay);
            }
            if (state.compareAndSet(RUNNING, IDLE)) {
                return;
            }
            state.set(RUNNING); // RUNNING_RERUN: có yêu cầu mới trong lúc chạy → chạy thêm 1 lượt
        }
    }

    @Override
    public void close() {
        timer.shutdownNow();
        workers.shutdown();
        try {
            workers.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
