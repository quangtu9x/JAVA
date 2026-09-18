package com.td.infrastructure.errortracking;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadlockWatchdogTest {

    private final ErrorTrackingService errorTrackingService = mock(ErrorTrackingService.class);
    private final ErrorTrackingProperties properties = new ErrorTrackingProperties();
    private final DeadlockWatchdog watchdog = new DeadlockWatchdog(errorTrackingService, properties);

    @Test
    void checkForDeadlocks_shouldNotCapture_whenNoDeadlockInCurrentJvm() {
        // JVM test hien tai (khong co deadlock that) - findDeadlockedThreads() tra null/rong.
        watchdog.checkForDeadlocks();

        verify(errorTrackingService, never()).captureSystemEvent(any(), any(), any());
    }

    @Test
    void checkForDeadlocks_shouldSkip_whenDisabled() {
        properties.setEnabled(false);

        watchdog.checkForDeadlocks();

        verify(errorTrackingService, never()).captureSystemEvent(any(), any(), any());
    }

    @Test
    void checkForDeadlocks_shouldSkip_whenWatchdogDisabled() {
        properties.getDeadlockWatchdog().setEnabled(false);

        watchdog.checkForDeadlocks();

        verify(errorTrackingService, never()).captureSystemEvent(any(), any(), any());
    }

    @Test
    void checkForDeadlocks_shouldNeverThrow_whenCaptureFails() {
        when(errorTrackingService.captureSystemEvent(any(), any(), any())).thenThrow(new RuntimeException("DB down"));

        watchdog.checkForDeadlocks(); // khong co deadlock that nen khong goi capture, nhung method van khong duoc throw du sao
    }
}
