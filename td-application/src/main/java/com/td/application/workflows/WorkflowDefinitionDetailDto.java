package com.td.application.workflows;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class WorkflowDefinitionDetailDto {

    private UUID id;

    private String workflowCode;

    private String workflowName;

    private String appliesTo;

    private int versionNo;

    private boolean active;

    private String description;

    private LocalDateTime createdOn;

    private LocalDateTime lastModifiedOn;

    private List<WorkflowStepDto> steps = new ArrayList<>();

    private List<WorkflowTransitionDto> transitions = new ArrayList<>();
}
