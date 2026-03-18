package com.td.application.workflows;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class DelegateWorkflowTaskRequest {

    @NotNull(message = "Assignee user ID is required")
    private UUID assigneeUserId;

    private String reason;
}
