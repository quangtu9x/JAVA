package com.td.application.tenant;

import lombok.Data;

// BL-117. KHÔNG có tenantId/databaseName — bất biến sau khi tạo (xem Tenant.update). Để xoay vòng
// credential phải gửi ĐỦ host + port + username + password CÙNG LÚC (xem UpdateTenantUseCase) —
// tránh phải giải mã lại mật khẩu cũ khi chỉ đổi 1 phần thông tin kết nối.
@Data
public class UpdateTenantRequest {
    private String displayName;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String driverClassName;
}
