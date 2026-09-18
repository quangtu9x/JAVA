package com.td.infrastructure.errortracking;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorLogCleanupSchedulerTest {

    private final ErrorLogJdbcRepository errorLogRepository = mock(ErrorLogJdbcRepository.class);
    private final ErrorTrackingProperties properties = new ErrorTrackingProperties();
    private final ErrorLogCleanupScheduler scheduler = new ErrorLogCleanupScheduler(errorLogRepository, properties);

    @Test
    void cleanupOldLogs_shouldDeleteRowsOlderThanRetention() {
        properties.setRetentionDays(30);
        when(errorLogRepository.deleteByOccurredAtBefore(any())).thenReturn(42);

        scheduler.cleanupOldLogs();

        verify(errorLogRepository).deleteByOccurredAtBefore(any(Instant.class));
    }

    @Test
    void cleanupOldLogs_shouldSkip_whenDisabled() {
        properties.setEnabled(false);

        scheduler.cleanupOldLogs();

        verify(errorLogRepository, never()).deleteByOccurredAtBefore(any());
    }

    @Test
    void cleanupOldLogs_shouldNeverThrow_whenDeleteFails() {
        when(errorLogRepository.deleteByOccurredAtBefore(any())).thenThrow(new RuntimeException("DB down"));

        scheduler.cleanupOldLogs(); // khong duoc throw ra ngoai
    }
}
