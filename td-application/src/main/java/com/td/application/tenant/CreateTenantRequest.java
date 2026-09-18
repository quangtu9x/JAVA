package com.td.application.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTenantRequest {

    @NotBlank(message = "Mã tenant không được để trống")
    @Size(max = 63, message = "Mã tenant tối đa 63 ký tự")
    private String tenantId;

    @NotBlank(message = "Tên hiển thị đơn vị không được để trống")
    @Size(max = 300, message = "Tên hiển thị tối đa 300 ký tự")
    private String displayName;

    @NotBlank(message = "Host không được để trống")
    private String host;

    @NotNull(message = "Port không được để trống")
    private Integer port;

    // Không bắt buộc — tự sinh từ tenantId nếu bỏ trống (xem CreateTenantUseCase).
    private String databaseName;

    @NotBlank(message = "Username không được để trống")
    private String username;

    // Plaintext — chỉ tồn tại trong request lúc xử lý, KHÔNG bao giờ được lưu/log/trả về dạng thô.
    @NotBlank(message = "Password không được để trống")
    private String password;

    // Không bắt buộc — mặc định org.postgresql.Driver.
    private String driverClassName;

    // Không bắt buộc — mặc định CREATE_NEW.
    private TenantProvisionMode mode;
}
