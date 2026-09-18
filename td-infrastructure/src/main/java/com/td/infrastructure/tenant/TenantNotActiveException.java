package com.td.infrastructure.tenant;

// BL-117: ném bởi TenantResolver khi tenant XÁC ĐỊNH ĐƯỢC từ claim nhưng đã bị deactivate — bắt ở
// TenantFilter, trả 403 (KHÔNG fallback về tenant mặc định, tránh rò rỉ dữ liệu chéo tenant).
public class TenantNotActiveException extends RuntimeException {

    public TenantNotActiveException(String tenantId) {
        super("Tenant '" + tenantId + "' hien khong active - request bi tu choi");
    }
}
