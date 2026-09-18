package com.td.application.sharedcore;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

// Partial update: field null = không đổi. Riêng roleIds/permissionIds: null = không đổi danh sách
// hiện có, khác giá trị (kể cả danh sách rỗng) = THAY TOÀN BỘ danh sách (không phải merge — đúng
// hành vi "chọn lại từ đầu" của picker phía FE, khác cơ chế MERGE của dynamicFields).
@Data
public class UpdateRoleGroupRequest {

    @Size(max = 200, message = "Tên nhóm vai trò tối đa 200 ký tự")
    private String name;

    private Integer sortOrder;

    private Boolean fullAccess;

    private Boolean isActive;

    private List<UUID> roleIds;

    private List<UUID> permissionIds;
}
