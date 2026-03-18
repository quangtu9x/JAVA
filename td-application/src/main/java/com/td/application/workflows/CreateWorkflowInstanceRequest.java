package com.td.application.workflows;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateWorkflowInstanceRequest {

    @NotBlank(message = "Definition code is required")
    private String definitionCode;

    @Min(value = 1, message = "Definition version must be at least 1")
    private Integer definitionVersion = 1;

    @NotBlank(message = "Entity type is required")
    private String entityType = "DOCUMENT";

    @NotNull(message = "Entity ID is required")
    private UUID entityId;

    private Map<String, Object> context = new HashMap<>();
}
