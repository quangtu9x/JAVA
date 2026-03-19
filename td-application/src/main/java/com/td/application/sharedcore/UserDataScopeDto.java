package com.td.application.sharedcore;

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
    private boolean isActive;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}
