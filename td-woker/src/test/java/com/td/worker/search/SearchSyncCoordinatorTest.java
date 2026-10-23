package com.td.worker.search;

import com.td.worker.search.ElasticsearchHttpClient.ElasticsearchUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SearchSyncCoordinatorTest {

    private SearchSyncCoordinator coordinator;

    @AfterEach
    void close() {
        if (coordinator != null) {
            coordinator.close();
        }
    }

    private static void awaitTrue(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    // Nhiều thông báo dồn dập khi tenant đang chạy → gộp thành ĐÚNG 1 lượt chạy tiếp, không chạy trùng.
    @Test
    void requestsWhileRunning_shouldCoalesceIntoOneRerun() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        coordinator = new SearchSyncCoordinator((tenant, sweep) -> {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            if (runs.incrementAndGet() == 1) {
                firstStarted.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            concurrent.decrementAndGet();
            return null;
        }, 4, Duration.ofSeconds(1));

        coordinator.request("H05", false);
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
        for (int i = 0; i < 50; i++) {
            coordinator.request("H05", false);
        }
        release.countDown();

        awaitTrue(() -> runs.get() == 2);
        Thread.sleep(200);
        assertThat(runs.get()).isEqualTo(2);
        assertThat(maxConcurrent.get()).isEqualTo(1);
    }

    // Tenant đang nạp lớn không chặn tenant khác.
    @Test
    void differentTenants_shouldRunInParallel() throws Exception {
        CountDownLatch bothRunning = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        coordinator = new SearchSyncCoordinator((tenant, sweep) -> {
            bothRunning.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }, 4, Duration.ofSeconds(1));

        coordinator.request("H05", false);
        coordinator.request("H05_01", false);

        assertThat(bothRunning.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
    }

    // Hết lượt mà còn việc (ZERO) → tự chạy tiếp tới khi xong.
    @Test
    void moreWork_shouldRequeueUntilDone() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        coordinator = new SearchSyncCoordinator((tenant, sweep) -> runs.incrementAndGet() < 3 ? Duration.ZERO : null,
            2, Duration.ofSeconds(1));

        coordinator.request("H05", false);

        awaitTrue(() -> runs.get() == 3);
        Thread.sleep(200);
        assertThat(runs.get()).isEqualTo(3);
    }

    // Dòng chờ thử lại / ES chưa sẵn sàng → tự hẹn giờ chạy lại, không cần thông báo mới.
    @Test
    void retryDelayAndUnavailable_shouldScheduleWakeUp() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        coordinator = new SearchSyncCoordinator((tenant, sweep) -> {
            int n = runs.incrementAndGet();
            if (n == 1) {
                return Duration.ofMillis(100);
            }
            if (n == 2) {
                throw new ElasticsearchUnavailableException("down", null);
            }
            return null;
        }, 2, Duration.ofMillis(100));

        coordinator.request("H05", false);

        awaitTrue(() -> runs.get() == 3);
    }

    @Test
    void sweepFlag_shouldReachTask() throws Exception {
        Map<String, Boolean> sweeps = new ConcurrentHashMap<>();
        coordinator = new SearchSyncCoordinator((tenant, sweep) -> {
            sweeps.put(tenant, sweep);
            return null;
        }, 2, Duration.ofSeconds(1));

        coordinator.request("H05", true);

        awaitTrue(() -> Boolean.TRUE.equals(sweeps.get("H05")));
    }
}
