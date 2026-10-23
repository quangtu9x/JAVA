package com.td.worker.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đầu cuối: PostgreSQL (schema tạm) + migration V1.0.72 + Elasticsearch THẬT với tiền tố index ngẫu
 * nhiên, xoá sạch sau khi chạy. Chỉ chạy khi khai SEARCH_SYNC_IT_JDBC_URL/USERNAME/PASSWORD và
 * SEARCH_SYNC_IT_ES_URIS (không chạy trong build thường).
 */
class SearchIndexSyncEndToEndTest {

    private static final Path MIGRATION = Path.of(
        "../td-web/src/main/resources/db/migration/V1.0.72__Create_Search_Index_Queue.sql");
    private static final Path MIGRATION_NOTIFY = Path.of(
        "../td-web/src/main/resources/db/migration/V1.0.73__Search_Index_Queue_Notify.sql");
    private static final String TENANT = "IT";

    private static HikariDataSource dataSource;
    private static HikariDataSource adminDataSource;
    private static JdbcTemplate jdbc;
    private static String tempSchema;
    private static String prefix;
    private static SearchSyncProperties props;
    private static ElasticsearchHttpClient client;
    private static String esUri;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv("SEARCH_SYNC_IT_JDBC_URL");
        esUri = System.getenv("SEARCH_SYNC_IT_ES_URIS");
        Assumptions.assumeTrue(url != null && !url.isBlank() && esUri != null && !esUri.isBlank(),
            "Bỏ qua: chưa khai SEARCH_SYNC_IT_JDBC_URL / SEARCH_SYNC_IT_ES_URIS");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        tempSchema = "search_e2e_" + suffix;
        prefix = "hscvit" + suffix;

        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setJdbcUrl(url);
        adminConfig.setUsername(System.getenv("SEARCH_SYNC_IT_JDBC_USERNAME"));
        adminConfig.setPassword(System.getenv("SEARCH_SYNC_IT_JDBC_PASSWORD"));
        adminConfig.setMaximumPoolSize(1);
        adminDataSource = new HikariDataSource(adminConfig);
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + tempSchema);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + tempSchema);
        config.setUsername(adminConfig.getUsername());
        config.setPassword(adminConfig.getPassword());
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE TABLE documents (
                id UUID NOT NULL, title VARCHAR(300), document_type VARCHAR(100) NOT NULL,
                dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb, files JSONB,
                created_on TIMESTAMPTZ NOT NULL DEFAULT now(), version_no BIGINT NOT NULL DEFAULT 1,
                deleted_on TIMESTAMPTZ, PRIMARY KEY (id, document_type)
            ) PARTITION BY LIST (document_type);
            CREATE TABLE documents_default PARTITION OF documents DEFAULT;
            """);
        for (String table : List.of("vbdi_documents", "duthao_documents", "hosocv_documents",
                "phieutrinhtd_documents", "vbcddh_documents", "hosoluutru_documents")) {
            jdbc.execute("CREATE TABLE " + table + " (id UUID PRIMARY KEY, title VARCHAR(300))");
        }
        jdbc.execute(Files.readString(MIGRATION));
        jdbc.execute(Files.readString(MIGRATION_NOTIFY));
        // 17/10/2026: trích nội dung tệp (files_text) — bảng file_metadata tối thiểu + migration V1.0.74.
        jdbc.execute("CREATE TABLE file_metadata (id UUID PRIMARY KEY, original_filename VARCHAR(255) NOT NULL, "
            + "stored_filename VARCHAR(255) NOT NULL, file_path VARCHAR(500) NOT NULL, file_size BIGINT NOT NULL, "
            + "content_type VARCHAR(255), bucket_name VARCHAR(100) NOT NULL, uploaded_at TIMESTAMP NOT NULL DEFAULT now(), "
            + "document_id UUID, superseded_at TIMESTAMPTZ)");
        jdbc.execute(Files.readString(Path.of("../td-web/src/main/resources/db/migration/V1.0.74__Create_File_Content_Extracts.sql")));

        props = new SearchSyncProperties();
        props.setUris(List.of(esUri));
        props.setIndexPrefix(prefix);
        props.setNumberOfReplicas(0);
        props.setReconcileInterval(java.time.Duration.ZERO);
        props.setEntities(Map.of("documents", "documents"));
        client = new ElasticsearchHttpClient(props, objectMapper);
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            for (int v = 1; v <= 2; v++) {
                try {
                    client.deleteIndex(SearchIndexNaming.physical(SearchIndexNaming.alias(prefix, TENANT, "documents"), v));
                } catch (Exception ignored) {
                    // dọn dẹp tốt nhất có thể
                }
            }
        }
        if (dataSource != null) {
            dataSource.close();
        }
        if (adminDataSource != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + tempSchema + " CASCADE");
            adminDataSource.close();
        }
    }

    private SearchIndexSyncService newService(SearchSyncProperties p) {
        return new SearchIndexSyncService(p, new ElasticsearchHttpClient(p, objectMapper), new SearchIndexQueueStore(),
            new SearchDocumentMapper(objectMapper, p), null, null, objectMapper, new SimpleMeterRegistry());
    }

    @Test
    void fullLifecycle() throws Exception {
        jdbc.execute("DELETE FROM documents"); // độc lập với thứ tự chạy các test khác
        String alias = SearchIndexNaming.alias(prefix, TENANT, "documents");
        UUID kept = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        // Dữ liệu có TRƯỚC khi có index — phải được nạp lần đầu (bootstrap).
        jdbc.update("INSERT INTO documents (id, title, document_type, dynamic_fields, files) VALUES (?, 'Công văn số 12 về việc triển khai', 'vbden', '{\"so_ky_hieu\":\"12/UBND\"}', '[{\"name\":\"a.pdf\"}]')", kept);
        jdbc.update("INSERT INTO documents (id, title, document_type, files) VALUES (?, 'Tờ trình', 'vbden', '\"chuoi\"')", removed);
        jdbc.execute("DELETE FROM search_index_queue"); // giả lập dữ liệu cũ có trước khi bật đồng bộ

        SearchIndexSyncService service = newService(props);
        service.syncTenant(TENANT, dataSource, true); // tạo index + đưa toàn bộ bảng vào hàng đợi
        service.syncTenant(TENANT, dataSource, true); // xử lý hàng đợi

        assertThat(client.aliasTargets(alias)).containsExactly(alias + "-v1");
        waitFor(() -> client.count(alias), 2);
        assertThat(queueSize()).isZero();

        // Tìm không dấu "cong van" ra "Công văn"; tìm theo field trong jsonb (flattened).
        assertThat(searchIds(alias, "{\"query\":{\"match\":{\"search_text\":\"cong van trien khai\"}}}")).contains(kept.toString());
        assertThat(searchIds(alias, "{\"query\":{\"term\":{\"dynamic_fields.so_ky_hieu\":\"12/UBND\"}}}")).containsExactly(kept.toString());

        // Sửa + xoá → ES theo đúng trạng thái DB.
        jdbc.update("UPDATE documents SET title = 'Công văn đã sửa' WHERE id = ?", kept);
        jdbc.update("DELETE FROM documents WHERE id = ?", removed);
        service.syncTenant(TENANT, dataSource, true);
        waitFor(() -> client.count(alias), 1);
        assertThat(searchIds(alias, "{\"query\":{\"match\":{\"title\":\"da sua\"}}}")).containsExactly(kept.toString());

        // Bản cũ đến muộn (version nhỏ hơn) không đè bản mới.
        List<ElasticsearchHttpClient.BulkItemResult> stale = client.bulk(
            "{\"index\":{\"_index\":\"" + alias + "\",\"_id\":\"" + kept + "\",\"version\":1,\"version_type\":\"external_gte\"}}\n"
                + "{\"id\":\"" + kept + "\",\"title\":\"BAN CU\"}\n");
        assertThat(stale.get(0).status()).isEqualTo(409);
        assertThat(stale.get(0).isDone()).isTrue();

        // Đổi mapping version → index mới, alias chuyển, nạp lại toàn bộ.
        SearchSyncProperties v2 = new SearchSyncProperties();
        v2.setUris(props.getUris());
        v2.setIndexPrefix(prefix);
        v2.setNumberOfReplicas(0);
        v2.setReconcileInterval(java.time.Duration.ZERO);
        v2.setEntities(props.getEntities());
        v2.setMappingVersion(2);
        SearchIndexSyncService serviceV2 = newService(v2);
        serviceV2.syncTenant(TENANT, dataSource, true);
        serviceV2.syncTenant(TENANT, dataSource, true);
        assertThat(client.aliasTargets(alias)).containsExactly(alias + "-v2");
        waitFor(() -> client.count(alias), 1);

        // Đối soát: ES thừa 1 document không còn trong DB → bị dọn.
        UUID orphan = UUID.randomUUID();
        client.bulk("{\"index\":{\"_index\":\"" + alias + "\",\"_id\":\"" + orphan + "\"}}\n{\"id\":\"" + orphan + "\"}\n");
        waitFor(() -> client.count(alias), 2);
        serviceV2.reconcile(TENANT, dataSource, "documents", "documents");
        serviceV2.syncTenant(TENANT, dataSource, true);
        waitFor(() -> client.count(alias), 1);
    }

    // Đường CHÍNH theo sự kiện: LISTEN/NOTIFY → coordinator → ES, KHÔNG có quét định kỳ (chỉ 1 lượt bootstrap).
    @Test
    void notifyDriven_shouldIndexWithinSeconds_withoutPolling() throws Exception {
        SearchSyncProperties p = new SearchSyncProperties();
        p.setUris(props.getUris());
        p.setIndexPrefix(prefix + "n");
        p.setNumberOfReplicas(0);
        p.setReconcileInterval(java.time.Duration.ZERO);
        p.setEntities(Map.of("documents", "documents"));
        String alias = SearchIndexNaming.alias(p.getIndexPrefix(), TENANT, "documents");
        SearchIndexSyncService service = newService(p);
        try {
            service.syncTenant(TENANT, dataSource, true); // tạo index + nạp lần đầu
            for (int i = 0; i < 50 && service.syncTenant(TENANT, dataSource, false) != null; i++) {
                // xử lý hết hàng đợi hiện có
            }
            long before = waitStable(() -> client.count(alias));

            String url = System.getenv("SEARCH_SYNC_IT_JDBC_URL");
            String user = System.getenv("SEARCH_SYNC_IT_JDBC_USERNAME");
            String pass = System.getenv("SEARCH_SYNC_IT_JDBC_PASSWORD");
            try (SearchSyncCoordinator coordinator = new SearchSyncCoordinator(
                    (tenant, sweep) -> service.syncTenant(TENANT, dataSource, sweep), 2, java.time.Duration.ofSeconds(1));
                 SearchNotificationListener listener = new SearchNotificationListener(
                     tenant -> java.sql.DriverManager.getConnection(url, user, pass), t -> coordinator.request(t, false), "search_index",
                     java.time.Duration.ofSeconds(30), java.time.Duration.ofSeconds(1))) {
                listener.listenTo(List.of(TENANT));
                long deadline = System.currentTimeMillis() + 10_000;
                while (listener.connectedCount() < 1 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50);
                }
                assertThat(listener.connectedCount()).isEqualTo(1);

                UUID id = UUID.randomUUID();
                long started = System.currentTimeMillis();
                jdbc.update("INSERT INTO documents (id, title, document_type) VALUES (?, 'Quyết định mới', 'vbden')", id);
                waitFor(() -> client.count(alias), before + 1);
                long latencyMs = System.currentTimeMillis() - started;
                System.out.println("[search-sync e2e] commit -> tìm thấy trên ES: " + latencyMs + " ms");
                assertThat(latencyMs).isLessThan(3_000);
                assertThat(queueSize()).isZero();
            }
        } finally {
            client.deleteIndex(SearchIndexNaming.physical(alias, 1));
        }
    }

    /**
     * 17/10/2026 — HỢP ĐỒNG với td-web (ElasticsearchAdvancedSearchIndex#buildSearchBody): các dạng truy vấn td-web gửi
     * (terms trên norm_dyn/norm_col, range trên date_dyn/date_col, exists, cross_fields + bool_prefix trên kw.*, sort)
     * phải chạy đúng trên mapping do worker tạo. Đổi tên/kiểu trường ở 1 phía mà quên phía kia → test này hỏng.
     */
    @Test
    void advancedSearchQueryContract() throws Exception {
        SearchSyncProperties p = new SearchSyncProperties();
        p.setUris(props.getUris());
        p.setIndexPrefix(prefix + "c");
        p.setNumberOfReplicas(0);
        p.setReconcileInterval(java.time.Duration.ZERO);
        p.setEntities(Map.of("documents", "documents"));
        String alias = SearchIndexNaming.alias(p.getIndexPrefix(), TENANT, "documents");
        SearchIndexSyncService service = newService(p);
        jdbc.execute("DELETE FROM documents");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        jdbc.update("INSERT INTO documents (id, title, document_type, dynamic_fields) VALUES (?, 'Công văn về việc triển khai phần mềm', 'vbden', "
            + "'{\"unit_process\":[\"H05.01\"],\"user_process\":\"NguyenVanA;tranb\",\"received_date\":\"15/10/2026 08:30:00\","
            + "\"code\":\"12/UBND-VP\",\"priority\":\"Khẩn\",\"is_revoke\":\"\"}')", a);
        jdbc.update("INSERT INTO documents (id, title, document_type, dynamic_fields) VALUES (?, 'Quyết định khen thưởng', 'vbden', "
            + "'{\"unit_process\":\"H05.02;H05.01\",\"received_date\":\"2026-09-01\",\"code\":\"99/QD\",\"is_revoke\":\"1\"}')", b);
        jdbc.update("INSERT INTO documents (id, title, document_type, deleted_on) VALUES (?, 'Công văn đã xoá', 'vbden', now())", deleted);
        // Tệp đính kèm của a đã trích (như làn trích tệp ghi) → files_text của a.
        UUID fileOfA = UUID.randomUUID();
        jdbc.update("INSERT INTO file_metadata (id, original_filename, stored_filename, file_path, file_size, bucket_name, document_id) "
            + "VALUES (?, 'hopdong.pdf', ?, ?, 10, 'b', ?)", fileOfA, fileOfA.toString(), fileOfA.toString(), a);
        jdbc.update("INSERT INTO file_content_extracts (file_id, document_id, status, content, content_chars) "
            + "VALUES (?, ?, 'DONE', 'Hợp đồng mua sắm thiết bị phòng họp trực tuyến', 45)", fileOfA, a);
        jdbc.update("DELETE FROM search_index_queue WHERE entity_type = 'file'"); // làn tệp không chạy trong test này
        try {
            service.syncTenant(TENANT, dataSource, true);
            for (int i = 0; i < 20 && service.syncTenant(TENANT, dataSource, false) != null; i++) {
                // nạp hết
            }
            waitFor(() -> client.count(alias), 3);

            String notDeleted = "{\"bool\":{\"must_not\":[{\"bool\":{\"minimum_should_match\":1,\"should\":["
                + "{\"exists\":{\"field\":\"norm_col.deleted_on\"}},{\"exists\":{\"field\":\"date_col.deleted_on\"}}]}}]}}";
            // Phạm vi tổ chức (mảng JSON lẫn chuỗi "a;b") + chưa xoá + đúng loại.
            assertThat(searchIds(alias, query("[" + notDeleted + ",{\"terms\":{\"norm_col.document_type\":[\"vbden\"]}},"
                + "{\"terms\":{\"norm_dyn.unit_process\":[\"h05.01\"]}}]", null)))
                .containsExactlyInAnyOrder(a.toString(), b.toString());
            // Chưa thu hồi: is_revoke rỗng không có token → không bị loại.
            assertThat(searchIds(alias, query("[" + notDeleted + ",{\"bool\":{\"must_not\":[{\"terms\":{\"norm_dyn.is_revoke\":[\"1\"]}}]}}]", null)))
                .containsExactly(a.toString());
            // Thành viên danh sách "a;b", không phân biệt hoa thường.
            assertThat(searchIds(alias, query("[{\"terms\":{\"norm_dyn.user_process\":[\"nguyenvana\"]}}]", null)))
                .containsExactly(a.toString());
            // Khoảng ngày trên 2 dạng lưu (dd/MM/yyyy giờ và ISO).
            assertThat(searchIds(alias, query("[{\"range\":{\"date_dyn.received_date\":{\"gte\":\"2026-10-01\",\"lte\":\"2026-10-31\"}}}]", null)))
                .containsExactly(a.toString());
            // Bằng giá trị có dấu.
            assertThat(searchIds(alias, query("[{\"terms\":{\"norm_dyn.priority\":[\"khẩn\"]}}]", null)))
                .containsExactly(a.toString());
            // Exists trên flattened.
            assertThat(searchIds(alias, query("[{\"exists\":{\"field\":\"norm_dyn.user_process\"}}]", null)))
                .containsExactly(a.toString());
            // Từ khoá: bỏ dấu, đủ mọi từ, từ cuối gõ dở, trên kw.subject (gộp cột title) + kw.code.
            String keyword = "{\"bool\":{\"minimum_should_match\":1,\"should\":["
                + "{\"multi_match\":{\"query\":\"%s\",\"type\":\"cross_fields\",\"operator\":\"and\",\"fields\":[\"kw.subject\",\"kw.code\"]}},"
                + "{\"multi_match\":{\"query\":\"%s\",\"type\":\"bool_prefix\",\"operator\":\"and\",\"fields\":[\"kw.subject\",\"kw.code\"]}}]}}";
            assertThat(searchIds(alias, query("[" + notDeleted + "]", String.format(keyword, "cong van trien khai", "cong van trien khai"))))
                .containsExactly(a.toString());
            assertThat(searchIds(alias, query("[]", String.format(keyword, "phan me", "phan me"))))
                .as("từ cuối gõ dở").containsExactly(a.toString());
            assertThat(searchIds(alias, query("[]", String.format(keyword, "12 ubnd", "12 ubnd"))))
                .containsExactly(a.toString());
            // Nội dung TỆP đính kèm (files_text): td-web thêm trường này vào từ khoá khi SEARCH_ES_INCLUDE_FILE_CONTENT.
            String withFiles = "{\"bool\":{\"minimum_should_match\":1,\"should\":["
                + "{\"multi_match\":{\"query\":\"%s\",\"type\":\"cross_fields\",\"operator\":\"and\",\"fields\":[\"kw.subject\",\"files_text\"]}},"
                + "{\"multi_match\":{\"query\":\"%s\",\"type\":\"bool_prefix\",\"operator\":\"and\",\"fields\":[\"kw.subject\",\"files_text\"]}}]}}";
            assertThat(searchIds(alias, query("[]", String.format(withFiles, "mua sam thiet bi phong hop", "mua sam thiet bi phong hop"))))
                .as("tìm thấy nhờ nội dung tệp, không dấu").containsExactly(a.toString());
            assertThat(searchIds(alias, query("[]", String.format(keyword, "mua sam thiet bi", "mua sam thiet bi"))))
                .as("không thêm files_text thì không tìm trong tệp").isEmpty();
            // Sắp xếp td-web dùng (unmapped_type để trường thiếu không lỗi).
            String sorted = "{\"size\":10,\"_source\":false,\"query\":{\"match_all\":{}},\"sort\":["
                + "{\"created_on\":{\"order\":\"desc\",\"missing\":\"_last\",\"unmapped_type\":\"date\"}},"
                + "{\"sort_promulgation_date\":{\"order\":\"desc\",\"missing\":\"_last\",\"unmapped_type\":\"date\"}},"
                + "{\"sort_document_number\":{\"order\":\"desc\",\"missing\":\"_last\",\"unmapped_type\":\"long\"}},"
                + "{\"id\":{\"order\":\"desc\",\"missing\":\"_last\",\"unmapped_type\":\"keyword\"}}]}";
            assertThat(searchIds(alias, sorted)).hasSize(3);
        } finally {
            client.deleteIndex(SearchIndexNaming.physical(alias, 1));
        }
    }

    private static String query(String filtersJson, String mustJson) {
        return "{\"size\":20,\"_source\":false,\"track_total_hits\":true,\"query\":{\"bool\":{\"filter\":" + filtersJson
            + (mustJson == null ? "" : ",\"must\":[" + mustJson + "]") + "}}}";
    }

    private static long waitStable(LongSupplier value) throws InterruptedException {
        long previous = -1;
        long current = value.getAsLong();
        while (current != previous) {
            Thread.sleep(1_200); // > refresh_interval
            previous = current;
            current = value.getAsLong();
        }
        return current;
    }

    private long queueSize() {
        return jdbc.queryForObject("SELECT count(*) FROM search_index_queue", Long.class);
    }

    private List<String> searchIds(String alias, String query) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(esUri.replaceAll("/+$", "") + "/" + alias + "/_search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(query)).build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode hits = objectMapper.readTree(response.body()).path("hits").path("hits");
        List<String> ids = new java.util.ArrayList<>();
        hits.forEach(h -> ids.add(h.path("_id").asText()));
        return ids;
    }

    private static void waitFor(LongSupplier actual, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        long value = -1;
        while (System.currentTimeMillis() < deadline) {
            value = actual.getAsLong();
            if (value == expected) {
                return;
            }
            Thread.sleep(300);
        }
        assertThat(value).isEqualTo(expected);
    }
}
