package com.td.application.dbserver;

import lombok.Data;

// BL-117 (bổ sung 19/08/2026): DTO DUY NHẤT trong toàn bộ tính năng DbServer được phép mang mật khẩu
// PLAINTEXT (đã giải mã) — CHỦ Ý, chỉ trả về qua GET /{id}/credential (PLATFORM_ADMIN-only) khi admin
// chủ động chọn 1 máy chủ CSDL để autofill lúc khởi tạo đơn vị. Xem javadoc DbServer.java để biết lý
// do đây là ngoại lệ có chủ đích so với nguyên tắc "API không bao giờ trả mật khẩu" của Tenant.
@Data
public class DbServerCredentialDto {
    private String username;
    private String password;
}
