package com.td.infrastructure.tenant;

import com.td.application.tenant.ProvisionTenantCommand;
import com.td.application.tenant.TenantProvisioningService;
import com.td.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Implement {@link TenantProvisioningService} (BL-117) — CREATE DATABASE thật, chạy Flyway, mã hoá
 * mật khẩu, hot-add connection pool vào {@link TenantRoutingDataSource}.
 *
 * <p><b>Vì sao mở connection JDBC thô (không qua Hikari/EntityManager)</b>: Postgres KHÔNG cho phép
 * chạy {@code CREATE DATABASE} bên trong 1 transaction block — connection ở đây luôn
 * {@code autoCommit=true}, dùng ĐÚNG 1 lần rồi đóng ngay, không phải pool lâu dài.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostgresTenantProvisioningService implements TenantProvisioningService {

    private final TenantRoutingDataSource tenantRoutingDataSource;
    private final TenantCredentialCipher tenantCredentialCipher;
    private final TenantActiveRegistry tenantActiveRegistry;
    private final TenantPoolFactory tenantPoolFactory;
    private final TenantFlywayMigrationService tenantFlywayMigrationService;

    @Override
    public String provision(ProvisionTenantCommand command) {
        switch (command.getMode()) {
            case CREATE_NEW -> {
                createDatabase(command);
                runFlywayMigration(command);
            }
            case REGISTER_EXISTING -> runFlywayMigration(command);
            case ROTATE_CREDENTIAL_ONLY -> testConnection(command);
        }

        DataSource pool = tenantPoolFactory.build(
            command.getTenantId(), command.getHost(), command.getPort(), command.getDatabaseName(),
            command.getUsername(), command.getPlainPassword(), command.getDriverClassName());
        tenantRoutingDataSource.registerOrReplace(command.getTenantId(), pool);
        tenantActiveRegistry.markActive(command.getTenantId());

        return tenantCredentialCipher.encrypt(command.getPlainPassword());
    }

    @Override
    public void testConnection(ProvisionTenantCommand command) {
        String url = TenantPoolFactory.buildJdbcUrl(command.getHost(), command.getPort(), command.getDatabaseName());
        try (Connection ignored = DriverManager.getConnection(url, command.getUsername(), command.getPlainPassword())) {
            log.info("Test connection toi tenant '{}' thanh cong", command.getTenantId());
        } catch (SQLException ex) {
            throw new IllegalStateException("Khong the ket noi toi database tenant: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void ensurePoolRegistered(Tenant tenant) {
        if (tenantRoutingDataSource.hasTenant(tenant.getTenantId())) {
            return;
        }
        String plainPassword = tenantCredentialCipher.decrypt(tenant.getEncryptedPassword());
        DataSource pool = tenantPoolFactory.build(
            tenant.getTenantId(), tenant.getHost(), tenant.getPort(), tenant.getDatabaseName(),
            tenant.getUsername(), plainPassword, tenant.getDriverClassName());
        tenantRoutingDataSource.registerOrReplace(tenant.getTenantId(), pool);
    }

    @Override
    public void markActive(String tenantId) {
        tenantActiveRegistry.markActive(tenantId);
    }

    @Override
    public void markInactive(String tenantId) {
        tenantActiveRegistry.markInactive(tenantId);
    }

    private void createDatabase(ProvisionTenantCommand command) {
        // Ket noi vao database "postgres" mac dinh (maintenance DB) tren CUNG server de chay DDL
        // CREATE DATABASE - databaseName da duoc validate bang whitelist nghiem ngat
        // (^[a-zA-Z][a-zA-Z0-9_]{0,62}$, xem CreateTenantUseCase) TRUOC khi toi day - an toan de noi
        // truc tiep vao cau lenh DDL (JDBC khong ho tro bind param cho identifier).
        String adminUrl = TenantPoolFactory.buildJdbcUrl(command.getHost(), command.getPort(), "postgres");
        try (Connection connection = DriverManager.getConnection(adminUrl, command.getUsername(), command.getPlainPassword())) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE \"" + command.getDatabaseName() + "\"");
            }
            log.info("Da tao database '{}' cho tenant '{}'", command.getDatabaseName(), command.getTenantId());
        } catch (SQLException ex) {
            throw new IllegalStateException("Tao database cho tenant that bai: " + ex.getMessage(), ex);
        }
    }

    private void runFlywayMigration(ProvisionTenantCommand command) {
        tenantFlywayMigrationService.migrateTenant(
            command.getTenantId(), command.getHost(), command.getPort(), command.getDatabaseName(),
            command.getUsername(), command.getPlainPassword());
    }
}
