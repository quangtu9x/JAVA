package com.td.worker.search;

import com.td.worker.search.SearchIndexQueueStore.QueueItem;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy ĐÚNG migration td-web {@code V1.0.72__Create_Search_Index_Queue.sql} trên PostgreSQL thật
 * (bảng "documents" partition như V1.0.26) rồi kiểm tra trigger + SQL của {@link SearchIndexQueueStore}.
 * Không có Docker → bỏ qua (chạy thật trong CI).
 */
class SearchIndexQueueIntegrationTest {

    private static final Path MIGRATION = Path.of(
        "../td-web/src/main/resources/db/migration/V1.0.72__Create_Search_Index_Queue.sql");
    private static final Path MIGRATION_NOTIFY = Path.of(
        "../td-web/src/main/resources/db/migration/V1.0.73__Search_Index_Queue_Notify.sql");
    private static final List<String> OTHER_TABLES = List.of(
        "vbdi_documents", "duthao_documents", "hosocv_documents", "phieutrinhtd_documents",
        "vbcddh_documents", "hosoluutru_documents");

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;

    private final SearchIndexQueueStore store = new SearchIndexQueueStore();

    /** Không có Docker: chạy trên PostgreSQL có sẵn (biến môi trường), trong 1 schema tạm, xoá khi xong. */
    private static String tempSchema;
    private static HikariDataSource adminDataSource;

