package com.td.application.workflows;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowRuntimeFacade {

    public Result<UUID> createInstance(CreateWorkflowInstanceRequest request) {
        if (request == null) {
            return Result.failure("Request body is required");
        }

        if (request.getEntityId() == null) {
            return Result.failure("Entity ID is required");
        }

        return Result.success(UUID.randomUUID());
    }

    public Result<WorkflowInstanceDto> getInstance(UUID instanceId) {
        if (instanceId == null) {
            return Result.failure("Workflow instance ID is required");
        }

        WorkflowInstanceDto dto = new WorkflowInstanceDto();
        dto.setId(instanceId);
        dto.setDefinitionId(null);
        dto.setDefinitionCode("SCaffold");
        dto.setEntityType("DOCUMENT");
        dto.setEntityId(null);
        dto.setCurrentStepCode("REVIEW");
        dto.setCurrentStatus("ACTIVE");
        dto.setBusinessStatus("IN_REVIEW");
        dto.setStartedBy(null);
        dto.setStartedOn(LocalDateTime.now());

        return Result.success(dto);
    }

    public Result<UUID> executeAction(UUID instanceId, String actionCode, WorkflowActionRequest request) {
        if (instanceId == null) {
            return Result.failure("Workflow instance ID is required");
        }

        if (actionCode == null || actionCode.isBlank()) {
            return Result.failure("Action code is required");
        }

        return Result.success(instanceId);
    }

    public Result<UUID> cancelInstance(UUID instanceId, WorkflowCancelRequest request) {
        if (instanceId == null) {
            return Result.failure("Workflow instance ID is required");
        }

        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            return Result.failure("Cancel reason is required");
        }

        return Result.success(instanceId);
    }

    public PaginationResponse<WorkflowHistoryDto> getHistory(UUID instanceId, int pageNumber, int pageSize) {
        int normalizedPage = Math.max(0, pageNumber);
        int normalizedSize = Math.min(Math.max(1, pageSize), 100);

        return new PaginationResponse<>(
            List.of(),
            normalizedPage,
            normalizedSize,
            0L,
            0,
            true,
            true
        );
    }

    public PaginationResponse<WorkflowTaskDto> getMyTasks(String status, int pageNumber, int pageSize) {
        return emptyTaskPage(pageNumber, pageSize);
    }

    public PaginationResponse<WorkflowTaskDto> getGroupTasks(String groupCode, String status, int pageNumber, int pageSize) {
        if (groupCode == null || groupCode.isBlank()) {
            return emptyTaskPage(pageNumber, pageSize);
        }

        return emptyTaskPage(pageNumber, pageSize);
    }

    public Result<UUID> claimTask(UUID taskId) {
        if (taskId == null) {
            return Result.failure("Task ID is required");
        }

        return Result.success(taskId);
    }

    public Result<UUID> delegateTask(UUID taskId, DelegateWorkflowTaskRequest request) {
        if (taskId == null) {
            return Result.failure("Task ID is required");
        }

        if (request == null || request.getAssigneeUserId() == null) {
            return Result.failure("Assignee user ID is required");
        }

        return Result.success(taskId);
    }

    private PaginationResponse<WorkflowTaskDto> emptyTaskPage(int pageNumber, int pageSize) {
        int normalizedPage = Math.max(0, pageNumber);
        int normalizedSize = Math.min(Math.max(1, pageSize), 100);

        return new PaginationResponse<>(
            List.of(),
            normalizedPage,
            normalizedSize,
            0L,
            0,
            true,
            true
        );
    }
}
