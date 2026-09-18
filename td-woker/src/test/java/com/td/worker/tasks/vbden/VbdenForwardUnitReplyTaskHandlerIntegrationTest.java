package com.td.worker.tasks.vbden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.config.WorkerProperties;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.UnitResult;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test {@link VbdenForwardUnitReplyTaskHandler} — port {@code DocumentInProcessUnit.java
 * #copyToDocumentIn()} phần cập nhật văn bản GỐC (loại đơn vị khỏi {@code unit_process}, ghi vết
 * {@code forwarded_units}), xem javadoc lớp handler.
 */
class VbdenForwardUnitReplyTaskHandlerIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static HikariDataSource adminDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TenantDataSourceManager tenantDataSources;
    private WorkerProperties props;
    private VbdenForwardUnitReplyTaskHandler handler;

    @BeforeAll
    static void startContainer() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdworker_vbden_forward_reply_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua VbdenForwardUnitReplyTaskHandlerIntegrationTest: khong co Docker Linux "
                    + "container kha dung trong moi truong nay (" + ex.getMessage() + "). Test nay "
                    + "chay that trong CI (.github/workflows/ci.yml, ubuntu-latest).");
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-vbden-forward-reply-test");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        config.setAutoCommit(false);
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);

        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setPoolName("hikari-vbden-forward-reply-test-admin");
        adminConfig.setJdbcUrl(postgres.getJdbcUrl());
        adminConfig.setUsername(postgres.getUsername());
        adminConfig.setPassword(postgres.getPassword());
        adminConfig.setDriverClassName(postgres.getDriverClassName());
        adminConfig.addDataSourceProperty("stringtype", "unspecified");
        adminDataSource = new HikariDataSource(adminConfig);

        new JdbcTemplate(adminDataSource).execute("""
            CREATE TABLE documents (
                id UUID PRIMARY KEY,
                title VARCHAR(300) NOT NULL,
                document_type VARCHAR(100),
                status VARCHAR(50),
                dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
                created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
            )
            """);

        // Bo sung 08/09/2026 - subset THAT cua "vbden_res_documents" (V1.0.42), can de
        // insertArrivalHistoryRow test duoc dung schema (xem javadoc handler).
        new JdbcTemplate(adminDataSource).execute("""
            CREATE TABLE vbden_res_documents (
                id UUID PRIMARY KEY,
                title VARCHAR(300) NOT NULL,
                document_type VARCHAR(100),
                status VARCHAR(50),
                tags_json TEXT NOT NULL DEFAULT '[]',
                attributes_json TEXT NOT NULL DEFAULT '{}',
                metadata_json TEXT NOT NULL DEFAULT '{}',
                dynamic_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
                version_no BIGINT NOT NULL DEFAULT 1,
                created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
            )
            """);
    }

    @AfterAll
    static void stopContainer() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (adminDataSource != null) {
            adminDataSource.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(dataSource != null, "Container chưa khởi động được (xem @BeforeAll)");
        new JdbcTemplate(adminDataSource).execute("TRUNCATE TABLE documents");
        new JdbcTemplate(adminDataSource).execute("TRUNCATE TABLE vbden_res_documents");

        tenantDataSources = mock(TenantDataSourceManager.class);
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.of(dataSource));
        props = new WorkerProperties();
        props.getTasks().getVbden().setEnabled(true);
        handler = new VbdenForwardUnitReplyTaskHandler(props, tenantDataSources, objectMapper);
    }

    private UUID insertOriginDocument(String dynamicFieldsJson) {
        UUID id = UUID.randomUUID();
        new JdbcTemplate(adminDataSource).update("""
                INSERT INTO documents (id, title, document_type, status, dynamic_fields)
                VALUES (?, 'Van ban den goc', 'vbden', '2', ?::jsonb)
                """, id, dynamicFieldsJson);
        return id;
    }

    private ObjectNode basePayload(UUID originDocumentId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("originDocumentId", originDocumentId.toString());
        node.put("targetIdentifier", "H05.02");
        node.put("targetName", "So NN&MT");
        node.put("createdDocumentId", UUID.randomUUID().toString());
        node.put("createdTenantId", "h05_02");
        return node;
    }

    private TaskContext context() {
        return new TaskContext("vbden.forward_unit.reply", "msg-1", "u1", "corr-1", "h05_01",
            null, Map.of(), 5, null, Map.of());
    }

    @Test
    void removesTargetIdentifierFromUnitProcessArrayAndRecordsForwardedUnit() throws Exception {
        UUID originId = insertOriginDocument("""
            {"unit_process": ["H05.02", "H05.03"]}
            """);
        JsonNode payload = basePayload(originId);

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, Object> dynamicFields = objectMapper.readValue(
            jdbc.queryForObject("SELECT dynamic_fields::text FROM documents WHERE id = ?", String.class, originId),
            Map.class);
        List<String> unitProcess = (List<String>) dynamicFields.get("unit_process");
        assertThat(unitProcess).containsExactly("H05.03"); // H05.02 da bi loai, H05.03 con nguyen.

        List<Map<String, Object>> forwardedUnits = (List<Map<String, Object>>) dynamicFields.get("forwarded_units");
        assertThat(forwardedUnits).hasSize(1);
        assertThat(forwardedUnits.get(0)).containsEntry("identifier", "H05.02")
            .containsEntry("tenantId", "h05_02");
    }

    /**
     * Yêu cầu người dùng 08/09/2026 ("muốn phản hồi trạng thái 'Đã đến'... vào quá trình xử lý"):
     * cùng lúc với việc dọn {@code unit_process}, phải ghi THÊM 1 dòng {@code vbden_res_documents}
     * ({@code parentid = originDocumentId}) để tab "Quá trình xử lý" của văn bản gốc hiện "Đã đến
     * đơn vị X" — xem javadoc handler.
     */
    @Test
    void recordsArrivalHistoryRowIntoVbdenResDocuments() throws Exception {
        UUID originId = insertOriginDocument("""
            {"unit_process": ["H05.02"]}
            """);
        JsonNode payload = basePayload(originId);

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        List<Map<String, Object>> rows = new JdbcTemplate(dataSource)
            .queryForList("SELECT * FROM vbden_res_documents");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("title")).isEqualTo("Đã đến");
        assertThat(row.get("document_type")).isEqualTo("vbden_res");

        Map<String, Object> fields = objectMapper.readValue(row.get("dynamic_fields").toString(), Map.class);
        assertThat(fields.get("parentid")).isEqualTo(originId.toString());
        assertThat(fields.get("action")).isEqualTo("da_den_don_vi_khac");
        assertThat(fields.get("opinion")).isEqualTo("Đã đến đơn vị So NN&MT");
        assertThat(fields.get("nguoi_thuc_hien")).isEqualTo("So NN&MT");
        assertThat(fields.get("identifier")).isEqualTo("H05.02");
    }

    /** {@code targetName} rỗng/null — fallback về {@code targetIdentifier}, không lỗi/để trống. */
    @Test
    void arrivalHistoryRow_fallsBackToIdentifierWhenTargetNameBlank() throws Exception {
        UUID originId = insertOriginDocument("""
            {"unit_process": ["H05.02"]}
            """);
        ObjectNode payload = basePayload(originId);
        payload.remove("targetName");

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        Map<String, Object> row = new JdbcTemplate(dataSource)
            .queryForList("SELECT * FROM vbden_res_documents").get(0);
        Map<String, Object> fields = objectMapper.readValue(row.get("dynamic_fields").toString(), Map.class);
        assertThat(fields.get("opinion")).isEqualTo("Đã đến đơn vị H05.02");
    }

    /**
     * {@code unit_process} lưu dạng SCALAR ";"-nối (dữ liệu cũ/ghi qua đường khác) — phải đọc khoan
     * dung và ghi lại đúng dạng array, cùng nguyên tắc {@code readMultiValue} phía td-application.
     */
    @Test
    void toleratesScalarSemicolonFormatForUnitProcess() throws Exception {
        UUID originId = insertOriginDocument("""
            {"unit_process": "H05.02;H05.03"}
            """);
        JsonNode payload = basePayload(originId);

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        Map<String, Object> dynamicFields = objectMapper.readValue(
            new JdbcTemplate(dataSource).queryForObject(
                "SELECT dynamic_fields::text FROM documents WHERE id = ?", String.class, originId),
            Map.class);
        List<String> unitProcess = (List<String>) dynamicFields.get("unit_process");
        assertThat(unitProcess).containsExactly("H05.03");
    }

    @Test
    void appendsMultipleForwardedUnitsAcrossSeparateCalls() throws Exception {
        UUID originId = insertOriginDocument("""
            {"unit_process": ["H05.02", "H05.03"]}
            """);

        assertThat(handler.execute(context(), basePayload(originId)).isSuccess()).isTrue();
        ObjectNode second = basePayload(originId);
        second.put("targetIdentifier", "H05.03");
        second.put("createdTenantId", "h05_03");
        assertThat(handler.execute(context(), second).isSuccess()).isTrue();

        Map<String, Object> dynamicFields = objectMapper.readValue(
            new JdbcTemplate(dataSource).queryForObject(
                "SELECT dynamic_fields::text FROM documents WHERE id = ?", String.class, originId),
            Map.class);
        assertThat((List<String>) dynamicFields.get("unit_process")).isEmpty();
        assertThat((List<Map<String, Object>>) dynamicFields.get("forwarded_units")).hasSize(2);
    }

    @Test
    void originDocumentNotFound_noOpWithoutFailingUnit() {
        JsonNode payload = basePayload(UUID.randomUUID()); // khong ton tai

        UnitResult result = handler.execute(context(), payload);
        assertThat(result.isSuccess()).isTrue(); // best-effort, khong fail toan bo unit
    }

    @Test
    void disabledTaskReturnsFailure() {
        props.getTasks().getVbden().setEnabled(false);
        JsonNode payload = basePayload(UUID.randomUUID());
        UnitResult result = handler.execute(context(), payload);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void missingTenantDataSourceFails() {
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.empty());
        JsonNode payload = basePayload(UUID.randomUUID());
        UnitResult result = handler.execute(context(), payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("h05_01");
    }
}
