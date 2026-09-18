package com.td.application.categories;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class CategoryDto {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private String form;
    private UUID parentId;
    private int level;
    private String fullPath;
    private int sortOrder;
    // Bug Lombok+Jackson đã xác nhận (18/08/2026, docs/architecture/04-quy-tac-crud-api-shared-core.md).
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;

    // Field tự định nghĩa từ FE (cột dynamic_fields JSONB) — BL-109 mở rộng cho Category.
    private Map<String, Object> dynamicFields;
}
