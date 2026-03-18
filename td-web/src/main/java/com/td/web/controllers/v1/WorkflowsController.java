package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.workflows.CreateWorkflowDefinitionRequest;
import com.td.application.workflows.CreateWorkflowInstanceRequest;
import com.td.application.workflows.DelegateWorkflowTaskRequest;
import com.td.application.workflows.UpdateWorkflowDefinitionRequest;
import com.td.application.workflows.WorkflowActionRequest;
import com.td.application.workflows.WorkflowCancelRequest;
import com.td.application.workflows.WorkflowDefinitionDetailDto;
import com.td.application.workflows.WorkflowDefinitionFacade;
import com.td.application.workflows.WorkflowDefinitionSummaryDto;
import com.td.application.workflows.WorkflowHistoryDto;
import com.td.application.workflows.WorkflowInstanceDto;
import com.td.application.workflows.WorkflowPageRequest;
import com.td.application.workflows.WorkflowRuntimeFacade;
import com.td.application.workflows.WorkflowTaskDto;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Validated
@Tag(name = "Workflows", description = "Workflow definitions and runtime operations")
public class WorkflowsController extends BaseController {

    private final WorkflowDefinitionFacade workflowDefinitionFacade;
    private final WorkflowRuntimeFacade workflowRuntimeFacade;

    @PostMapping("/definitions")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_ADMIN')")
    @Operation(summary = "Create workflow definition")
    public ResponseEntity<Result<UUID>> createDefinition(@Valid @RequestBody CreateWorkflowDefinitionRequest request) {
        return created(workflowDefinitionFacade.createDefinition(request));
    }

    @PutMapping("/definitions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_ADMIN')")
    @Operation(summary = "Update workflow definition")
    public ResponseEntity<Result<UUID>> updateDefinition(
            @Parameter(description = "Workflow definition ID", required = true) @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateWorkflowDefinitionRequest request) {
        return ok(workflowDefinitionFacade.updateDefinition(id, request));
    }

    @PostMapping("/definitions/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_ADMIN')")
    @Operation(summary = "Publish workflow definition")
    public ResponseEntity<Result<UUID>> publishDefinition(
            @Parameter(description = "Workflow definition ID", required = true) @PathVariable("id") UUID id) {
        return ok(workflowDefinitionFacade.publishDefinition(id));
    }

    @GetMapping("/definitions")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_ADMIN', 'USER')")
    @Operation(summary = "List workflow definitions")
    public ResponseEntity<PaginationResponse<WorkflowDefinitionSummaryDto>> listDefinitions(
            @RequestParam(name = "workflowCode", required = false) String workflowCode,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "sortBy", defaultValue = "createdOn") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "desc") String sortDirection) {

        WorkflowPageRequest request = new WorkflowPageRequest();
        request.setWorkflowCode(workflowCode);
        request.setActive(active);
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);

        return ok(workflowDefinitionFacade.listDefinitions(request));
    }

    @GetMapping("/definitions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_ADMIN', 'USER')")
    @Operation(summary = "Get workflow definition detail")
    public ResponseEntity<Result<WorkflowDefinitionDetailDto>> getDefinition(
            @Parameter(description = "Workflow definition ID", required = true) @PathVariable("id") UUID id) {
        return ok(workflowDefinitionFacade.getDefinition(id));
    }

    @PostMapping("/instances")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'WORKFLOW_ADMIN', 'DOC_EDITOR', 'DOC_APPROVER')")
    @Operation(summary = "Create workflow instance")
    public ResponseEntity<Result<UUID>> createInstance(@Valid @RequestBody CreateWorkflowInstanceRequest request) {
        return created(workflowRuntimeFacade.createInstance(request));
    }

    @GetMapping("/instances/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'WORKFLOW_ADMIN', 'DOC_EDITOR', 'DOC_APPROVER')")
    @Operation(summary = "Get workflow instance")
    public ResponseEntity<Result<WorkflowInstanceDto>> getInstance(
            @Parameter(description = "Workflow instance ID", required = true) @PathVariable("id") UUID id) {
        return ok(workflowRuntimeFacade.getInstance(id));
    }

    @PostMapping("/instances/{id}/actions/{actionCode}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'WORKFLOW_ADMIN', 'DOC_EDITOR', 'DOC_APPROVER')")
    @Operation(summary = "Execute workflow action")
    public ResponseEntity<Result<UUID>> executeAction(
            @Parameter(description = "Workflow instance ID", required = true) @PathVariable("id") UUID id,
            @Parameter(description = "Action code", required = true) @PathVariable("actionCode") String actionCode,
            @Valid @RequestBody WorkflowActionRequest request) {
        return ok(workflowRuntimeFacade.executeAction(id, actionCode, request));
    }

    @PostMapping("/instances/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_ADMIN')")
    @Operation(summary = "Cancel workflow instance")
    public ResponseEntity<Result<UUID>> cancelInstance(
            @Parameter(description = "Workflow instance ID", required = true) @PathVariable("id") UUID id,
            @Valid @RequestBody WorkflowCancelRequest request) {
        return ok(workflowRuntimeFacade.cancelInstance(id, request));
    }

    @GetMapping("/instances/{id}/history")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'WORKFLOW_ADMIN', 'DOC_EDITOR', 'DOC_APPROVER')")
    @Operation(summary = "Get workflow history")
    public ResponseEntity<PaginationResponse<WorkflowHistoryDto>> getHistory(
            @Parameter(description = "Workflow instance ID", required = true) @PathVariable("id") UUID id,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ok(workflowRuntimeFacade.getHistory(id, pageNumber, pageSize));
    }

    @GetMapping("/tasks/my")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'WORKFLOW_ADMIN', 'DOC_EDITOR', 'DOC_APPROVER')")
    @Operation(summary = "Get my workflow tasks")
    public ResponseEntity<PaginationResponse<WorkflowTaskDto>> getMyTasks(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ok(workflowRuntimeFacade.getMyTasks(status, pageNumber, pageSize));
    }

    @GetMapping("/tasks/group/{groupCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_ADMIN', 'DOC_APPROVER')")
    @Operation(summary = "Get group workflow tasks")
    public ResponseEntity<PaginationResponse<WorkflowTaskDto>> getGroupTasks(
            @Parameter(description = "Group code", required = true) @PathVariable("groupCode") String groupCode,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ok(workflowRuntimeFacade.getGroupTasks(groupCode, status, pageNumber, pageSize));
    }

    @PostMapping("/tasks/{taskId}/claim")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'WORKFLOW_ADMIN', 'DOC_APPROVER')")
    @Operation(summary = "Claim workflow task")
    public ResponseEntity<Result<UUID>> claimTask(
            @Parameter(description = "Task ID", required = true) @PathVariable("taskId") UUID taskId) {
        return ok(workflowRuntimeFacade.claimTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/delegate")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_ADMIN', 'DOC_APPROVER')")
    @Operation(summary = "Delegate workflow task")
    public ResponseEntity<Result<UUID>> delegateTask(
            @Parameter(description = "Task ID", required = true) @PathVariable("taskId") UUID taskId,
            @Valid @RequestBody DelegateWorkflowTaskRequest request) {
        return ok(workflowRuntimeFacade.delegateTask(taskId, request));
    }
}
