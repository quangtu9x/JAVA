package com.td.worker.infrastructure.fileregistry;

import com.td.worker.config.WorkerProperties;
import com.td.worker.infrastructure.fileregistry.SharedFileRegistry.SharedFileRef;
import com.td.worker.infrastructure.minio.WorkerMinioClient;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.infrastructure.tenant.TenantRecord;
import com.td.worker.infrastructure.tenant.TenantRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.TaskScheduler;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu người dùng 22/10/2026 (đính kèm dùng chung, chỉ xóa khi hết đơn vị): {@link SharedFileRegistry} +
 * {@link SharedFileGcJob} trên Postgres THẬT — 1 database sổ đăng ký (schema = migration td-web
 * {@code db/migration-fileregistry}) + 2 database tenant.
 *
 * <p>Postgres: Testcontainers như các integration test khác; hoặc Postgres sẵn có qua
 * {@code -Dtd.test.postgres.url=jdbc:postgresql://host:port/postgres} ({@code td.test.postgres.user}/
 * {@code td.test.postgres.password}, user phải được CREATE DATABASE) — dùng khi máy không có Docker Linux.</p>
 */
class SharedFileGcJobIntegrationTest {

    private static final Path REGISTRY_MIGRATION =
        Path.of("..", "td-web", "src", "main", "resources", "db", "migration-fileregistry", "V1__Create_Shared_File_Registry.sql");
    private static final String BUCKET = "td-webapi-files";
    private static final String SUFFIX = Long.toString(System.nanoTime(), 36);

    private static PostgreSQLContainer<?> postgres;
    private static String baseUrl;
    private static String user;
    private static String password;
    private static HikariDataSource admin;
    private static HikariDataSource registryDs;
    private static HikariDataSource tenantA;
    private static HikariDataSource tenantB;

    private WorkerProperties props;
    private SharedFileRegistry registry;
    private TenantRegistry tenants;
    private TenantDataSourceManager tenantDataSources;
    private WorkerMinioClient minio;
    private SharedFileGcJob job;

