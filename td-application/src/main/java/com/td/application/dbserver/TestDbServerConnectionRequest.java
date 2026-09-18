package com.td.application.dbserver;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TestDbServerConnectionRequest {

    @NotBlank(message = "Host không được để trống")
    private String host;

    @NotNull(message = "Port không được để trống")
    @Min(value = 1, message = "Port không hợp lệ")
    @Max(value = 65535, message = "Port không hợp lệ")
    private Integer port;

    @NotBlank(message = "Username không được để trống")
    private String username;

    @NotBlank(message = "Password không được để trống")
    private String password;
}
