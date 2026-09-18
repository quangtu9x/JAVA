package com.td.application.sharedcore;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserDataScopeDto {
    private UUID id;
    private UUID userId;
    private String scopeModule;
    private String scopeType;
    private UUID scopeOrgId;
    private String scopeValue;
    // Bug Lombok+Jackson đã xác nhận (18/08/2026, docs/architecture/04-quy-tac-crud-api-shared-core.md).
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}
