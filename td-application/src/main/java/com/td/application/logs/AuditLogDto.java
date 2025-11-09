package com.td.application.logs;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class AuditLogDto {
    private String id;
    private String entityType;
    private UUID entityId;
    private String action;
    private UUID userId;
    private String userEmail;
    private LocalDateTime timestamp;
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
    private Map<String, Object> changes;
    private String ipAddress;
    private String userAgent;
}