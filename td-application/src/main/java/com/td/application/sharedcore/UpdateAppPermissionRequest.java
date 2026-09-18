package com.td.application.sharedcore;

import jakarta.validation.constraints.Size;
import lombok.Data;

// Partial update: field null = không đổi (cùng convention với UpdateOrganizationRequest).
@Data
public class UpdateAppPermissionRequest {

    @Size(max = 200, message = "Tên quyền tối đa 200 ký tự")
    private String name;

    @Size(max = 100, message = "Nhóm quyền tối đa 100 ký tự")
    private String moduleKey;

    private String description;

    private Boolean isActive;
}
