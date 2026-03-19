package com.td.application.sharedcore;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AppPermissionDto {
    private UUID id;
    private String code;
    private String name;
    private String moduleKey;
    private String description;
    private boolean isActive;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}
