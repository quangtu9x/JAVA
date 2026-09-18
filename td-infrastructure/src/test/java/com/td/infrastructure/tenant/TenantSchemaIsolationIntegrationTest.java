package com.td.infrastructure.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chứng minh cơ chế routing lõi của {@link TenantRoutingDataSource} bằng PostgreSQL thật
 * (Testcontainers, không mock): dữ liệu ghi vào tenant này không xuất hiện khi truy vấn với
 * {@link TenantContext} của tenant khác. Ban đầu viết cho BL-303 (2 schema khác nhau CÙNG 1
 * database) — kể từ BL-117 (database-per-tenant thật, mỗi tenant 1 database vật lý riêng), test
 * này vẫn còn giá trị hồi quy nguyên vẹn: cơ chế đang kiểm tra (route đúng {@code DataSource} theo
 * {@link TenantContext}, cô lập hoàn toàn giữa 2 pool) HỆT NHAU bất kể 2 pool đó trỏ tới 2 schema
 * của 1 database hay 2 database vật lý khác nhau — không cần đổi logic test, chỉ đổi ý nghĩa mô tả.
 *
 * <p><b>Lưu ý môi trường</b>: cần Docker chạy Linux container. Container được khởi động thủ công
 * (không dùng {@code @Testcontainers}/{@code @Container}) để có thể tự "skip" (assumption, không
 * fail) toàn bộ class nếu môi trường không hỗ trợ — vd. máy dev cục bộ dùng Docker Desktop ở chế
 * độ Windows container (image {@code postgres} là Linux-only) sẽ tự bỏ qua thay vì làm fail build.
 * CI (`.github/workflows/ci.yml`, chạy trên {@code ubuntu-latest}) có Linux container nên vẫn xác
 * nhận đầy đủ trên mỗi lần push/PR.
 */
class TenantSchemaIsolationIntegrationTest {

    private static PostgreSQLContainer<?> postgres;

    private HikariDataSource h05DataSource;
    private HikariDataSource k05DataSource;
    private TenantRoutingDataSource routingDataSource;

    @BeforeAll
    static void startContainer() {
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tdwebapi_test")
                .withUsername("test")
                .withPassword("test");
            postgres.start();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false,
                "Bo qua TenantSchemaIsolationIntegrationTest: khong co Docker Linux container kha "
                    + "dung trong moi truong nay (" + ex.getMessage() + "). Test nay chay that trong "
                    + "CI (.github/workflows/ci.yml, ubuntu-latest).");
        }
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS qlvb_h05");
            statement.execute("CREATE SCHEMA IF NOT EXISTS qlvb_k05");
        }

        h05DataSource = buildTenantDataSource("qlvb_h05");
        k05DataSource = buildTenantDataSource("qlvb_k05");

        createProbeTable(h05DataSource);
        createProbeTable(k05DataSource);

        routingDataSource = new TenantRoutingDataSource();
        routingDataSource.setTargetDataSources(Map.of("H05", h05DataSource, "K05", k05DataSource));
        routingDataSource.setDefaultTargetDataSource(h05DataSource);
        routingDataSource.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (h05DataSource != null) {
            h05DataSource.close();
        }
        if (k05DataSource != null) {
            k05DataSource.close();
        }
    }

    @Test
    void dataWrittenForOneTenant_shouldNotBeVisibleToAnotherTenant() throws Exception {
        TenantContext.setCurrentTenant("H05");
        try (Connection connection = routingDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tenant_probe (id, name) VALUES (1, 'Van ban Thanh Hoa')");
        }

        TenantContext.setCurrentTenant("K05");
        try (Connection connection = routingDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tenant_probe (id, name) VALUES (1, 'Van ban tinh khac')");
        }

        TenantContext.setCurrentTenant("H05");
        assertThat(queryAllNames(routingDataSource)).containsExactly("Van ban Thanh Hoa");

        TenantContext.setCurrentTenant("K05");
        assertThat(queryAllNames(routingDataSource)).containsExactly("Van ban tinh khac");
    }

    @Test
    void noTenantContextSet_shouldRouteToDefaultTenant() throws Exception {
        TenantContext.setCurrentTenant("H05");
        try (Connection connection = routingDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO tenant_probe (id, name) VALUES (1, 'Du lieu tenant mac dinh')");
        }

        // Mô phỏng truy vấn ngoài vòng đời request (vd. lúc Flyway chạy ở startup).
        TenantContext.clear();
        assertThat(queryAllNames(routingDataSource)).containsExactly("Du lieu tenant mac dinh");
    }

    private List<String> queryAllNames(DataSource dataSource) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT name FROM tenant_probe ORDER BY id")) {
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
        }
        return names;
    }

    private void createProbeTable(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS tenant_probe (id INT PRIMARY KEY, name VARCHAR(200) NOT NULL)");
        }
    }

    private HikariDataSource buildTenantDataSource(String schema) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("hikari-tenant-test-" + schema);
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        config.setSchema(schema);
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }
}
