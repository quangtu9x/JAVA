package com.td.domain.logs;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Document(collection = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    private String id;
    
    @Field("entity_type")
    private String entityType;
    
    @Field("entity_id")
    private UUID entityId;
    
    @Field("action")
    private String action; // CREATE, UPDATE, DELETE
    
    @Field("user_id")
    private UUID userId;
    
    @Field("user_email")
    private String userEmail;
    
    @Field("timestamp")
    private LocalDateTime timestamp;
    
    @Field("old_values")
    private Map<String, Object> oldValues;
    
    @Field("new_values")
    private Map<String, Object> newValues;
    
    @Field("changes")
    private Map<String, Object> changes;
    
    @Field("ip_address")
    private String ipAddress;
    
    @Field("user_agent")
    private String userAgent;
    
    public AuditLog(String entityType, UUID entityId, String action, UUID userId, String userEmail) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.userId = userId;
        this.userEmail = userEmail;
        this.timestamp = LocalDateTime.now();
    }
}