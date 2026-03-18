package com.td.application.workflows;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WorkflowInstanceDto {

    private UUID id;

    private UUID definitionId;

    private String definitionCode;

    private String entityType;

    private UUID entityId;

    private String currentStepCode;

    private String currentStatus;

    private String businessStatus;

    private UUID startedBy;

    private LocalDateTime startedOn;

    private LocalDateTime completedOn;

    private LocalDateTime cancelledOn;

    private String cancelReason;
}
