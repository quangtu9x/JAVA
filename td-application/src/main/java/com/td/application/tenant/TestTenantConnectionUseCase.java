package com.td.application.tenant;

import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// BL-117: cho phép PLATFORM_ADMIN kiểm tra host/port/database/username/password TRƯỚC khi bấm Lưu
// thật (tạo database mới hoặc xoay vòng credential) — giảm rủi ro phát hiện lỗi kết nối SAU khi đã
// CREATE DATABASE thật hoặc đã ghi đè credential cũ. KHÔNG tạo/sửa/xoá bất kỳ thứ gì, chỉ mở thử 1
// connection (xem TenantProvisioningService.testConnection).
@Service
@RequiredArgsConstructor
@Slf4j
public class TestTenantConnectionUseCase {

    private final TenantProvisioningService tenantProvisioningService;

    public Result<Void> execute(TestTenantConnectionRequest request) {
        try {
            // tenantId/mode không có ý nghĩa với testConnection() (chỉ dùng host/port/database/
            // username/password) — truyền giá trị placeholder hợp lệ, không phải null, để tránh rủi
            // ro NPE nếu logic testConnection() được mở rộng đọc thêm field khác sau này.
            var command = new ProvisionTenantCommand(
                "test-connection", request.getHost(), request.getPort(), request.getDatabaseName(),
                request.getUsername(), request.getPassword(), null, TenantProvisionMode.ROTATE_CREDENTIAL_ONLY);
            tenantProvisioningService.testConnection(command);
            return Result.success(null);
        } catch (Exception ex) {
            log.warn("Kiem tra ket noi tenant that bai: {}", ex.getMessage());
            return Result.failure("Kết nối thất bại: " + ex.getMessage());
        }
    }
}
