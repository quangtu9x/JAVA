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
 * Test {@link VbdenForwardUnitRevokeTaskHandler} — giai đoạn 2 {@code vbden.forward_unit} (xem
 * javadoc lớp handler): đánh dấu bản sao "đã lấy lại/thu hồi" + xoá rỗng field điều hướng hàng đợi +
 * ghi audit vào {@code vbden_res_documents}.
 */
class VbdenForwardUnitRevokeTaskHandlerIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static HikariDataSource adminDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TenantDataSourceManager tenantDataSources;
    private WorkerProperties props;
    private VbdenForwardUnitRevokeTaskHandler handler;

    @BeforeAll
    static void startContainer() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdworker_vbden_forward_revoke_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua VbdenForwardUnitRevokeTaskHandlerIntegrationTest: khong co Docker Linux "
                    + "container kha dung trong moi truong nay (" + ex.getMessage() + "). Test nay "
                    + "chay that trong CI (.github/workflows/ci.yml, ubuntu-latest).");
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-vbden-forward-revoke-test");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        config.setAutoCommit(false);
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);

        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setPoolName("hikari-vbden-forward-revoke-test-admin");
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
        handler = new VbdenForwardUnitRevokeTaskHandler(props, tenantDataSources, objectMapper);
    }

    private UUID insertTargetDocument(String dynamicFieldsJson) {
        UUID id = UUID.randomUUID();
        new JdbcTemplate(adminDataSource).update("""
                INSERT INTO documents (id, title, document_type, status, dynamic_fields)
                VALUES (?, 'Ban sao van ban den', 'vbden', '2', ?::jsonb)
                """, id, dynamicFieldsJson);
        return id;
    }

    private ObjectNode basePayload(UUID targetDocumentId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("targetDocumentId", targetDocumentId.toString());
        node.put("originDocumentId", UUID.randomUUID().toString());
        node.put("actingUsername", "vanthu01");
        node.put("opinion", "Gui nham don vi");
        node.put("reason", "laylaivanban");
        return node;
    }

    private TaskContext context() {
        return new TaskContext("vbden.forward_unit.revoke", "msg-1", "u1", "corr-1", "h05_02",
            null, Map.of(), 5, null, Map.of());
    }

    @Test
    void successfulRevoke_setsReplyAndIsRevokeAndClearsQueueFields() throws Exception {
        UUID targetId = insertTargetDocument("""
            {"user_process": ["chuyenvien01"], "unit_process": ["H05.02.01"],
             "identifier_creator": "H05.02", "contact_unit_identifier": "H05.02"}
            """);
        JsonNode payload = basePayload(targetId);

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        Map<String, Object> dynamicFields = objectMapper.readValue(
            new JdbcTemplate(dataSource).queryForObject(
                "SELECT dynamic_fields::text FROM documents WHERE id = ?", String.class, targetId),
            Map.class);
        assertThat(dynamicFields.get("reply")).isEqualTo("1");
        assertThat(dynamicFields.get("is_revoke")).isEqualTo("1");
        assertThat(dynamicFields.get("delete_date")).isNotNull();
        assertThat((List<?>) dynamicFields.get("user_process")).isEmpty();
        assertThat((List<?>) dynamicFields.get("unit_process")).isEmpty();
        // Field khong lien quan (identifier_creator/contact_unit_identifier) PHAI giu nguyen - van
        // con dung de tra cuu/audit, chi bi loai khoi view nho reply=1.
        assertThat(dynamicFields.get("identifier_creator")).isEqualTo("H05.02");
        assertThat(dynamicFields.get("contact_unit_identifier")).isEqualTo("H05.02");
    }

    @Test
    void successfulRevoke_insertsHistoryRowIntoVbdenResDocuments() throws Exception {
        UUID targetId = insertTargetDocument("{}");
        JsonNode payload = basePayload(targetId);

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        List<Map<String, Object>> rows = new JdbcTemplate(dataSource)
            .queryForList("SELECT * FROM vbden_res_documents");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("title")).isEqualTo("Lấy lại VB");

        Map<String, Object> fields = objectMapper.readValue(row.get("dynamic_fields").toString(), Map.class);
        assertThat(fields.get("parentid")).isEqualTo(targetId.toString());
        assertThat(fields.get("action")).isEqualTo("laylaivanban");
        assertThat(fields.get("opinion")).isEqualTo("Văn bản đã bị \"Lấy lại VB\" bởi đơn vị gốc: Gui nham don vi");
        assertThat(fields.get("nguoi_thuc_hien")).isEqualTo("vanthu01");
    }

    /** 23/10/2026: cùng unit chạy lại — bỏ qua, không thêm dòng audit trùng, không ghi đè delete_date. */
    @Test
    void rerunOfSameUnit_isSkipped() throws Exception {
        UUID targetId = insertTargetDocument("{}");
        JsonNode payload = basePayload(targetId);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();
        String firstFields = jdbc.queryForObject(
            "SELECT dynamic_fields::text FROM documents WHERE id = ?", String.class, targetId);
        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        assertThat(jdbc.queryForList("SELECT id FROM vbden_res_documents")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT dynamic_fields::text FROM documents WHERE id = ?", String.class, targetId))
            .isEqualTo(firstFields);
    }

    /** {@code reason = "thuhoi"} — nhãn audit khác, xem javadoc handler. */
    @Test
    void thuHoiReason_usesThuHoiLabel() throws Exception {
        UUID targetId = insertTargetDocument("{}");
        ObjectNode payload = basePayload(targetId);
        payload.put("reason", "thuhoi");

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        Map<String, Object> row = new JdbcTemplate(dataSource)
            .queryForList("SELECT * FROM vbden_res_documents").get(0);
        assertThat(row.get("title")).isEqualTo("Thu hồi");
    }

    @Test
    void targetDocumentNotFound_noOpWithoutFailingUnit() {
        JsonNode payload = basePayload(UUID.randomUUID()); // khong ton tai

        UnitResult result = handler.execute(context(), payload);
        assertThat(result.isSuccess()).isTrue(); // best-effort, khong fail toan bo unit
    }

    @Test
    void missingRequiredFieldReturnsFailure() {
        ObjectNode payload = basePayload(UUID.randomUUID());
        payload.remove("targetDocumentId");
        UnitResult result = handler.execute(context(), payload);

        assertThat(result.isSuccess()).isFalse();
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
        assertThat(result.error()).contains("h05_02");
    }
}
