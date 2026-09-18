package com.td.worker.core.checkpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.core.envelope.MessageEnvelope;
import com.td.worker.core.envelope.MessageUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test {@link JdbcCheckpointStore} trên H2 (PostgreSQL mode) — focus vào idempotency,
 * competing claim, lease crash-recovery, retry/FAILED theo maxAttempts.
 */
class JdbcCheckpointStoreTest {

    private DataSource dataSource;
    private JdbcCheckpointStore store;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:worker-test-" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUsername("sa");
        ds.setPassword("");
        this.dataSource = ds;
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/worker-schema-h2.sql"));
        populator.execute(dataSource);
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.store = new JdbcCheckpointStore(dataSource, objectMapper);
    }

    @AfterEach
    void tearDown() {
        new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
    }

    private MessageEnvelope message(String messageId, List<MessageUnit> units) {
        return MessageEnvelope.builder()
            .messageId(messageId)
            .taskType("sample.echo")
            .correlationId("corr-" + messageId)
            .source("test")
            .units(units)
            .build();
    }

    private MessageUnit unit(String unitId, String tenantId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("unitId", unitId);
        return MessageUnit.builder()
            .unitId(unitId)
            .tenantId(tenantId)
            .payload(payload)
            .maxAttempts(3)
            .build();
    }

    @Test
    void initializeUnitsIsIdempotent() {
        MessageEnvelope m = message("m1", List.of(unit("u1", "H05"), unit("u2", "H06")));
        assertThat(store.initializeUnits(m)).isEqualTo(2);
        assertThat(store.initializeUnits(m)).isEqualTo(0);
        assertThat(store.countByStatusForMessage("m1").get(UnitStatus.PENDING)).isEqualTo(2L);
    }

    @Test
    void claimIsExclusiveBetweenWorkers() {
        MessageEnvelope m = message("m1", List.of(unit("u1", "H05")));
        store.initializeUnits(m);
        String key = "H05|m1|u1";

        Optional<UnitRecord> first = store.claim(key, "worker-A", 120);
        assertThat(first).isPresent();
        assertThat(first.get().attempts()).isEqualTo(1);
        assertThat(first.get().status()).isEqualTo(UnitStatus.PROCESSING);

        assertThat(store.claim(key, "worker-B", 120)).isEmpty();
        assertThat(store.complete(key, "worker-A")).isTrue();
        assertThat(store.findByUniqueKey(key).get().status()).isEqualTo(UnitStatus.DONE);
        assertThat(store.claim(key, "worker-B", 120)).isEmpty();
    }
@Test
    void expiredLeaseCanBeReclaimed() throws Exception {
        MessageEnvelope m = message("m1", List.of(unit("u1", "H05")));
        store.initializeUnits(m);
        String key = "H05|m1|u1";

        store.claim(key, "worker-A", 1);
        Thread.sleep(1100);
        Optional<UnitRecord> reclaimed = store.claim(key, "worker-B", 120);
        assertThat(reclaimed).isPresent();
        assertThat(reclaimed.get().attempts()).isEqualTo(2);
        assertThat(reclaimed.get().status()).isEqualTo(UnitStatus.PROCESSING);
    }

    @Test
    void failTransitionsToRetryThenFailedAfterMaxAttempts() {
        MessageEnvelope m = message("m1", List.of(unit("u1", "H05")));
        store.initializeUnits(m);
        String key = "H05|m1|u1";

        Optional<UnitRecord> rec;
        for (int i = 1; i <= 3; i++) {
            rec = store.claim(key, "worker-A", 120);
            assertThat(rec).isPresent();
            Instant next = Instant.now().minusMillis(1); // đủ hạn để claim lại
            store.fail(key, "worker-A", "boom " + i, next);
            if (i < 3) {
                assertThat(store.findByUniqueKey(key).get().status()).isEqualTo(UnitStatus.RETRY);
            }
        }
        assertThat(store.findByUniqueKey(key).get().status()).isEqualTo(UnitStatus.FAILED);
        assertThat(store.claim(key, "worker-A", 120)).isEmpty();
    }

    @Test
    void requeueExpiredLeasesMovesProcessingToRetry() throws Exception {
        MessageEnvelope m = message("m1", List.of(unit("u1", "H05")));
        store.initializeUnits(m);
        String key = "H05|m1|u1";
        store.claim(key, "worker-A", 1);
        Thread.sleep(1100);
        int changed = store.requeueExpiredLeases(Duration.ZERO, 10);
        assertThat(changed).isEqualTo(1);
        assertThat(store.findByUniqueKey(key).get().status()).isEqualTo(UnitStatus.RETRY);
    }

    @Test
    void completeIsOwnedByClaimingWorkerOnly() {
        MessageEnvelope m = message("m1", List.of(unit("u1", "H05")));
        store.initializeUnits(m);
        String key = "H05|m1|u1";
        store.claim(key, "worker-A", 120);
        assertThat(store.complete(key, "worker-B")).isFalse();
        assertThat(store.complete(key, "worker-A")).isTrue();
    }

    @Test
    void deferMovesProcessingToWaitingDependencyWithoutConsumingAttempt() {
        MessageEnvelope m = message("m1", List.of(unit("u1", "H05")));
        store.initializeUnits(m);
        String key = "H05|m1|u1";

        Optional<UnitRecord> claimed = store.claim(key, "worker-A", 120);
        assertThat(claimed.get().attempts()).isEqualTo(1);

        Instant next = Instant.now().minusMillis(1); // đã tới hạn kiểm tra lại
        assertThat(store.defer(key, "worker-A", "chưa có gateway", next)).isTrue();
        UnitRecord deferred = store.findByUniqueKey(key).get();
        assertThat(deferred.status()).isEqualTo(UnitStatus.WAITING_DEPENDENCY);
        // defer() không được tiêu số lần thử nghiệp vụ (maxAttempts) - unit tự chờ vô hạn định.
        assertThat(deferred.attempts()).isEqualTo(0);

        // Tới hạn next_retry_at -> claim lại được như RETRY bình thường (RetryPoller sẽ tự làm).
        Optional<UnitRecord> reclaimed = store.claim(key, "worker-B", 120);
        assertThat(reclaimed).isPresent();
        assertThat(reclaimed.get().status()).isEqualTo(UnitStatus.PROCESSING);
    }

    @Test
    void findDueRetriesIncludesWaitingDependency() {
        MessageEnvelope m = message("m1", List.of(unit("u1", "H05")));
        store.initializeUnits(m);
        String key = "H05|m1|u1";
        store.claim(key, "worker-A", 120);
        store.defer(key, "worker-A", "chưa có gateway", Instant.now().minusMillis(1));

        assertThat(store.findDueRetries(10)).containsExactly(key);
    }

    @Test
    void countByStatusForMessage() {
        MessageEnvelope m = message("m1", List.of(unit("u1", "H05"), unit("u2", "H06")));
        store.initializeUnits(m);
        store.claim("H05|m1|u1", "worker-A", 120);
        Map<UnitStatus, Long> counts = store.countByStatusForMessage("m1");
        assertThat(counts.get(UnitStatus.PENDING)).isEqualTo(1L);
        assertThat(counts.get(UnitStatus.PROCESSING)).isEqualTo(1L);
    }
}