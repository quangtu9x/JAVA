package com.td.application.workflows;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UpdateWorkflowDefinitionRequest {

    private String workflowCode;

    @NotBlank(message = "Workflow name is required")
    private String workflowName;

    private String appliesTo = "DOCUMENT";

    private String description;

    private Boolean active;

    @Valid
    private List<WorkflowStepRequest> steps = new ArrayList<>();

    @Valid
    private List<WorkflowTransitionRequest> transitions = new ArrayList<>();
}