    @BeforeAll
    static void startContainer() throws Exception {
        String url;
        String username;
        String password;
        String externalUrl = System.getenv("SEARCH_SYNC_IT_JDBC_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            tempSchema = "search_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            username = System.getenv("SEARCH_SYNC_IT_JDBC_USERNAME");
            password = System.getenv("SEARCH_SYNC_IT_JDBC_PASSWORD");
            HikariConfig adminConfig = new HikariConfig();
            adminConfig.setJdbcUrl(externalUrl);
            adminConfig.setUsername(username);
            adminConfig.setPassword(password);
            adminConfig.setMaximumPoolSize(1);
            adminDataSource = new HikariDataSource(adminConfig);
            new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + tempSchema);
            url = externalUrl + (externalUrl.contains("?") ? "&" : "?") + "currentSchema=" + tempSchema;
        } else {
            try {
                postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("search_queue_test").withUsername("test").withPassword("test");
                postgres.start();
            } catch (Exception ex) {
                Assumptions.assumeTrue(false, "Bỏ qua: không có Docker và không khai SEARCH_SYNC_IT_JDBC_URL ("
                    + ex.getMessage() + ")");
                return;
            }
            url = postgres.getJdbcUrl();
            username = postgres.getUsername();
            password = postgres.getPassword();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);
        jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
            CREATE TABLE documents (
                id UUID NOT NULL,
                title VARCHAR(300),
                document_type VARCHAR(100) NOT NULL,
                dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
                legacy_json JSON,
                deleted_on TIMESTAMPTZ,
                PRIMARY KEY (id, document_type)
            ) PARTITION BY LIST (document_type);
            CREATE TABLE documents_duthao PARTITION OF documents FOR VALUES IN ('duthao');
            CREATE TABLE documents_default PARTITION OF documents DEFAULT;
            """);
        for (String table : OTHER_TABLES) {
            jdbc.execute("CREATE TABLE " + table + " (id UUID PRIMARY KEY, title VARCHAR(300))");
        }
        jdbc.execute(Files.readString(MIGRATION));
        jdbc.execute(Files.readString(MIGRATION_NOTIFY));
    }

    @AfterAll
    static void stop() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (adminDataSource != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + tempSchema + " CASCADE");
            adminDataSource.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void clean() {
        jdbc.execute("DELETE FROM search_index_queue");
        jdbc.execute("DELETE FROM documents");
        for (String table : OTHER_TABLES) {
            jdbc.execute("DELETE FROM " + table);
        }
        jdbc.execute("DELETE FROM search_index_queue");
    }

    private long queued(String entityType, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM search_index_queue WHERE entity_type = ? AND entity_id = ?",
            Long.class, entityType, id);
    }

    @Test
    void trigger_shouldEnqueueInsertUpdateDelete_onPartitionedTable() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (id, title, document_type) VALUES (?, 'a', 'vbden')", id);
        jdbc.update("UPDATE documents SET title = 'b' WHERE id = ?", id);
        jdbc.update("DELETE FROM documents WHERE id = ?", id);

        List<String> ops = jdbc.queryForList(
            "SELECT operation FROM search_index_queue WHERE entity_id = ? ORDER BY id", String.class, id);
        assertThat(ops).containsExactly("I", "U", "D");
    }

    // UPDATE không đổi gì không sinh hàng đợi; bảng có cột kiểu json (không có toán tử "=") vẫn UPDATE được.
    @Test
    void trigger_shouldSkipNoopUpdate_andWorkWithJsonColumn() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (id, title, document_type, legacy_json) VALUES (?, 'a', 'duthao', '{\"x\":1}')", id);
        jdbc.update("UPDATE documents SET title = 'a' WHERE id = ?", id);

        assertThat(queued("documents", id)).isEqualTo(1);
    }

    @Test
    void trigger_shouldCoverEveryConfiguredTable() {
        for (String table : OTHER_TABLES) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO " + table + " (id, title) VALUES (?, 'x')", id);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM search_index_queue WHERE entity_id = ?", Long.class, id))
                .as(table).isEqualTo(1);
        }
    }

    // Rollback nghiệp vụ => không để lại dòng hàng đợi (cùng transaction).
    @Test
    void trigger_shouldRollbackWithBusinessTransaction() throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var ps = connection.prepareStatement("INSERT INTO vbdi_documents (id, title) VALUES (?, 'x')")) {
                ps.setObject(1, id);
                ps.executeUpdate();
            }
            connection.rollback();
        }
        assertThat(queued("vbdi", id)).isZero();
    }

    // V1.0.73: thông báo CHỈ đến khi COMMIT (rollback thì không), và nhiều dòng trong 1 transaction gộp thành 1.
    @Test
    void notify_shouldArriveOnlyAfterCommit_andBeDeduplicated() throws Exception {
        try (Connection listenConnection = dataSource.getConnection(); Connection writer = dataSource.getConnection()) {
            listenConnection.setAutoCommit(true);
            try (var st = listenConnection.createStatement()) {
                st.execute("LISTEN search_index");
            }
            org.postgresql.PGConnection pg = listenConnection.unwrap(org.postgresql.PGConnection.class);

            writer.setAutoCommit(false);
            try (var ps = writer.prepareStatement("INSERT INTO vbdi_documents (id, title) VALUES (?, 'x')")) {
                for (int i = 0; i < 5; i++) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.executeUpdate();
                }
            }
            assertThat(pg.getNotifications(300)).as("chưa commit thì chưa có thông báo").isNullOrEmpty();
            writer.rollback();
            assertThat(pg.getNotifications(300)).as("rollback thì không có thông báo").isNullOrEmpty();

            try (var ps = writer.prepareStatement("INSERT INTO vbdi_documents (id, title) VALUES (?, 'x')")) {
                for (int i = 0; i < 5; i++) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.executeUpdate();
                }
            }
            writer.commit();
            org.postgresql.PGNotification[] received = pg.getNotifications(2000);
            assertThat(received).hasSize(1);
            assertThat(received[0].getName()).isEqualTo("search_index");
            assertThat(received[0].getParameter()).isEqualTo("vbdi");
        }
    }

    @Test
    void store_nextDueAt() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(store.nextDueAt(connection)).isNull();
            jdbc.update("INSERT INTO vbdi_documents (id, title) VALUES (?, 'x')", UUID.randomUUID());
            assertThat(store.nextDueAt(connection)).isNotNull();
        }
    }

    @Test
    void store_claimLoadDeleteRetry() throws Exception {
        UUID alive = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (id, title, document_type, dynamic_fields) VALUES (?, 'Công văn', 'vbden', '{\"k\":\"v\"}')", alive);
        jdbc.update("INSERT INTO documents (id, title, document_type) VALUES (?, 'x', 'vbden')", gone);
        jdbc.update("DELETE FROM documents WHERE id = ?", gone);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            List<QueueItem> items = store.claim(connection, 100);
            assertThat(items).hasSize(3);

            Map<UUID, String> rows = store.loadRows(connection, "documents", List.of(alive, gone));
            assertThat(rows).containsOnlyKeys(alive);
            assertThat(rows.get(alive)).contains("Công văn").contains("\"k\": \"v\"");

            Map<String, String> columns = store.columnTypes(connection, "documents");
            assertThat(columns).containsEntry("id", "uuid").containsEntry("dynamic_fields", "jsonb");

            store.delete(connection, List.of(items.get(0).id()));
            store.markRetry(connection, List.of(items.get(1).id()), "lỗi thử", Duration.ofSeconds(10), Duration.ofMinutes(30));
            connection.commit();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM search_index_queue", Long.class)).isEqualTo(2);
        Map<String, Object> retried = jdbc.queryForMap(
            "SELECT attempts, last_error, next_try_at > now() AS later FROM search_index_queue WHERE attempts > 0");
        assertThat(retried.get("attempts")).isEqualTo(1);
        assertThat(retried.get("last_error")).isEqualTo("lỗi thử");
        assertThat(retried.get("later")).isEqualTo(true);
    }

    // 2 worker cùng claim: SKIP LOCKED => không trùng dòng.
    @Test
    void store_claimShouldSkipLockedRows() throws Exception {
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO vbdi_documents (id, title) VALUES (?, 'x')", UUID.randomUUID());
        }
        try (Connection first = dataSource.getConnection(); Connection second = dataSource.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            List<QueueItem> a = store.claim(first, 2);
            List<QueueItem> b = store.claim(second, 10);
            assertThat(a).hasSize(2);
            assertThat(b).hasSize(2);
            assertThat(a).extracting(QueueItem::id).doesNotContainAnyElementsOf(b.stream().map(QueueItem::id).toList());
            first.rollback();
            second.rollback();
        }
    }

    @Test
    void store_enqueueAllAndCounts() throws Exception {
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO hosocv_documents (id, title) VALUES (?, 'x')", UUID.randomUUID());
        }
        jdbc.execute("DELETE FROM search_index_queue");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            assertThat(store.enqueueAll(connection, "hosocv", "hosocv_documents")).isEqualTo(3);
            assertThat(store.pending(connection, "hosocv")).isEqualTo(3);
            assertThat(store.countRows(connection, "hosocv_documents")).isEqualTo(3);
            assertThat(store.tryAdvisoryLock(connection, "search-index:hosocv")).isTrue();
            assertThat(store.queueStats(connection)[0]).isEqualTo(3);
            connection.commit();
        }
    }
}
