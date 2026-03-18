package com.td.application.workflows;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowCancelRequest {

    @NotBlank(message = "Cancel reason is required")
    private String reason;

    private String comment;
}
