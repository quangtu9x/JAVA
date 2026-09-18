package com.td.application.dbserver;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

// BL-117 (bổ sung 19/08/2026). Xem javadoc DbServer.java. LƯU Ý BẢO MẬT: có field `username` (không
// nhạy cảm) nhưng KHÔNG BAO GIỜ có field mật khẩu/encryptedPassword ở đây — chỉ trả `hasPassword` (cờ
// biết có credential đã lưu hay chưa). Lấy mật khẩu thật (giải mã) PHẢI qua endpoint riêng
// GET /{id}/credential (xem DbServerCredentialDto/GetDbServerCredentialUseCase).
@Data
public class DbServerDto {
    private UUID id;
    private String name;
    private String host;
    private int port;
    private String description;
    private String username;
    @JsonProperty("hasPassword")
    private boolean hasPassword;
    // Bug Lombok+Jackson đã xác nhận (docs/architecture/04-quy-tac-crud-api-shared-core.md): field
    // boolean "isXxx" bị Jackson serialize thành "xxx" nếu không ép bằng @JsonProperty.
    @JsonProperty("isActive")
    private boolean active;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}
