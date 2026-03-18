package com.td.application.workflows;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowStepRequest {

    @NotBlank(message = "Step code is required")
    private String stepCode;

    @NotBlank(message = "Step name is required")
    private String stepName;

    private String stepType = "USER_TASK";

    @Min(value = 1, message = "Step order must be at least 1")
    private int stepOrder = 1;

    private boolean start;

    private boolean end;

    @Min(value = 0, message = "SLA hours must be non-negative")
    private Integer slaHours;

    private List<String> allowedRoles = new ArrayList<>();
}
