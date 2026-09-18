package com.td.application.dbserver;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDbServerRequest {

    @NotBlank(message = "Tên máy chủ không được để trống")
    @Size(max = 200, message = "Tên máy chủ tối đa 200 ký tự")
    private String name;

    @NotBlank(message = "Host không được để trống")
    @Size(max = 255, message = "Host tối đa 255 ký tự")
    private String host;

    @NotNull(message = "Port không được để trống")
    @Min(value = 1, message = "Port không hợp lệ")
    @Max(value = 65535, message = "Port không hợp lệ")
    private Integer port;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String description;

    // BL-117 (bổ sung 19/08/2026): để CẢ HAI trống = GIỮ NGUYÊN credential đã lưu (nếu có) — KHÔNG
    // xoá bằng cách gửi request thiếu field này (khác hẳn `description`, gửi thiếu = xoá). Gửi ĐỦ CẢ
    // HAI = thay thế credential cũ. Xem UpdateDbServerUseCase.
    private String username;

    private String password;
}
