package com.td.application.sharedcore;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AppUserDto {
    private UUID id;
    private String keycloakSubject;
    private String username;
    private String fullName;
    private String email;
    private UUID organizationId;
    private UUID positionId;
    private boolean isActive;
    private LocalDateTime createdOn;
    private LocalDateTime lastModifiedOn;
}
