package com.td.application.logs;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SearchAuditLogsRequest {
    
    private String entityType;
    private UUID entityId;
    private UUID userId;
    private String action;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    @Min(value = 0, message = "Page number must be non-negative")
    private int pageNumber = 0;
    
    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private int pageSize = 10;
    
    private String sortBy = "timestamp";
    private String sortDirection = "desc";
}