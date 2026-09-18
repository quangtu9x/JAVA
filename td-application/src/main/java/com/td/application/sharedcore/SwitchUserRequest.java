package com.td.application.sharedcore;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Port TDSwitchUser (Domino) — xem docs/analysis/19-checklist-port-tdswitchuser-domino-sang-java.md.
 * CỐ TÌNH KHÔNG có field {@code ma} (mã bảo mật hardcode {@code "123@!@#Aa"} của Domino gốc) — gate
 * bằng {@code @PreAuthorize("hasRole('ADMIN')")} ở controller thay thế, Spring Security đã đủ.
 */
@Data
public class SwitchUserRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;
}
