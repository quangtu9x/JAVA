package com.td.application.sharedcore;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

// Partial update: field null = không đổi (cùng convention với UpdateOrganizationRequest).
@Data
public class UpdateAppRoleRequest {

    @Size(max = 200, message = "Tên vai trò tối đa 200 ký tự")
    private String name;

    private String description;

    private Boolean isSystemRole;

    private Boolean isActive;

    private Integer sortOrder;

    // null = không đổi field động; có giá trị thì MERGE vào field động hiện có (không xoá field cũ
    // không được nhắc tới) — xem UpdateAppRoleUseCase, cùng convention UpdateOrganizationRequest.
    private Map<String, String> dynamicFields;

    private String dateFields;

    private String numberFields;

    private String richtextFields;
}
