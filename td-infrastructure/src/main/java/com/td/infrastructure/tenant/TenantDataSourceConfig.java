package com.td.infrastructure.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thay thế {@code DataSource} tự động cấu hình của Spring Boot bằng {@link TenantRoutingDataSource}
 * (BL-303, viết lại theo BL-117): đọc danh sách tenant ACTIVE từ bảng {@code tenants} trong database
 * "control-plane" ({@code controlPlaneDataSource}) — KHÔNG còn đọc từ YAML tĩnh
 * {@code app.multitenancy.tenants} như BL-303 cũ. Mỗi tenant giờ có 1 database Postgres VẬT LÝ
 * RIÊNG BIỆT (host/port/database/username/password RIÊNG, có thể khác server hoàn toàn) — khác hẳn
 * BL-303 cũ (cùng 1 database, chỉ khác schema).
 *
 * <p>Dùng {@code JdbcTemplate} thô (không qua JPA/EntityManager) để đọc bảng {@code tenants} — tại
 * thời điểm bean này được tạo, {@code controlPlaneEntityManagerFactory} CHƯA CHẮC đã sẵn sàng (thứ
 * tự khởi tạo bean phức tạp hơn khi có nhiều EntityManagerFactory) nên tránh phụ thuộc vào nó, đúng
 * lý do {@code TenantFlywayMigrationService}/{@code ErrorTrackingSchemaMigrator} cũng dùng JDBC/
 * Flyway thô thay vì JPA cho các thao tác bootstrap.
 *
 * <p><b>Bootstrap lần đầu (bảng {@code tenants} rỗng) — CỐ TÌNH KHÔNG chặn khởi động</b>: nếu chặn
 * cứng (ném exception) khi chưa có tenant nào, sẽ tạo ra vòng lặp con gà-quả trứng thật sự — API
 * DUY NHẤT để thêm tenant (`POST /api/v1/platform/tenants`) lại cần chính app này ĐANG CHẠY mới gọi
 * được. Vì vậy chỉ log cảnh báo và cho app khởi động BÌNH THƯỜNG với {@link TenantRoutingDataSource}
 * rỗng (hoặc thiếu default) — {@code PlatformTenantsController} vẫn dùng được ngay (chạy hoàn toàn
 * trên {@code controlPlaneEntityManagerFactory}, KHÔNG đụng tới bean {@code DataSource} này) để đăng
 * ký tenant đầu tiên; mọi endpoint nghiệp vụ khác sẽ lỗi rõ ràng lúc gọi (không crash lúc khởi động)
 * cho tới khi có tenant.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class TenantDataSourceConfig {

    private final TenantProperties tenantProperties;
    private final TenantFlywayMigrationService tenantFlywayMigrationService;
    private final TenantCredentialCipher tenantCredentialCipher;
    private final TenantActiveRegistry tenantActiveRegistry;
    private final TenantPoolFactory tenantPoolFactory;

    /**
     * BL-118 (tiếp, 20/08/2026): tách bước "dựng map pool/tenant" thành 1 bean RIÊNG, tái dùng bởi
     * CẢ {@link #dataSource} (routing, nghiệp vụ/văn bản) LẪN {@link #identityDataSource} (cố định
     * tenant mặc định, danh mục dùng chung — AppUser/Organization/RoleGroup...) — tránh dựng 2 lần
     * (2 Hikari pool riêng biệt cùng trỏ 1 database) cho tenant mặc định.
     *
     * <p><b>Bugfix (khởi động lỗi "circular reference")</b>: bean này BẮT BUỘC phải trả về 1 KIỂU
     * RIÊNG ({@link TenantDataSourcePool}), KHÔNG được khai báo trực tiếp kiểu {@code Map<String,
     * DataSource>} — Spring coi MỌI injection point kiểu {@code Map<String, T>} là yêu cầu ĐẶC BIỆT
     * "tự động gom TẤT CẢ bean kiểu T trong context, key theo tên bean" (khác hẳn "tiêm đúng 1 bean cụ
     * thể có kiểu khớp"). Nếu khai báo thẳng {@code Map<String, DataSource>}, Spring sẽ cố gom TẤT CẢ
     * bean {@code DataSource} — bao gồm CHÍNH {@code dataSource}/{@code identityDataSource} đang được
     * tạo — tạo vòng lặp thật: {@code dataSource} cần map → map cần gom {@code identityDataSource} →
     * {@code identityDataSource} lại cần CHÍNH map đó → cần {@code dataSource} (đang dở dang) → lỗi
     * {@code UnsatisfiedDependencyException: Requested bean is currently in creation}.
     */
    @Bean
    public TenantDataSourcePool tenantDataSourcePool(
            @Qualifier("controlPlaneDataSource") DataSource controlPlaneDataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(controlPlaneDataSource);
        List<TenantRow> rows = jdbcTemplate.query(
            "SELECT tenant_id, host, port, database_name, username, encrypted_password, " +
                "driver_class_name, active FROM tenants WHERE deleted_on IS NULL",
            (rs, rowNum) -> new TenantRow(
                rs.getString("tenant_id"),
                rs.getString("host"),
                rs.getInt("port"),
                rs.getString("database_name"),
                rs.getString("username"),
                rs.getString("encrypted_password"),
                rs.getString("driver_class_name"),
                rs.getBoolean("active")));

        if (rows.isEmpty()) {
            log.warn(
                "Bang 'tenants' (database control-plane) dang rong - app van khoi dong nhung CHUA co " +
                "tenant nghiep vu nao dung duoc. Dang ky tenant dau tien (vd '{}') qua API " +
                "POST /api/v1/platform/tenants (mode=REGISTER_EXISTING cho tenant da co san du lieu, " +
                "hoac CREATE_NEW cho tenant hoan toan moi) - endpoint nay dung duoc ngay ca khi chua " +
                "co tenant nao (chay tren database control-plane, khong phu thuoc DataSource nay)",
                tenantProperties.getDefaultTenantId());
        }

        Map<String, DataSource> targetDataSources = new HashMap<>();
        for (TenantRow row : rows) {
            // Dang ky CA tenant inactive vao registry (isKnown=true, isActive=false) - can thiet de
            // TenantResolver phan biet dung "khong biet tenant nay" (fallback ve mac dinh) voi
            // "biet nhung dang bi khoa" (tu choi han, xem TenantResolver/TenantActiveRegistry).
            tenantActiveRegistry.register(row.tenantId(), row.active());

            if (!row.active()) {
                log.info("Bo qua khoi tao connection pool cho tenant '{}' (dang inactive)", row.tenantId());
                continue;
            }

            String plainPassword = tenantCredentialCipher.decrypt(row.encryptedPassword());
            // Bat kip migration moi (neu co) cho tenant da ton tai tu truoc - Flyway tu idempotent.
            tenantFlywayMigrationService.migrateTenant(
                row.tenantId(), row.host(), row.port(), row.databaseName(), row.username(), plainPassword);

            log.info("Khoi tao connection pool cho tenant {} (database={})", row.tenantId(), row.databaseName());
            DataSource pool = tenantPoolFactory.build(
                row.tenantId(), row.host(), row.port(), row.databaseName(), row.username(),
                plainPassword, row.driverClassName());
            targetDataSources.put(row.tenantId(), pool);
        }
        return new TenantDataSourcePool(targetDataSources);
    }

    /**
     * Bugfix 04/09/2026 (người dùng báo Tomcat WARN "hikari-tenant-H05_02 housekeeper... failed to
     * stop" — thread leak lúc reload/shutdown): {@code destroyMethod = "closeAll"} — Spring gọi
     * {@link TenantRoutingDataSource#closeAll()} khi context đóng, đóng HẾT connection pool của MỌI
     * tenant (không chỉ tenant mặc định — xem javadoc {@code closeAll()} để biết vì sao trước đây
     * chỉ tenant mặc định "vô tình" được đóng đúng).
     */
    @Bean(destroyMethod = "closeAll")
    @Primary
    public DataSource dataSource(TenantDataSourcePool tenantDataSourcePool) {
        Map<String, DataSource> tenantTargetDataSources = tenantDataSourcePool.byTenantId();
        String defaultTenantId = tenantProperties.getDefaultTenantId();
        DataSource defaultDataSource = tenantTargetDataSources.get(defaultTenantId);
        if (defaultDataSource == null) {
            // Khong throw (xem javadoc lop) - chi canh bao. TenantRoutingDataSource cho phep
            // defaultTargetDataSource = null; request khong xac dinh duoc tenant se loi RO RANG luc
            // GOI (AbstractRoutingDataSource nem IllegalStateException "Cannot determine target
            // DataSource"), khong lam app crash luc khoi dong.
            log.warn(
                "app.multitenancy.default-tenant-id='{}' khong active hoac khong ton tai trong bang " +
                "'tenants' - CHUA co default DataSource (request khong xac dinh duoc tenant se loi khi goi, " +
                "khong phai luc khoi dong)", defaultTenantId);
        }

        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        routingDataSource.initialize(tenantTargetDataSources, defaultDataSource);
        return routingDataSource;
    }

    /**
     * BL-118 (tiếp, 20/08/2026, bugfix NGHIÊM TRỌNG — xác nhận qua đối chiếu database thật, tài
     * khoản "skhcn"): AppUser/Organization/RoleGroup/AppRole/AppPermission... KHÔNG được nhân bản
     * theo từng tenant — đây là "danh mục dùng chung" (1 catalog identity duy nhất cho TOÀN hệ
     * thống), vật lý sống CỐ ĐỊNH ở database của tenant MẶC ĐỊNH — bất kể request đang thao tác
     * nghiệp vụ/văn bản của tenant nào. Chỉ dữ liệu nghiệp vụ/văn bản (Document, DocumentRegister...)
     * mới thật sự tách theo tenant (qua {@code Organization.linkedTenantId} của "Cơ quan").
     *
     * <p>Trước bugfix này, {@code AppUser}/{@code Organization} dùng CHUNG
     * {@code entityManagerFactory} (routing theo {@link TenantContext} của cả request) — khiến MỌI
     * lần đổi {@code TenantContext} sang tenant "hiệu lực" (linkedTenantId) để ghi văn bản mới CŨNG
     * làm mất luôn profile/roles/menu_* (AppUser không hề tồn tại ở tenant đó, vì vốn dĩ KHÔNG được
     * thiết kế để nhân bản — xem lịch sử đợt 11 gây regression). Tách hẳn 1
     * {@code identityEntityManagerFactory} RIÊNG (xem {@code MultiPersistenceUnitJpaConfig}), LUÔN
     * trỏ tới đúng 1 database này — không còn phụ thuộc {@link TenantContext} — giải quyết TẬN GỐC.
     */
    @Bean(name = "identityDataSource")
    public DataSource identityDataSource(TenantDataSourcePool tenantDataSourcePool) {
        DataSource identityDataSource = tenantDataSourcePool.byTenantId().get(tenantProperties.getDefaultTenantId());
        if (identityDataSource == null) {
            log.warn(
                "app.multitenancy.default-tenant-id='{}' chua co DataSource - danh muc dung chung " +
                "(AppUser/Organization/RoleGroup...) se KHONG dung duoc cho toi khi tenant nay duoc " +
                "dang ky (xem PlatformTenantsController)", tenantProperties.getDefaultTenantId());
        }
        return identityDataSource;
    }

    private record TenantRow(String tenantId, String host, int port, String databaseName,
                              String username, String encryptedPassword, String driverClassName,
                              boolean active) {
    }

    /** Xem javadoc {@link #tenantDataSourcePool} — KHÔNG được thay bằng {@code Map<String, DataSource>}
     * trần (Spring coi đó là yêu cầu tự động gom TẤT CẢ bean {@code DataSource}, gây vòng lặp). */
    record TenantDataSourcePool(Map<String, DataSource> byTenantId) {
    }
}
