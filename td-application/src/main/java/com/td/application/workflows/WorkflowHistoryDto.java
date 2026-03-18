package com.td.application.workflows;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WorkflowHistoryDto {

    private long id;

    private UUID workflowInstanceId;

    private String fromStepCode;

    private String toStepCode;

    private String fromStatus;

    private String toStatus;

    private String actionCode;

    private String actionName;

    private UUID actorId;

    private String actorUsername;

    private String actionComment;

    private String correlationId;

    private LocalDateTime occurredOn;
}
