package com.td.application.workflows;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowTransitionRequest {

    @NotBlank(message = "From step code is required")
    private String fromStepCode;

    @NotBlank(message = "To step code is required")
    private String toStepCode;

    @NotBlank(message = "Action code is required")
    private String actionCode;

    @NotBlank(message = "Action name is required")
    private String actionName;

    private List<String> requiredRoles = new ArrayList<>();

    private boolean auto;

    private String conditionExpression;
}
