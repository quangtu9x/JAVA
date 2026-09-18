package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Partial update: field null = không đổi (cùng convention với UpdateOrganizationRequest).
@Data
public class UpdateAppUserRequest {

    @Size(max = 100, message = "Tên đăng nhập tối đa 100 ký tự")
    private String username;

    @Size(max = 300, message = "Họ và tên tối đa 300 ký tự")
    private String fullName;

    private String email;

    private UUID organizationId;

    private UUID positionId;

    private Boolean isActive;

    private Integer level;

    private Integer sortOrder;

    private String phoneNumber;

    private String gender;

    private LocalDate birthdayDate;

    private String address;

    private String userIdNo;

    private String capLanhDao;

    @Size(max = 100, message = "Mã định danh tối đa 100 ký tự")
    private String identifier;

    private Boolean isSim;

    // Field tự định nghĩa từ FE — null nghĩa là không đổi field động (partial update); có giá trị
    // thì MERGE vào field động hiện có (không xoá field cũ không được nhắc tới), xem
    // UpdateAppUserUseCase. Cùng convention với UpdateOrganizationRequest (BL-109).
    private Map<String, String> dynamicFields;

    private String dateFields;

    private String numberFields;

    private String richtextFields;

    // "Nhóm vai trò" (BL-111, bổ sung 19/08/2026 — sửa lại thành N:N, xem AppUserRoleGroup.java) —
    // cần phân biệt "không gửi field" (giữ nguyên danh sách hiện có) với "gửi (kể cả mảng rỗng)"
    // (thay TOÀN BỘ danh sách) nên dùng cờ nội bộ, cùng convention với
    // UpdateOrganizationRequest.parentid/UpdateCategoryRequest.parentId.
    private boolean updateRoleGroups = false;
    private List<UUID> roleGroupIds;

    @JsonSetter("roleGroupIds")
    public void setRoleGroupIdsFromJson(List<UUID> roleGroupIds) {
        this.roleGroupIds = roleGroupIds;
        this.updateRoleGroups = true;
    }
}
