package com.td.application.workflows;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateWorkflowDefinitionRequest {

    @NotBlank(message = "Workflow code is required")
    private String workflowCode;

    @NotBlank(message = "Workflow name is required")
    private String workflowName;

    private String appliesTo = "DOCUMENT";

    private String description;

    @Valid
    @NotEmpty(message = "At least one step is required")
    private List<WorkflowStepRequest> steps = new ArrayList<>();

    @Valid
    private List<WorkflowTransitionRequest> transitions = new ArrayList<>();
}
