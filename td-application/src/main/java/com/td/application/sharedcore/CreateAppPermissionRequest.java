package com.td.application.sharedcore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// BL-111: Vai trò người dùng — CRUD "Quyền" (AppPermission), trước chỉ có Get/Search (đọc).
@Data
public class CreateAppPermissionRequest {

    @NotBlank(message = "Mã quyền không được để trống")
    @Size(max = 100, message = "Mã quyền tối đa 100 ký tự")
    private String code;

    @NotBlank(message = "Tên quyền không được để trống")
    @Size(max = 200, message = "Tên quyền tối đa 200 ký tự")
    private String name;

    @NotBlank(message = "Nhóm quyền không được để trống")
    @Size(max = 100, message = "Nhóm quyền tối đa 100 ký tự")
    private String moduleKey;

    private String description;

    private Boolean isActive = true;
}
