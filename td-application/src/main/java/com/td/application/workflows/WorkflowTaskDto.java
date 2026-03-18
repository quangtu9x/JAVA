package com.td.application.workflows;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WorkflowTaskDto {

    private UUID id;

    private UUID workflowInstanceId;

    private String stepCode;

    private String taskStatus;

    private UUID assigneeUserId;

    private String assigneeGroupCode;

    private LocalDateTime dueOn;

    private LocalDateTime createdOn;

    private LocalDateTime completedOn;
}
