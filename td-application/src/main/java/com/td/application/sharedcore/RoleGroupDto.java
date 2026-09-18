package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class RoleGroupDto {
    private UUID id;
    private String name;
    private int sortOrder;
    // fullAccess KHÔNG bị lỗi (tên field không có tiền tố "is" nên Lombok/Jackson round-trip đúng
    // "fullAccess" cả 2 chiều) — chỉ isActive bị (xem docs/architecture/04-quy-tac-crud-api-shared-core.md).
    private boolean fullAccess;
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;

    // Danh sách "vai trò" con (tương đương list_role/list_role_obj phía Domino).
    private List<UUID> roleIds;
    private List<String> roleNames;

    // Danh sách "quyền" cấp trực tiếp (tương đương field `roles` phía Domino) — độc lập với danh
    // sách vai trò con ở trên.
    private List<UUID> permissionIds;
    private List<String> permissionCodes;
}
