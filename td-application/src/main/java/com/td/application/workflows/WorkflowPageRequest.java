package com.td.application.workflows;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class WorkflowPageRequest {

    private String workflowCode;

    private Boolean active;

    @Min(value = 0, message = "Page number must be non-negative")
    private int pageNumber = 0;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private int pageSize = 10;

    private String sortBy = "createdOn";

    private String sortDirection = "desc";
}
