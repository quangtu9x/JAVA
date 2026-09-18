package com.td.web.numbering;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-104 (Document Numbering Service) — Definition of Done (3): không trùng số khi 100 request
 * đồng thời; và Tiêu chí kiểm thử: năm mới → số reset về 1. Dùng PostgreSQL thật (Testcontainers,
 * không mock) vì đây là bài toán concurrency thật — mock không thể chứng minh tính an toàn của
 * {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}.
 *
 * <p><b>Lưu ý môi trường</b>: giống các integration test BL-101/BL-303 khác — container khởi động
 * thủ công để tự "skip" (không fail build) nếu Docker cục bộ không hỗ trợ Linux container. Test
 * này chạy thật trong CI (`.github/workflows/ci.yml`, {@code ubuntu-latest}).
 */
class NumberingCounterConcurrencyIntegrationTest {

    private static final String UPSERT_AND_RETURN_SQL = """
        INSERT INTO numbering_counters (id, document_type, year, scope_key, current_value, updated_at)
        VALUES (uuid_generate_v4(), ?, ?, ?, 1, NOW())
        ON CONFLICT (document_type, year, scope_key)
        DO UPDATE SET current_value = numbering_counters.current_value + 1, updated_at = NOW()
        RETURNING current_value
        """;

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;

    @BeforeAll
    static void startContainerAndMigrate() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdwebapi_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua NumberingCounterConcurrencyIntegrationTest: khong co Docker Linux container "
                    + "kha dung trong moi truong nay (" + ex.getMessage() + "). Test nay chay that "
                    + "trong CI (.github/workflows/ci.yml, ubuntu-latest).");
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-numbering-counter-test");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        config.setMaximumPoolSize(20);
        dataSource = new HikariDataSource(config);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void concurrentIncrements_shouldProduceNoDuplicates() throws Exception {
        int requestCount = 100;
        String documentType = "concurrency_test_vbden";
        int year = 2026;

        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        Set<Long> results = ConcurrentHashMap.newKeySet();
        AtomicLong failures = new AtomicLong();

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    long value = incrementAndGet(documentType, year, "_");
                    results.add(value);
                } catch (Exception ex) {
                    failures.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(failures.get()).isZero();
        assertThat(results).hasSize(requestCount);
        assertThat(results).isEqualTo(IntStream.rangeClosed(1, requestCount).mapToObj(i -> (long) i)
            .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void newYear_shouldResetSequenceToOne() throws Exception {
        String documentType = "reset_test_vbdi";

        assertThat(incrementAndGet(documentType, 2030, "_")).isEqualTo(1L);
        assertThat(incrementAndGet(documentType, 2030, "_")).isEqualTo(2L);
        assertThat(incrementAndGet(documentType, 2030, "_")).isEqualTo(3L);

        // Nam moi (2031) -> bo dem rieng, bat dau lai tu 1 (DoD: "Tao document o nam moi -> so reset ve 1")
        assertThat(incrementAndGet(documentType, 2031, "_")).isEqualTo(1L);
        assertThat(incrementAndGet(documentType, 2031, "_")).isEqualTo(2L);

        // Nam 2030 khong bi anh huong boi viec tang o 2031
        assertThat(incrementAndGet(documentType, 2030, "_")).isEqualTo(4L);
    }

    @Test
    void differentScopeKey_shouldHaveIndependentCounters() throws Exception {
        String documentType = "scope_test_vbdi";

        assertThat(incrementAndGet(documentType, 2026, "unit-a")).isEqualTo(1L);
        assertThat(incrementAndGet(documentType, 2026, "unit-a")).isEqualTo(2L);
        assertThat(incrementAndGet(documentType, 2026, "unit-b")).isEqualTo(1L);
    }

    private long incrementAndGet(String documentType, int year, String scopeKey) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_AND_RETURN_SQL)) {
            statement.setString(1, documentType);
            statement.setInt(2, year);
            statement.setString(3, scopeKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
