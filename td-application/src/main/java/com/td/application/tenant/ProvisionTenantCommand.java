package com.td.application.tenant;

import lombok.Getter;

// BL-117 — tham số đầu vào cho TenantProvisioningService, mang mật khẩu PLAINTEXT (chỉ tồn tại
// trong bộ nhớ lúc xử lý request, KHÔNG bao giờ được lưu/log dạng thô — xem TenantCredentialCipher).
@Getter
public class ProvisionTenantCommand {

    private final String tenantId;
    private final String host;
    private final int port;
    private final String databaseName;
    private final String username;
    private final String plainPassword;
    private final String driverClassName;
    private final TenantProvisionMode mode;

    public ProvisionTenantCommand(String tenantId, String host, int port, String databaseName,
                                   String username, String plainPassword, String driverClassName,
                                   TenantProvisionMode mode) {
        this.tenantId = tenantId;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.plainPassword = plainPassword;
        this.driverClassName = (driverClassName != null && !driverClassName.isBlank())
            ? driverClassName : "org.postgresql.Driver";
        this.mode = mode;
    }
}
