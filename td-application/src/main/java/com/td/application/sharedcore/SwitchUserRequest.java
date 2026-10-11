package com.td.application.sharedcore;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Port TDSwitchUser (Domino) — xem docs/analysis/19-checklist-port-tdswitchuser-domino-sang-java.md.
 * Giữ field {@code ma} tương thích luồng Domino cũ. Controller kiểm tra mã này cùng quyền ADMIN;
 * giá trị thật được đọc từ cấu hình môi trường và không trả về client.
 */
@Data
public class SwitchUserRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Mã bảo mật không được để trống")
    private String ma;
}
