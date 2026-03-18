package com.td.application.workflows;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowDefinitionFacade {

    public PaginationResponse<WorkflowDefinitionSummaryDto> listDefinitions(WorkflowPageRequest request) {
        int pageNumber = request == null ? 0 : Math.max(0, request.getPageNumber());
        int pageSize = request == null ? 10 : Math.min(Math.max(1, request.getPageSize()), 100);

        return new PaginationResponse<>(
            List.of(),
            pageNumber,
            pageSize,
            0L,
            0,
            true,
            true
        );
    }

    public Result<WorkflowDefinitionDetailDto> getDefinition(UUID definitionId) {
        if (definitionId == null) {
            return Result.failure("Workflow definition ID is required");
        }

        WorkflowDefinitionDetailDto dto = new WorkflowDefinitionDetailDto();
        dto.setId(definitionId);
        dto.setWorkflowCode("SCAFFOLD");
        dto.setWorkflowName("Workflow scaffold placeholder");
        dto.setAppliesTo("DOCUMENT");
        dto.setVersionNo(1);
        dto.setActive(true);
        dto.setDescription("Scaffold response. Replace with repository-backed implementation.");
        dto.setCreatedOn(LocalDateTime.now());
        dto.setLastModifiedOn(LocalDateTime.now());
        dto.setSteps(new ArrayList<>());
        dto.setTransitions(new ArrayList<>());

        return Result.success(dto);
    }

    public Result<UUID> createDefinition(CreateWorkflowDefinitionRequest request) {
        if (request == null) {
            return Result.failure("Request body is required");
        }

        if (request.getSteps() == null || request.getSteps().isEmpty()) {
            return Result.failure("At least one workflow step is required");
        }

        return Result.success(UUID.randomUUID());
    }

    public Result<UUID> updateDefinition(UUID definitionId, UpdateWorkflowDefinitionRequest request) {
        if (definitionId == null) {
            return Result.failure("Workflow definition ID is required");
        }

        if (request == null) {
            return Result.failure("Request body is required");
        }

        return Result.success(definitionId);
    }

    public Result<UUID> publishDefinition(UUID definitionId) {
        if (definitionId == null) {
            return Result.failure("Workflow definition ID is required");
        }

        return Result.success(definitionId);
    }
}
