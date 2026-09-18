package com.td.worker.tasks.guinhanquocgia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test {@link GuiNhanQuocGiaSendLogStore} trên H2 in-memory thật (không cần Docker/Testcontainers —
 * CỐ Ý dùng "INSERT + bắt DuplicateKeyException" thay vì {@code ON CONFLICT} chính vì lý do này, xem
 * javadoc lớp {@link GuiNhanQuocGiaSendLogStore}).
 */
class GuiNhanQuocGiaSendLogStoreTest {

    private DataSource dataSource;
    private GuiNhanQuocGiaSendLogStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:guinhanquocgia-test-" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUsername("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.store = new GuiNhanQuocGiaSendLogStore(dataSource);
    }

    private GuiNhanQuocGiaSendPayload payload() {
        GuiNhanQuocGiaSendPayload p = new GuiNhanQuocGiaSendPayload();
        p.setDocumentId("doc-1");
        p.setSenderAgencyCode("777.77.04.D77");
        p.setRecipientAgencyCode("99.99.99.G21");
        p.setDocType(GuiNhanQuocGiaTaskConstants.DOC_TYPE_NEW);
        return p;
    }

    @Test
    void firstClaimReturnsTrueAndInsertsPendingRow() {
        boolean claimed = store.tryClaim("unit-1", "H05", payload());

        assertThat(claimed).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String status = jdbc.queryForObject(
            "SELECT status FROM worker_guinhanquocgia_send_log WHERE unit_id = ?", String.class, "unit-1");
        assertThat(status).isEqualTo("PENDING");
    }

    @Test
    void secondClaimWithSameUnitIdReturnsFalseIdempotent() {
        assertThat(store.tryClaim("unit-1", "H05", payload())).isTrue();
        assertThat(store.tryClaim("unit-1", "H05", payload())).isFalse();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        int count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM worker_guinhanquocgia_send_log WHERE unit_id = ?", Integer.class, "unit-1");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void markSentUpdatesStatusAndCenterDocId() {
        store.tryClaim("unit-1", "H05", payload());
        store.markSent("unit-1", "center-doc-99");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var row = jdbc.queryForMap(
            "SELECT status, center_doc_id FROM worker_guinhanquocgia_send_log WHERE unit_id = ?", "unit-1");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("center_doc_id")).isEqualTo("center-doc-99");
    }

    @Test
    void markFailedUpdatesStatusAndErrorDetail() {
        store.tryClaim("unit-1", "H05", payload());
        store.markFailed("unit-1", "loi mau");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var row = jdbc.queryForMap(
            "SELECT status, error_detail FROM worker_guinhanquocgia_send_log WHERE unit_id = ?", "unit-1");
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("error_detail")).isEqualTo("loi mau");
    }
}
