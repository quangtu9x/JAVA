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
 * Test {@link VbdenForwardUnitStatusTaskHandler} — xem javadoc lớp handler: ghi echo trạng thái xử
 * lý (mọi hành động tiếp theo trên bản sao, không riêng "chuyển xử lý") ngược về
 * {@code vbden_res_documents} của văn bản GỐC, đáp ứng yêu cầu người dùng 08/09/2026 "muốn phản hồi
 * trạng thái 'Đã đến' và các trạng thái như chuyển xử lý... vào quá trình xử lý".
 */
class VbdenForwardUnitStatusTaskHandlerIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;
    private static HikariDataSource adminDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TenantDataSourceManager tenantDataSources;
    private WorkerProperties props;
    private VbdenForwardUnitStatusTaskHandler handler;

    @BeforeAll
    static void startContainer() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdworker_vbden_forward_status_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua VbdenForwardUnitStatusTaskHandlerIntegrationTest: khong co Docker Linux "
                    + "container kha dung trong moi truong nay (" + ex.getMessage() + "). Test nay "
                    + "chay that trong CI (.github/workflows/ci.yml, ubuntu-latest).");
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-vbden-forward-status-test");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        config.setAutoCommit(false);
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);

        HikariConfig adminConfig = new HikariConfig();
        adminConfig.setPoolName("hikari-vbden-forward-status-test-admin");
        adminConfig.setJdbcUrl(postgres.getJdbcUrl());
        adminConfig.setUsername(postgres.getUsername());
        adminConfig.setPassword(postgres.getPassword());
        adminConfig.setDriverClassName(postgres.getDriverClassName());
        adminConfig.addDataSourceProperty("stringtype", "unspecified");
        adminDataSource = new HikariDataSource(adminConfig);

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
        new JdbcTemplate(adminDataSource).execute("TRUNCATE TABLE vbden_res_documents");

        tenantDataSources = mock(TenantDataSourceManager.class);
        when(tenantDataSources.getDataSource(any())).thenReturn(Optional.of(dataSource));
        props = new WorkerProperties();
        props.getTasks().getVbden().setEnabled(true);
        handler = new VbdenForwardUnitStatusTaskHandler(props, tenantDataSources, objectMapper);
    }

    private ObjectNode basePayload(UUID originDocumentId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("originDocumentId", originDocumentId.toString());
        node.put("action", "chuyenxuly");
        node.put("actionLabel", "Chuyển xử lý");
        node.put("reportingIdentifier", "H05.02");
        node.put("reportingName", "So NN&MT");
        node.put("actingUsername", "vanthu02");
        node.put("opinion", "Chuyen tiep cho chuyen vien phu trach");
        node.put("newAssigneesLabel", "chuyenvien01");
        return node;
    }

    private TaskContext context() {
        return new TaskContext("vbden.forward_unit.status", "msg-1", "u1", "corr-1", "h05_01",
            null, Map.of(), 5, null, Map.of());
    }

    @Test
    void successfulStatus_insertsHistoryRowWithFormattedOpinion() throws Exception {
        UUID originId = UUID.randomUUID();
        JsonNode payload = basePayload(originId);

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        List<Map<String, Object>> rows = new JdbcTemplate(dataSource)
            .queryForList("SELECT * FROM vbden_res_documents");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("title")).isEqualTo("Chuyển xử lý");
        assertThat(row.get("document_type")).isEqualTo("vbden_res");

        Map<String, Object> fields = objectMapper.readValue(row.get("dynamic_fields").toString(), Map.class);
        assertThat(fields.get("parentid")).isEqualTo(originId.toString());
        assertThat(fields.get("action")).isEqualTo("chuyenxuly");
        assertThat(fields.get("opinion")).isEqualTo(
            "Chuyển xử lý tại đơn vị So NN&MT (giao cho: chuyenvien01): Chuyen tiep cho chuyen vien phu trach");
        assertThat(fields.get("nguoi_thuc_hien")).isEqualTo("vanthu02");
        assertThat(fields.get("identifier")).isEqualTo("H05.02");
    }

    /** Thiếu actionLabel/opinion/newAssigneesLabel — vẫn ghi được, không lỗi, chỉ ngắn gọn hơn. */
    @Test
    void minimalPayload_stillInsertsHistoryRow() throws Exception {
        UUID originId = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("originDocumentId", originId.toString());
        payload.put("action", "phanphoi");

        assertThat(handler.execute(context(), payload).isSuccess()).isTrue();

        Map<String, Object> row = new JdbcTemplate(dataSource)
            .queryForList("SELECT * FROM vbden_res_documents").get(0);
        assertThat(row.get("title")).isEqualTo("phanphoi");
        Map<String, Object> fields = objectMapper.readValue(row.get("dynamic_fields").toString(), Map.class);
        assertThat(fields.get("opinion")).isEqualTo("phanphoi");
    }

    @Test
    void missingOriginDocumentIdReturnsFailure() {
        ObjectNode payload = basePayload(UUID.randomUUID());
        payload.remove("originDocumentId");
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
        assertThat(result.error()).contains("h05_01");
    }
}
