package com.td.worker.search;

import com.td.worker.config.WorkerProperties;
import com.td.worker.infrastructure.minio.WorkerMinioClient;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 17/10/2026 — làn trích nội dung tệp trên PostgreSQL thật (migration V1.0.72/73/74, schema tạm), MinIO giả lập. Chỉ
 * chạy khi khai SEARCH_SYNC_IT_JDBC_URL/_USERNAME/_PASSWORD.
 */
class FileExtractIntegrationTest {

    private static final Path MIGRATIONS = Path.of("../td-web/src/main/resources/db/migration");

    private static HikariDataSource dataSource;
    private static HikariDataSource adminDataSource;
    private static JdbcTemplate jdbc;
    private static String tempSchema;

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private FileExtractService service;
    private FileContentExtractor extractor;

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv("SEARCH_SYNC_IT_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Bỏ qua: chưa khai SEARCH_SYNC_IT_JDBC_URL");
        tempSchema = "file_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        HikariConfig admin = new HikariConfig();
        admin.setJdbcUrl(url);
        admin.setUsername(System.getenv("SEARCH_SYNC_IT_JDBC_USERNAME"));
        admin.setPassword(System.getenv("SEARCH_SYNC_IT_JDBC_PASSWORD"));
        admin.setMaximumPoolSize(1);
        adminDataSource = new HikariDataSource(admin);
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + tempSchema);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + tempSchema);
        config.setUsername(admin.getUsername());
        config.setPassword(admin.getPassword());
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE TABLE documents (id UUID NOT NULL, title VARCHAR(300), document_type VARCHAR(100) NOT NULL,
                dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb, PRIMARY KEY (id, document_type)) PARTITION BY LIST (document_type);
            CREATE TABLE documents_default PARTITION OF documents DEFAULT;
            CREATE TABLE file_metadata (id UUID PRIMARY KEY, original_filename VARCHAR(255) NOT NULL,
                stored_filename VARCHAR(255) NOT NULL, file_path VARCHAR(500) NOT NULL, file_size BIGINT NOT NULL,
                content_type VARCHAR(255), bucket_name VARCHAR(100) NOT NULL, uploaded_at TIMESTAMP NOT NULL DEFAULT now(),
                download_count BIGINT DEFAULT 0, document_id UUID, superseded_at TIMESTAMPTZ);
            """);
        for (String table : List.of("vbdi_documents", "duthao_documents", "hosocv_documents", "phieutrinhtd_documents",
                "vbcddh_documents", "hosoluutru_documents")) {
            jdbc.execute("CREATE TABLE " + table + " (id UUID PRIMARY KEY, title VARCHAR(300))");
        }
        for (String migration : List.of("V1.0.72__Create_Search_Index_Queue.sql", "V1.0.73__Search_Index_Queue_Notify.sql",
                "V1.0.74__Create_File_Content_Extracts.sql")) {
            jdbc.execute(Files.readString(MIGRATIONS.resolve(migration)));
        }
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (adminDataSource != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + tempSchema + " CASCADE");
            adminDataSource.close();
        }
    }

    @BeforeEach
    void init() {
        jdbc.execute("DELETE FROM file_content_extracts; DELETE FROM file_metadata; DELETE FROM documents; DELETE FROM vbdi_documents");
        jdbc.execute("DELETE FROM search_index_queue");
        SearchSyncProperties props = new SearchSyncProperties();
        props.getFileExtract().setEnabled(true);
        extractor = new FileContentExtractor(props.getFileExtract());
        WorkerMinioClient minio = new WorkerMinioClient(new WorkerProperties()) {
            @Override
            public InputStream openObject(String bucket, String objectName) {
                byte[] bytes = objects.get(objectName);
                if (bytes == null) {
                    throw new IllegalStateException("NoSuchKey " + objectName);
                }
                return new ByteArrayInputStream(bytes);
            }
        };
        service = new FileExtractService(props, new FileExtractStore(), new SearchIndexQueueStore(), extractor, minio,
            null, new SimpleMeterRegistry());
    }

    private UUID insertFile(UUID documentId, String path, String text) {
        UUID id = UUID.randomUUID();
        objects.put(path, text.getBytes(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO file_metadata (id, original_filename, stored_filename, file_path, file_size, content_type, "
            + "bucket_name, document_id) VALUES (?, ?, ?, ?, ?, 'text/plain', 'td-webapi-files', ?)",
            id, path, path, path, (long) text.length(), documentId);
        return id;
    }

    private List<String> queued(String entityType) {
        return jdbc.queryForList("SELECT entity_id::text FROM search_index_queue WHERE entity_type = ?", String.class, entityType);
    }

    @Test
    void upload_shouldExtractStoreAndRequeueParentDocument() throws Exception {
        UUID vbdi = UUID.randomUUID();
        jdbc.update("INSERT INTO vbdi_documents (id, title) VALUES (?, 'VB di')", vbdi);
        jdbc.execute("DELETE FROM search_index_queue");
        UUID file = insertFile(vbdi, "a/cv.txt", "Báo cáo tình hình triển khai phần mềm");
        assertThat(queued("file")).containsExactly(file.toString());

        service.extractTenant(dataSource, false);

        Map<String, Object> extract = jdbc.queryForMap("SELECT status, content, document_id::text AS doc FROM file_content_extracts WHERE file_id = ?", file);
        assertThat(extract.get("status")).isEqualTo("DONE");
        assertThat((String) extract.get("content")).contains("triển khai phần mềm");
        assertThat(extract.get("doc")).isEqualTo(vbdi.toString());
        assertThat(queued("file")).isEmpty();
        assertThat(queued("vbdi")).as("văn bản cha được nạp lại ES kèm nội dung tệp").containsExactly(vbdi.toString());

        try (Connection connection = dataSource.getConnection()) {
            Map<UUID, String> texts = new FileExtractStore().filesText(connection, List.of(vbdi), 1000);
            assertThat(texts.get(vbdi)).startsWith("a/cv.txt: ").contains("tình hình triển khai");
        }
    }

    @Test
    void downloadCountUpdate_shouldNotRequeue_butPathChangeShould() {
        UUID file = insertFile(null, "b.txt", "x");
        jdbc.execute("DELETE FROM search_index_queue");

        jdbc.update("UPDATE file_metadata SET download_count = download_count + 1 WHERE id = ?", file);
        assertThat(queued("file")).isEmpty();

        jdbc.update("UPDATE file_metadata SET file_path = 'b2.txt' WHERE id = ?", file);
        assertThat(queued("file")).containsExactly(file.toString());
    }

    @Test
    void deletedFile_shouldRemoveExtractAndRequeueParent() throws Exception {
        UUID vbdi = UUID.randomUUID();
        jdbc.update("INSERT INTO vbdi_documents (id, title) VALUES (?, 'VB di')", vbdi);
        UUID file = insertFile(vbdi, "c.txt", "noi dung");
        service.extractTenant(dataSource, false);
        jdbc.execute("DELETE FROM search_index_queue");

        jdbc.update("DELETE FROM file_metadata WHERE id = ?", file);
        service.extractTenant(dataSource, false);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM file_content_extracts", Long.class)).isZero();
        assertThat(queued("vbdi")).containsExactly(vbdi.toString());
    }

    @Test
    void missingObject_shouldRetryThenFail() throws Exception {
        UUID file = insertFile(null, "d.txt", "x");
        jdbc.update("UPDATE file_metadata SET document_id = ? WHERE id = ?", UUID.randomUUID(), file);
        objects.clear();
        jdbc.execute("UPDATE search_index_queue SET attempts = 4");

        service.extractTenant(dataSource, false);

        assertThat(jdbc.queryForObject("SELECT status FROM file_content_extracts WHERE file_id = ?", String.class, file))
            .isEqualTo("FAILED");
        assertThat(queued("file")).isEmpty();
    }

    // Lượt quét dự phòng: tệp chưa từng trích (vd có từ trước khi bật tính năng) được đưa vào hàng đợi đúng 1 lần.
    @Test
    void sweep_shouldEnqueueFilesNeverExtracted_once() throws Exception {
        UUID file = insertFile(null, "e.txt", "x");
        jdbc.execute("DELETE FROM search_index_queue");
        try (Connection connection = dataSource.getConnection()) {
            FileExtractStore store = new FileExtractStore();
            assertThat(store.enqueueMissing(connection)).isEqualTo(1);
            assertThat(store.enqueueMissing(connection)).isZero();
        }
        assertThat(queued("file")).containsExactly(file.toString());
    }

    // Làn đồng bộ văn bản KHÔNG lấy dòng 'file' (làn trích tệp riêng xử lý).
    @Test
    void documentLaneClaim_shouldSkipFileRows() throws Exception {
        insertFile(null, "f.txt", "x");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            assertThat(new SearchIndexQueueStore().claim(connection, 100)).isEmpty();
            connection.rollback();
        }
    }
}
