package com.td.application.workflows;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class WorkflowStepDto {

    private UUID id;

    private String stepCode;

    private String stepName;

    private String stepType;

    private int stepOrder;

    private boolean start;

    private boolean end;

    private Integer slaHours;

    private List<String> allowedRoles = new ArrayList<>();
}
