package com.td.infrastructure.tenant;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @Test
    void setAndGet_shouldReturnSameValue() {
        TenantContext.setCurrentTenant("H05");

        assertThat(TenantContext.getCurrentTenant()).isEqualTo("H05");

        TenantContext.clear();
    }

    @Test
    void clear_shouldRemoveValue() {
        TenantContext.setCurrentTenant("H05");
        TenantContext.clear();

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void getCurrentTenant_shouldBeNull_whenNeverSetOnThisThread() throws InterruptedException {
        // Bảo đảm tenant KHÔNG rò rỉ giữa các thread khác nhau (Tomcat dùng thread pool) —
        // đây là lý do TenantContext phải là ThreadLocal, không phải static field thường.
        TenantContext.setCurrentTenant("H05");

        AtomicReference<String> seenOnOtherThread = new AtomicReference<>("not-run");
        CountDownLatch latch = new CountDownLatch(1);
        Thread other = new Thread(() -> {
            seenOnOtherThread.set(TenantContext.getCurrentTenant());
            latch.countDown();
        });
        other.start();
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(seenOnOtherThread.get()).isNull();

        TenantContext.clear();
    }

    /**
     * Yêu cầu người dùng 04/09/2026 (outbox dispatcher đa tenant cần chạy code NGOÀI request —
     * xem {@link OutboxDispatcherScheduler}): {@code runAs} phải set đúng tenant TRONG action,
     * rồi clear/khôi phục sau khi xong, KỂ CẢ khi action ném exception.
     */
    @Test
    void runAs_shouldSetTenantDuringActionThenClear() {
        TenantContext.runAs("H05", () -> assertThat(TenantContext.getCurrentTenant()).isEqualTo("H05"));

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void runAs_shouldRestorePreviousTenant_whenNestedInsideAnother() {
        TenantContext.setCurrentTenant("OUTER");

        TenantContext.runAs("INNER", () -> assertThat(TenantContext.getCurrentTenant()).isEqualTo("INNER"));

        assertThat(TenantContext.getCurrentTenant()).isEqualTo("OUTER");
        TenantContext.clear();
    }

    @Test
    void runAs_shouldClearEvenWhenActionThrows() {
        try {
            TenantContext.runAs("H05", () -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException ignored) {
            // Da du kien - runAs khong nuot exception, chi dam bao clear o finally.
        }

        assertThat(TenantContext.getCurrentTenant()).isNull();
    }
}
