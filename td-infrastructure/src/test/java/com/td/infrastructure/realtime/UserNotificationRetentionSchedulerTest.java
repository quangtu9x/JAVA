package com.td.infrastructure.realtime;

import com.td.application.realtime.UserNotificationRepository;
import com.td.infrastructure.tenant.TenantActiveRegistry;
import com.td.infrastructure.tenant.TenantRoutingDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserNotificationRetentionSchedulerTest {

    private final UserNotificationRepository repository = Mockito.mock(UserNotificationRepository.class);
    private final UserNotificationRetentionScheduler scheduler = new UserNotificationRetentionScheduler(
        Mockito.mock(TenantRoutingDataSource.class), Mockito.mock(TenantActiveRegistry.class), repository);

    @Test
    void purgeTenant_loopsBatchesUntilLastPartialBatch() {
        when(repository.softDeleteExpired(any(), any(), eq(100))).thenReturn(100, 100, 7);
        LocalDateTime now = LocalDateTime.now();

        int total = scheduler.purgeTenant(now.minusDays(180), now.minusDays(365), 100);

        assertThat(total).isEqualTo(207);
        verify(repository, times(3)).softDeleteExpired(any(), any(), eq(100));
    }

    @Test
    void purgeTenant_nothingToDelete_singleQuery() {
        when(repository.softDeleteExpired(any(), any(), eq(100))).thenReturn(0);
        LocalDateTime now = LocalDateTime.now();

        assertThat(scheduler.purgeTenant(now.minusDays(180), now.minusDays(365), 100)).isZero();
        verify(repository, times(1)).softDeleteExpired(any(), any(), eq(100));
    }
}
