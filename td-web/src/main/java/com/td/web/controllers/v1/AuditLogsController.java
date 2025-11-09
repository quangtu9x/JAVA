package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.logs.AuditLogDto;
import com.td.application.logs.SearchAuditLogsRequest;
import com.td.domain.logs.AuditLog;
import com.td.infrastructure.persistence.mongo.AuditLogRepository;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Validated
@Tag(name = "Audit Logs", description = "Audit log management endpoints")
public class AuditLogsController extends BaseController {

    private final AuditLogRepository auditLogRepository;

    @PostMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search audit logs with filters")
    public ResponseEntity<PaginationResponse<AuditLogDto>> searchAuditLogs(
            @Valid @RequestBody SearchAuditLogsRequest request) {
        
        // Create pageable
        Sort sort = Sort.by(
            "desc".equalsIgnoreCase(request.getSortDirection()) 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC, 
            request.getSortBy()
        );
        
        Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize(), sort);
        
        // Execute query based on filters
        var page = auditLogRepository.findByTimestampBetween(
            request.getStartDate() != null ? request.getStartDate() : 
                java.time.LocalDateTime.now().minusDays(30),
            request.getEndDate() != null ? request.getEndDate() : 
                java.time.LocalDateTime.now(),
            pageable
        );
        
        // Map to DTOs
        List<AuditLogDto> auditLogDtos = page.getContent().stream()
            .map(this::mapToDto)
            .toList();
        
        var response = new PaginationResponse<>(
            auditLogDtos,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
        
        return ok(response);
    }

    private AuditLogDto mapToDto(AuditLog auditLog) {
        var dto = new AuditLogDto();
        dto.setId(auditLog.getId());
        dto.setEntityType(auditLog.getEntityType());
        dto.setEntityId(auditLog.getEntityId());
        dto.setAction(auditLog.getAction());
        dto.setUserId(auditLog.getUserId());
        dto.setUserEmail(auditLog.getUserEmail());
        dto.setTimestamp(auditLog.getTimestamp());
        dto.setOldValues(auditLog.getOldValues());
        dto.setNewValues(auditLog.getNewValues());
        dto.setChanges(auditLog.getChanges());
        dto.setIpAddress(auditLog.getIpAddress());
        dto.setUserAgent(auditLog.getUserAgent());
        return dto;
    }
}