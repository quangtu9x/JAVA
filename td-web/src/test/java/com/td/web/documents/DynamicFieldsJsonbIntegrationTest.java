package com.td.web.documents;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-101 (Dynamic Data Engine) — Definition of Done (1): chứng minh bằng PostgreSQL thật
 * (Testcontainers, không mock) rằng migration {@code V1.0.12} tạo đúng cột {@code documents.dynamic_fields}
 * kiểu JSONB, và một giá trị {@link String} (giống cách Hibernate bind {@code BusinessDocument.dynamicFieldsJson})
 * ghi/đọc được vào cột này nhờ {@code stringtype=unspecified} (cấu hình trong {@code TenantDataSourceConfig}),
 * không cần thư viện JSONB-mapping (hypersistence-utils) hay ép kiểu tường minh ở tầng ứng dụng.
 *
 * <p><b>Lưu ý môi trường</b>: giống {@code TenantSchemaIsolationIntegrationTest} (BL-303) — container khởi
 * động thủ công để tự "skip" (không fail build) nếu Docker cục bộ không hỗ trợ Linux container. Test này
 * chạy thật trong CI (`.github/workflows/ci.yml`, {@code ubuntu-latest}).
 */
class DynamicFieldsJsonbIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource dataSource;

    @BeforeAll
    static void startContainerAndMigrate() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdwebapi_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua DynamicFieldsJsonbIntegrationTest: khong co Docker Linux container kha dung "
                    + "trong moi truong nay (" + ex.getMessage() + "). Test nay chay that trong CI "
                    + "(.github/workflows/ci.yml, ubuntu-latest).");
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-dynamic-fields-test");
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        // Giống hệt cấu hình trong TenantDataSourceConfig — điều kiện để String bind vào cột jsonb.
        config.addDataSourceProperty("stringtype", "unspecified");
        dataSource = new HikariDataSource(config);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    @AfterAll
    static void stopContainer() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void dynamicFieldsColumn_shouldBeJsonbAndAcceptStringBoundValue() throws Exception {
        String dynamicFieldsJson = "{\"received_date\":\"2026-08-04\",\"so_tien_number\":150000.0,"
            + "\"process_obj\":{\"step\":1}}";
        UUID documentId = UUID.randomUUID();

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO documents (id, title, dynamic_fields) VALUES (?, ?, ?)")) {
                insert.setObject(1, documentId);
                insert.setString(2, "BL-101 JSONB test");
                // Bind nhu mot String thuan (dung cach Hibernate se lam cho String field) - khong ep kieu ::jsonb.
                insert.setString(3, dynamicFieldsJson);
                insert.executeUpdate();
            }

            try (PreparedStatement columnTypeQuery = connection.prepareStatement(
                    "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'documents' AND column_name = 'dynamic_fields'");
                 ResultSet columnTypeResult = columnTypeQuery.executeQuery()) {
                assertThat(columnTypeResult.next()).isTrue();
                assertThat(columnTypeResult.getString("data_type")).isEqualTo("jsonb");
            }

            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT dynamic_fields ->> 'received_date' AS received_date, "
                        + "(dynamic_fields ->> 'so_tien_number')::numeric AS so_tien_number, "
                        + "dynamic_fields -> 'process_obj' ->> 'step' AS process_step "
                        + "FROM documents WHERE id = ?")) {
                select.setObject(1, documentId);
                try (ResultSet result = select.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("received_date")).isEqualTo("2026-08-04");
                    assertThat(result.getBigDecimal("so_tien_number")).isEqualByComparingTo("150000.0");
                    assertThat(result.getString("process_step")).isEqualTo("1");
                }
            }
        }
    }
}
