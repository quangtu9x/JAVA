package com.td.infrastructure.fileregistry;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Yêu cầu người dùng 22/10/2026 (đính kèm dùng chung): migration {@code db/migration-fileregistry} (td-web) +
 * {@link SharedFileRegistryClient#hasActiveRefs}/{@link SharedFileRegistryClient#release} trên Postgres THẬT.
 * Testcontainers, hoặc {@code -Dtd.test.postgres.url=jdbc:postgresql://host:port/postgres} khi máy không có
 * Docker Linux (user phải được CREATE DATABASE).
 */
class SharedFileRegistryClientIntegrationTest {

    private static final Path MIGRATIONS = Path.of("..", "td-web", "src", "main", "resources", "db", "migration-fileregistry");
    private static final String DB = "sfrc_" + Long.toString(System.nanoTime(), 36);
    private static final String BUCKET = "td-webapi-files";

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource admin;
    private static String url;
    private static String user;
    private static String password;
    private static SharedFileRegistryClient client;

    @BeforeAll
    static void start() {
        Assumptions.assumeTrue(Files.isDirectory(MIGRATIONS), "Bo qua: khong thay " + MIGRATIONS.toAbsolutePath());
        String baseUrl = System.getProperty("td.test.postgres.url");
        if (baseUrl != null && !baseUrl.isBlank()) {
            user = System.getProperty("td.test.postgres.user", "postgres");
            password = System.getProperty("td.test.postgres.password", "");
        } else {
            try {
                postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("postgres").withUsername("test").withPassword("test");
                postgres.start();
            } catch (Exception ex) {
                Assumptions.assumeTrue(false, "Bo qua SharedFileRegistryClientIntegrationTest: khong co Docker Linux ("
                    + ex.getMessage() + ") - co the chay voi -Dtd.test.postgres.url");
                return;
            }
            baseUrl = postgres.getJdbcUrl();
            user = postgres.getUsername();
            password = postgres.getPassword();
        }
        admin = pool(baseUrl);
        new JdbcTemplate(admin).execute("CREATE DATABASE " + DB);
        int slash = baseUrl.indexOf('/', "jdbc:postgresql://".length());
        int query = baseUrl.indexOf('?', slash);
        url = baseUrl.substring(0, slash + 1) + DB + (query < 0 ? "" : baseUrl.substring(query));

        FileRegistryProperties props = new FileRegistryProperties();
        props.getDatasource().setUrl(url);
        props.getDatasource().setUsername(user);
        props.getDatasource().setPassword(password);
        client = new SharedFileRegistryClient(props);
        Flyway.configure().dataSource(client.dataSource())
            .locations("filesystem:" + MIGRATIONS.toAbsolutePath()).load().migrate();
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.destroy();
        }
        if (admin != null) {
            new JdbcTemplate(admin).execute("DROP DATABASE IF EXISTS " + DB);
            admin.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @AfterEach
    void clean() {
        new JdbcTemplate(client.dataSource()).execute("TRUNCATE shared_file_ref, shared_file_object");
    }

    @Test
    void releaseMarksObjectZeroRefOnlyWhenLastUnitReleases() {
        String key = "national-incoming/T1/1-a.pdf";
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        addRef(a, "ta", key);
        addRef(b, "tb", key);

        client.release(List.of(a));
        assertThat(client.hasActiveRefs(BUCKET, key)).isTrue();
        assertThat(zeroRefSinceIsSet(key)).isFalse();

        client.release(List.of(b));
        assertThat(client.hasActiveRefs(BUCKET, key)).isFalse();
        assertThat(zeroRefSinceIsSet(key)).isTrue();

        client.release(List.of(b)); // nha lai khong loi, khong doi gi
        assertThat(new JdbcTemplate(client.dataSource()).queryForObject(
            "SELECT COUNT(*) FROM shared_file_ref WHERE released_at IS NOT NULL", Integer.class)).isEqualTo(2);
    }

    @Test
    void hasActiveRefsIsFalseForUnregisteredObject() {
        assertThat(client.hasActiveRefs(BUCKET, "documents/own/upload.pdf")).isFalse();
    }

    private void addRef(UUID id, String tenant, String key) {
        JdbcTemplate jdbc = new JdbcTemplate(client.dataSource());
        jdbc.update("INSERT INTO shared_file_object (bucket_name, object_key) VALUES (?, ?) ON CONFLICT DO NOTHING", BUCKET, key);
        jdbc.update("INSERT INTO shared_file_ref (file_metadata_id, tenant_id, document_id, bucket_name, object_key, source) "
            + "VALUES (?, ?, ?, ?, ?, 'national_incoming')", id, tenant, UUID.randomUUID(), BUCKET, key);
    }

    private boolean zeroRefSinceIsSet(String key) {
        return Boolean.TRUE.equals(new JdbcTemplate(client.dataSource()).queryForObject(
            "SELECT zero_ref_since IS NOT NULL FROM shared_file_object WHERE object_key = ?", Boolean.class, key));
    }

    private static HikariDataSource pool(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }
}
