package com.td.application.workflows;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class WorkflowTransitionDto {

    private UUID id;

    private String fromStepCode;

    private String toStepCode;

    private String actionCode;

    private String actionName;

    private List<String> requiredRoles = new ArrayList<>();

    private boolean auto;
}
