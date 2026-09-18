package com.td.application.tenant;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

// BL-117. LƯU Ý BẢO MẬT: KHÔNG có field mật khẩu/encryptedPassword nào ở đây — API tenant KHÔNG
// BAO GIỜ trả credential dưới bất kỳ hình thức nào (kể cả đã mã hoá), chỉ trả thông tin nhận diện
// kết nối (host/port/database/username).
@Data
public class TenantDto {
    private UUID id;
    private String tenantId;
    private String displayName;
    private String host;
    private int port;
    private String databaseName;
    private String username;
    private String driverClassName;
    // Bug Lombok+Jackson đã xác nhận (docs/architecture/04-quy-tac-crud-api-shared-core.md): field
    // boolean "isXxx" bị Jackson serialize thành "xxx" nếu không ép bằng @JsonProperty.
    @JsonProperty("isActive")
    private boolean active;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}
