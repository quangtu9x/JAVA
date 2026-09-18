package com.td.infrastructure.tenant;

import com.td.application.dbserver.DbServerConnectionTester;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// BL-117 (bổ sung 19/08/2026): implement DbServerConnectionTester — mở thử 1 connection JDBC thô tới
// database "postgres" mặc định (maintenance DB), cùng cơ chế PostgresTenantProvisioningService.testConnection
// nhưng KHÔNG cần biết databaseName cụ thể (DbServer đại diện 1 SERVER, không phải 1 database).
@Service
@Slf4j
public class PostgresDbServerConnectionTester implements DbServerConnectionTester {

    @Override
    public void testConnection(String host, int port, String username, String password) {
        String url = TenantPoolFactory.buildJdbcUrl(host, port, "postgres");
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            log.info("Test connection toi may chu CSDL '{}:{}' thanh cong", host, port);
        } catch (SQLException ex) {
            throw new IllegalStateException("Khong the ket noi toi may chu CSDL: " + ex.getMessage(), ex);
        }
    }
}
