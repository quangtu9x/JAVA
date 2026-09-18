package com.td.domain.tenant;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// BL-117: registry tenant thật (database-per-tenant) — sống trong database "control-plane" (schema
// mặc định, KHÔNG phải database nghiệp vụ của bất kỳ tenant nào), cùng entity `Category` (xem
// ControlPlaneDataSourceConfig, EntityManagerFactory RIÊNG). Đây là bảng thay thế hoàn toàn YAML tĩnh
// `app.multitenancy.tenants` của BL-303 cũ — đọc động lúc khởi động (TenantDataSourceConfig) và qua
// PlatformTenantsController lúc runtime (hot-add connection pool, không cần restart app).
//
// LƯU Ý bảo mật: `encryptedPassword` là mật khẩu DB của TENANT (không phải mật khẩu người dùng cuối)
// đã mã hoá 2 CHIỀU (không phải hash 1 chiều như BCrypt) bằng TenantCredentialCipher — bắt buộc vì
// cần giải mã lại để mở JDBC connection tới database riêng của tenant đó. `tenantId`/`databaseName`
// là định danh BẤT BIẾN sau khi tạo (đổi = phải tạo tenant mới, xem UpdateTenantUseCase).
@Entity
@Table(name = "tenants")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(name = "tenant_id", nullable = false, length = 100, unique = true)
    private String tenantId;

    @Column(name = "display_name", nullable = false, length = 300)
    private String displayName;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "encrypted_password", nullable = false, columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(name = "driver_class_name", nullable = false, length = 200)
    private String driverClassName = "org.postgresql.Driver";

    @Column(nullable = false)
    private boolean active = true;

    public Tenant(String tenantId, String displayName, String host, int port, String databaseName,
                  String username, String encryptedPassword, String driverClassName) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.displayName = displayName;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.driverClassName = (driverClassName != null && !driverClassName.isBlank())
            ? driverClassName : "org.postgresql.Driver";
        this.active = true;
    }

    // KHÔNG nhận tenantId/databaseName — bất biến sau khi tạo (xem javadoc lớp). Đổi host/username/
    // encryptedPassword = xoay vòng credential (vd. đổi mật khẩu Postgres định kỳ), KHÔNG đổi định
    // danh/database mà connection đang trỏ tới.
    public Tenant update(String displayName, String host, Integer port, String username,
                          String encryptedPassword, String driverClassName) {
        if (displayName != null)       this.displayName = displayName;
        if (host != null)              this.host = host;
        if (port != null)              this.port = port;
        if (username != null)          this.username = username;
        if (encryptedPassword != null) this.encryptedPassword = encryptedPassword;
        if (driverClassName != null)   this.driverClassName = driverClassName;
        return this;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
