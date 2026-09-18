package com.td.worker.tasks.simpki;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test {@link SimPkiSignLogStore} trên H2 in-memory thật (không cần Docker/Testcontainers — CỐ Ý
 * dùng "INSERT + bắt DuplicateKeyException" thay vì {@code ON CONFLICT}, cùng lý do
 * {@code GuiNhanQuocGiaSendLogStoreTest}).
 */
class SimPkiSignLogStoreTest {

    private DataSource dataSource;
    private SimPkiSignLogStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:simpki-test-" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUsername("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.store = new SimPkiSignLogStore(dataSource);
    }

    private SimPkiSignPayload payload() {
        SimPkiSignPayload p = new SimPkiSignPayload();
        p.setDocumentId("doc-1");
        p.setFileId("file-1");
        p.setMsisdn("84972495193");
        p.setHashBase64("dGVzdA==");
        return p;
    }

    @Test
    void firstClaimReturnsTrueAndInsertsPendingRow() {
        boolean claimed = store.tryClaim("unit-1", "H05", payload());

        assertThat(claimed).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String status = jdbc.queryForObject(
            "SELECT status FROM worker_simpki_sign_log WHERE unit_id = ?", String.class, "unit-1");
        assertThat(status).isEqualTo("PENDING");
    }

    @Test
    void secondClaimWithSameUnitIdReturnsFalseIdempotent() {
        assertThat(store.tryClaim("unit-1", "H05", payload())).isTrue();
        assertThat(store.tryClaim("unit-1", "H05", payload())).isFalse();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        int count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM worker_simpki_sign_log WHERE unit_id = ?", Integer.class, "unit-1");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void markSignedUpdatesStatusAndTraceFields() {
        store.tryClaim("unit-1", "H05", payload());
        store.markSigned("unit-1", "trans-99", "1234", 500L);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var row = jdbc.queryForMap(
            "SELECT status, trans_id, verification_code, status_code FROM worker_simpki_sign_log WHERE unit_id = ?",
            "unit-1");
        assertThat(row.get("status")).isEqualTo("SIGNED");
        assertThat(row.get("trans_id")).isEqualTo("trans-99");
        assertThat(row.get("verification_code")).isEqualTo("1234");
        assertThat(((Number) row.get("status_code")).longValue()).isEqualTo(500L);
    }

    @Test
    void markFailedUpdatesStatusAndErrorDetail() {
        store.tryClaim("unit-1", "H05", payload());
        store.markFailed("unit-1", "loi mau");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var row = jdbc.queryForMap(
            "SELECT status, error_detail FROM worker_simpki_sign_log WHERE unit_id = ?", "unit-1");
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_detail")).isEqualTo("loi mau");
    }
}
