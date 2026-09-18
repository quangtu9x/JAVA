package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class AppRoleDto {
    private UUID id;
    private String code;
    private String name;
    private String description;
    // Bug Lombok+Jackson đã xác nhận (18/08/2026, docs/architecture/04-quy-tac-crud-api-shared-core.md):
    // field boolean tên "isXxx" bị Jackson serialize thành "xxx" (mất "is") nếu không ép bằng
    // @JsonProperty — FE luôn đọc dto.isActive/dto.isSystemRole là undefined, tưởng "khoá" mãi mãi.
    @JsonProperty("isSystemRole")
    private boolean isSystemRole;
    @JsonProperty("isActive")
    private boolean isActive;
    private int sortOrder;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;

    // Field tự định nghĩa (cột dynamic_fields JSONB) — menu_top/menu_vbden/.../access_level của
    // "Vai trò" Domino gốc, xem AppRole.java (BL-111).
    private Map<String, Object> dynamicFields;
}
