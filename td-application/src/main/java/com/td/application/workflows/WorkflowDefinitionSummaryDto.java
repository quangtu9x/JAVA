package com.td.application.workflows;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WorkflowDefinitionSummaryDto {

    private UUID id;

    private String workflowCode;

    private String workflowName;

    private String appliesTo;

    private int versionNo;

    private boolean active;

    private String description;

    private LocalDateTime createdOn;

    private LocalDateTime lastModifiedOn;
}
