package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.sharedcore.AppRoleDto;
import com.td.application.sharedcore.CreateAppRoleRequest;
import com.td.application.sharedcore.CreateAppRoleUseCase;
import com.td.application.sharedcore.DeleteAppRoleUseCase;
import com.td.application.sharedcore.GetAppRoleUseCase;
import com.td.application.sharedcore.SearchAppRolesRequest;
import com.td.application.sharedcore.SearchAppRolesUseCase;
import com.td.application.sharedcore.UpdateAppRoleRequest;
import com.td.application.sharedcore.UpdateAppRoleUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shared-core/roles")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shared Core - Roles", description = "Vai trò trong shared core (BL-111)")
public class SharedCoreRolesController extends BaseController {

    private final SearchAppRolesUseCase searchAppRolesUseCase;
    private final GetAppRoleUseCase getAppRoleUseCase;
    private final CreateAppRoleUseCase createAppRoleUseCase;
    private final UpdateAppRoleUseCase updateAppRoleUseCase;
    private final DeleteAppRoleUseCase deleteAppRoleUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách vai trò shared core")
    public ResponseEntity<ApiResponse<List<AppRoleDto>>> listRoles(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isSystemRole,
            @RequestParam(required = false) Boolean isActive) {

        var request = new SearchAppRolesRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setIsSystemRole(isSystemRole);
        request.setIsActive(isActive);

        return ok(searchAppRolesUseCase.execute(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chi tiết vai trò shared core")
    public ResponseEntity<ApiResponse<AppRoleDto>> getRole(
            @Parameter(description = "Role ID", required = true)
            @PathVariable UUID id) {
        return ok(getAppRoleUseCase.execute(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo mới vai trò shared core")
    public ResponseEntity<ApiResponse<UUID>> createRole(
            @Valid @RequestBody CreateAppRoleRequest request) {
        return created(createAppRoleUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật vai trò shared core",
        description = "Partial update — field null trong body nghĩa là không đổi.")
    public ResponseEntity<ApiResponse<UUID>> updateRole(
            @Parameter(description = "Role ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateAppRoleRequest request) {
        return ok(updateAppRoleUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm vai trò shared core")
    public ResponseEntity<ApiResponse<UUID>> deleteRole(
            @Parameter(description = "Role ID", required = true) @PathVariable UUID id) {
        return ok(deleteAppRoleUseCase.execute(id));
    }
}
