package com.td.application.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// BL-117: kiểm tra kết nối TRƯỚC khi tạo/xoay vòng credential thật — không tạo database, không lưu
// gì, chỉ mở thử 1 connection rồi đóng ngay (xem TestTenantConnectionUseCase).
@Data
public class TestTenantConnectionRequest {

    @NotBlank(message = "Host không được để trống")
    private String host;

    @NotNull(message = "Port không được để trống")
    private Integer port;

    @NotBlank(message = "Tên database không được để trống")
    private String databaseName;

    @NotBlank(message = "Username không được để trống")
    private String username;

    @NotBlank(message = "Password không được để trống")
    private String password;
}