    @BeforeAll
    static void startDatabases() {
        Assumptions.assumeTrue(Files.exists(REGISTRY_MIGRATION),
            "Bo qua: khong thay migration so dang ky tai " + REGISTRY_MIGRATION.toAbsolutePath());
        String external = System.getProperty("td.test.postgres.url");
        if (external != null && !external.isBlank()) {
            baseUrl = external;
            user = System.getProperty("td.test.postgres.user", "postgres");
            password = System.getProperty("td.test.postgres.password", "");
        } else {
            try {
                postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("postgres").withUsername("test").withPassword("test");
                postgres.start();
            } catch (Exception ex) {
                Assumptions.assumeTrue(false, "Bo qua SharedFileGcJobIntegrationTest: khong co Docker Linux container ("
                    + ex.getMessage() + ") - co the chay voi -Dtd.test.postgres.url");
                return;
            }
            baseUrl = postgres.getJdbcUrl();
            user = postgres.getUsername();
            password = postgres.getPassword();
        }
        admin = pool(baseUrl);
        for (String db : List.of("fr", "ta", "tb")) {
            new JdbcTemplate(admin).execute("CREATE DATABASE " + dbName(db));
        }
        registryDs = pool(urlFor(dbName("fr")));
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(REGISTRY_MIGRATION));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(registryDs);
        tenantA = pool(urlFor(dbName("ta")));
        tenantB = pool(urlFor(dbName("tb")));
        for (DataSource ds : List.of(tenantA, tenantB)) {
            new JdbcTemplate(ds).execute("""
                CREATE TABLE file_metadata (
                    id UUID PRIMARY KEY,
                    document_id UUID,
                    file_path VARCHAR(500) NOT NULL,
                    bucket_name VARCHAR(100),
                    is_shared BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        }
    }

    @AfterAll
    static void stopDatabases() {
        for (HikariDataSource ds : new HikariDataSource[]{registryDs, tenantA, tenantB}) {
            if (ds != null) {
                ds.close();
            }
        }
        if (admin != null) {
            for (String db : List.of("fr", "ta", "tb")) {
                new JdbcTemplate(admin).execute("DROP DATABASE IF EXISTS " + dbName(db));
            }
            admin.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        props = new WorkerProperties();
        props.getFileRegistry().setUrl(urlFor(dbName("fr")));
        props.getFileRegistry().setUsername(user);
        props.getFileRegistry().setPassword(password);
        registry = new SharedFileRegistry(props);

        tenants = mock(TenantRegistry.class);
        Map<String, TenantRecord> all = new LinkedHashMap<>();
        all.put("ta", new TenantRecord("ta", "A", "localhost", 5432, dbName("ta"), user, "x", null, true));
        all.put("tb", new TenantRecord("tb", "B", "localhost", 5432, dbName("tb"), user, "x", null, true));
        when(tenants.all()).thenReturn(all);
        when(tenants.isControlPlaneEnabled()).thenReturn(true);
        tenantDataSources = mock(TenantDataSourceManager.class);
        when(tenantDataSources.getExisting("ta")).thenReturn(Optional.of(tenantA));
        when(tenantDataSources.getExisting("tb")).thenReturn(Optional.of(tenantB));
        minio = mock(WorkerMinioClient.class);
        when(minio.isEnabled()).thenReturn(true);
        when(minio.bucketName()).thenReturn(BUCKET);
        job = new SharedFileGcJob(props, registry, tenants, tenantDataSources, minio, mock(TaskScheduler.class));
    }

    @AfterEach
    void cleanUp() {
        registry.destroy();
        new JdbcTemplate(registryDs).execute(
            "TRUNCATE shared_file_ref, shared_file_object, shared_file_backfill; UPDATE shared_file_gc_lease SET owner = NULL, lease_until = NULL");
        for (DataSource ds : List.of(tenantA, tenantB)) {
            new JdbcTemplate(ds).execute("TRUNCATE file_metadata");
        }
    }

    @Test
    void backfillMarksLegacyIncomingRowsSharedAndRegistersEveryReceiver() {
        UUID incomingA = insertRow(tenantA, UUID.randomUUID(), "national-incoming/T1/1-a.pdf", false);
        UUID incomingB = insertRow(tenantB, UUID.randomUUID(), "national-incoming/T1/1-a.pdf", false);
        UUID internalB = insertRow(tenantB, UUID.randomUUID(), "vbdi-delivery/H05/2026/10/01/d/x.docx", false);
        UUID forwardedA = insertRow(tenantA, UUID.randomUUID(), "documents/origin/y.pdf", true);
        UUID senderAudit = insertRow(tenantA, null, "vbdi-delivery/H05/2026/10/01/d/x.docx", false);
        UUID ownUpload = insertRow(tenantA, UUID.randomUUID(), "documents/own/z.pdf", false);

        job.run(false);

        assertThat(isShared(tenantA, incomingA)).isTrue();
        assertThat(isShared(tenantB, incomingB)).isTrue();
        assertThat(isShared(tenantB, internalB)).isTrue();
        assertThat(isShared(tenantA, senderAudit)).as("dong kiem toan phia gui (document_id NULL) giu nguyen").isFalse();
        assertThat(isShared(tenantA, ownUpload)).as("tep tu tai len giu nguyen").isFalse();
        assertThat(activeRefs("national-incoming/T1/1-a.pdf")).isEqualTo(2);
        assertThat(refIds()).containsExactlyInAnyOrder(incomingA, incomingB, internalB, forwardedA);
        assertThat(new JdbcTemplate(registryDs).queryForList("SELECT tenant_id FROM shared_file_backfill", String.class))
            .containsExactlyInAnyOrder("ta", "tb");
        verify(minio, never()).bucketName(); // moi dong deu co bucket_name
    }

    @Test
    void objectIsDeletedOnlyAfterEveryUnitReleasedAndGracePeriodPassed() throws Exception {
        String key = "national-incoming/T2/1-b.pdf";
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        registry.register("ta", SharedFileRegistry.SOURCE_NATIONAL_INCOMING, List.of(ref(a, key)));
        registry.register("tb", SharedFileRegistry.SOURCE_NATIONAL_INCOMING, List.of(ref(b, key)));
        markBackfilled();

        // Don vi A xoa vinh vien van ban: don vi B van dung -> khong phai ung vien.
        release(a);
        ageReleases(key, 8);
        job.run(true);
        verify(minio, never()).removeObject(anyString(), anyString());

        // Don vi B cung xoa, nhung moi 1 ngay -> chua qua 7 ngay.
        release(b);
        ageReleases(key, 1);
        job.run(true);
        verify(minio, never()).removeObject(anyString(), anyString());

        ageReleases(key, 8);
        job.run(true);
        verify(minio).removeObject(BUCKET, key);
        assertThat(new JdbcTemplate(registryDs).queryForObject(
            "SELECT deleted_at IS NOT NULL FROM shared_file_object WHERE object_key = ?", Boolean.class, key)).isTrue();
    }

    @Test
    void objectStillHavingSharedRowWithoutRefIsRepairedNotDeleted() throws Exception {
        String key = "vbdi-delivery/H05/2026/10/02/d/c.pdf";
        UUID a = insertRow(tenantA, UUID.randomUUID(), key, true);
        UUID b = insertRow(tenantB, UUID.randomUUID(), key, true);
        registry.register("ta", SharedFileRegistry.SOURCE_VBDI_DELIVERY, List.of(ref(a, key)));
        markBackfilled(); // tham chieu cua B bi mat (vd ghi sau commit loi)
        release(a);
        ageReleases(key, 8);

        job.run(true);

        verify(minio, never()).removeObject(anyString(), anyString());
        assertThat(new JdbcTemplate(registryDs).queryForObject(
            "SELECT source FROM shared_file_ref WHERE file_metadata_id = ? AND released_at IS NULL", String.class, b))
            .isEqualTo(SharedFileRegistry.SOURCE_GC_REPAIR);
    }

    @Test
    void objectStillOwnedByOriginDocumentIsPostponed() throws Exception {
        String key = "documents/origin/owned.pdf";
        insertRow(tenantA, UUID.randomUUID(), key, false); // van ban goc (chu so huu) da "Chuyen xu ly" di
        UUID forwarded = UUID.randomUUID();
        registry.register("tb", SharedFileRegistry.SOURCE_FORWARD_UNIT, List.of(ref(forwarded, key)));
        markBackfilled();
        release(forwarded);
        ageReleases(key, 8);

        job.run(true);

        verify(minio, never()).removeObject(anyString(), anyString());
        assertThat(new JdbcTemplate(registryDs).queryForObject(
            "SELECT zero_ref_since > now() - interval '1 hour' FROM shared_file_object WHERE object_key = ?",
            Boolean.class, key)).as("hoan them 1 grace-period").isTrue();
    }

    @Test
    void unreachableTenantStopsTheRunWithoutDeletingAnything() throws Exception {
        String key = "national-incoming/T3/1-c.pdf";
        UUID a = UUID.randomUUID();
        registry.register("ta", SharedFileRegistry.SOURCE_NATIONAL_INCOMING, List.of(ref(a, key)));
        markBackfilled();
        release(a);
        ageReleases(key, 8);
        HikariDataSource broken = pool(urlFor("db_khong_ton_tai_" + SUFFIX));
        broken.close();
        when(tenantDataSources.getExisting("tb")).thenReturn(Optional.of(broken));

        job.run(true);

        verify(minio, never()).removeObject(anyString(), anyString());
        assertThat(new JdbcTemplate(registryDs).queryForObject(
            "SELECT deleted_at IS NULL FROM shared_file_object WHERE object_key = ?", Boolean.class, key)).isTrue();
    }

    @Test
    void minioFailureLeavesObjectForNextRun() throws Exception {
        String key = "national-incoming/T4/1-d.pdf";
        UUID a = UUID.randomUUID();
        registry.register("ta", SharedFileRegistry.SOURCE_NATIONAL_INCOMING, List.of(ref(a, key)));
        markBackfilled();
        release(a);
        ageReleases(key, 8);
        org.mockito.Mockito.doThrow(new RuntimeException("minio down")).when(minio).removeObject(any(), any());

        job.run(true);

        assertThat(new JdbcTemplate(registryDs).queryForObject(
            "SELECT deleted_at IS NULL FROM shared_file_object WHERE object_key = ?", Boolean.class, key)).isTrue();
    }

    @Test
    void registerIsIdempotentPerFileMetadataRow() {
        String key = "national-incoming/T5/1-e.pdf";
        UUID a = UUID.randomUUID();
        registry.register("ta", SharedFileRegistry.SOURCE_NATIONAL_INCOMING, List.of(ref(a, key)));
        registry.register("ta", SharedFileRegistry.SOURCE_NATIONAL_INCOMING, List.of(ref(a, key)));
        assertThat(activeRefs(key)).isEqualTo(1);
    }

    // ---- helpers ----

    private SharedFileRef ref(UUID id, String key) {
        return new SharedFileRef(id, UUID.randomUUID(), BUCKET, key);
    }

    /** Cùng SQL với {@code SharedFileRegistryClient#release} phía td-web. */
    private void release(UUID fileMetadataId) {
        JdbcTemplate jdbc = new JdbcTemplate(registryDs);
        jdbc.update("UPDATE shared_file_ref SET released_at = now() WHERE file_metadata_id = ? AND released_at IS NULL",
            fileMetadataId);
        jdbc.update("""
            UPDATE shared_file_object o SET zero_ref_since = now()
            WHERE o.zero_ref_since IS NULL AND o.deleted_at IS NULL
              AND EXISTS (SELECT 1 FROM shared_file_ref r WHERE r.bucket_name = o.bucket_name
                          AND r.object_key = o.object_key AND r.file_metadata_id = ?)
              AND NOT EXISTS (SELECT 1 FROM shared_file_ref r WHERE r.bucket_name = o.bucket_name
                              AND r.object_key = o.object_key AND r.released_at IS NULL)
            """, fileMetadataId);
    }

    private void ageReleases(String key, int days) {
        JdbcTemplate jdbc = new JdbcTemplate(registryDs);
        jdbc.update("UPDATE shared_file_ref SET released_at = now() - (? * interval '1 day') "
            + "WHERE object_key = ? AND released_at IS NOT NULL", days, key);
        jdbc.update("UPDATE shared_file_object SET zero_ref_since = now() - (? * interval '1 day') "
            + "WHERE object_key = ? AND zero_ref_since IS NOT NULL", days, key);
    }

    private void markBackfilled() {
        registry.markBackfilled("ta", 0, 0);
        registry.markBackfilled("tb", 0, 0);
    }

    private UUID insertRow(DataSource ds, UUID documentId, String path, boolean shared) {
        UUID id = UUID.randomUUID();
        new JdbcTemplate(ds).update(
            "INSERT INTO file_metadata (id, document_id, file_path, bucket_name, is_shared) VALUES (?, ?, ?, ?, ?)",
            id, documentId, path, BUCKET, shared);
        return id;
    }

    private boolean isShared(DataSource ds, UUID id) {
        return Boolean.TRUE.equals(new JdbcTemplate(ds).queryForObject(
            "SELECT is_shared FROM file_metadata WHERE id = ?", Boolean.class, id));
    }

    private int activeRefs(String key) {
        return new JdbcTemplate(registryDs).queryForObject(
            "SELECT COUNT(*) FROM shared_file_ref WHERE object_key = ? AND released_at IS NULL", Integer.class, key);
    }

    private List<UUID> refIds() {
        return new JdbcTemplate(registryDs).queryForList("SELECT file_metadata_id FROM shared_file_ref", UUID.class);
    }

    private static String dbName(String logical) {
        return "sfgc_" + logical + "_" + SUFFIX;
    }

    private static String urlFor(String database) {
        int slash = baseUrl.indexOf('/', "jdbc:postgresql://".length());
        int query = baseUrl.indexOf('?', slash);
        return baseUrl.substring(0, slash + 1) + database + (query < 0 ? "" : baseUrl.substring(query));
    }

    private static HikariDataSource pool(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(3000);
        return new HikariDataSource(config);
    }
}
