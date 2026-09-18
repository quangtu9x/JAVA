package com.td.application.privatecatalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class PrivateCategoryDto {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private String form;
    private UUID parentId;
    private int level;
    private String fullPath;
    private int sortOrder;
    // Bug Lombok+Jackson đã xác nhận (18/08/2026, docs/architecture/04-quy-tac-crud-api-shared-core.md):
    // field boolean tên "isXxx" bị Jackson serialize thành "xxx" (mất "is") nếu không ép bằng
    // @JsonProperty.
    @JsonProperty("isActive")
    private boolean isActive;
    // Mã định danh đơn vị/phòng ban (currentUser.identifier bên FE) — xem PrivateCategory.identifier.
    private String identifier;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
    private Map<String, Object> dynamicFields;
}
