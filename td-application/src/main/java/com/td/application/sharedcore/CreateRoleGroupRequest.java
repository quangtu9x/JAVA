package com.td.application.sharedcore;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

// BL-111: Vai trò người dùng — "Nhóm vai trò" (RoleGroup), đơn vị thực sự gán cho user
// (AppUser.roleGroupId). Domino gốc lưu tên/vai trò con/quyền trực tiếp trong 1 lần lưu duy nhất
// (LuuDanhMuc) nên request này nhận luôn roleIds/permissionIds thay vì tách endpoint riêng.
@Data
public class CreateRoleGroupRequest {

    @NotBlank(message = "Tên nhóm vai trò không được để trống")
    @Size(max = 200, message = "Tên nhóm vai trò tối đa 200 ký tự")
    private String name;

    private Integer sortOrder = 0;

    // Domino field "limit": true="Toàn bộ" (mặc định), false="Quản trị tổng".
    private Boolean fullAccess = true;

    private Boolean isActive = true;

    // Danh sách "vai trò" con (tương đương list_role_obj phía Domino).
    private List<UUID> roleIds = List.of();

    // Danh sách "quyền" cấp trực tiếp (tương đương field `roles` phía Domino).
    private List<UUID> permissionIds = List.of();
}
