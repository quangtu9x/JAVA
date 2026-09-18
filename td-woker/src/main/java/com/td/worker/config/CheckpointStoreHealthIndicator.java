package com.td.worker.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Health indicator cho checkpoint store (database RIÊNG của Worker) — nếu database này down,
 * Worker không thể claim/persist trạng thái unit, nên /actuator/health trả DOWN (fail fast
 * trong orchestration, đúng yêu cầu "Health/readiness" docs/promt.txt).
 */
@Component
@RequiredArgsConstructor
public class CheckpointStoreHealthIndicator implements HealthIndicator {

    private final DataSource workerDataSource;

    @Override
    public Health health() {
        try (Connection ignored = workerDataSource.getConnection()) {
            return Health.up()
                .withDetail("database", workerDataSource.getClass().getSimpleName())
                .build();
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("reason", "Không lấy được connection tới database checkpoint Worker")
                .build();
        }
    }
}