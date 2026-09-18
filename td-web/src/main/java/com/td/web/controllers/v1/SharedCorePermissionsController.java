package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.sharedcore.AppPermissionDto;
import com.td.application.sharedcore.CreateAppPermissionRequest;
import com.td.application.sharedcore.CreateAppPermissionUseCase;
import com.td.application.sharedcore.DeleteAppPermissionUseCase;
import com.td.application.sharedcore.GetAppPermissionUseCase;
import com.td.application.sharedcore.SearchAppPermissionsRequest;
import com.td.application.sharedcore.SearchAppPermissionsUseCase;
import com.td.application.sharedcore.UpdateAppPermissionRequest;
import com.td.application.sharedcore.UpdateAppPermissionUseCase;
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
@RequestMapping("/api/v1/shared-core/permissions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shared Core - Permissions", description = "Quyền trong shared core (BL-111)")
public class SharedCorePermissionsController extends BaseController {

    private final SearchAppPermissionsUseCase searchAppPermissionsUseCase;
    private final GetAppPermissionUseCase getAppPermissionUseCase;
    private final CreateAppPermissionUseCase createAppPermissionUseCase;
    private final UpdateAppPermissionUseCase updateAppPermissionUseCase;
    private final DeleteAppPermissionUseCase deleteAppPermissionUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách quyền shared core")
    public ResponseEntity<ApiResponse<List<AppPermissionDto>>> listPermissions(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "moduleKey") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) Boolean isActive) {

        var request = new SearchAppPermissionsRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setModuleKey(moduleKey);
        request.setIsActive(isActive);

        return ok(searchAppPermissionsUseCase.execute(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chi tiết quyền shared core")
    public ResponseEntity<ApiResponse<AppPermissionDto>> getPermission(
            @Parameter(description = "Permission ID", required = true)
            @PathVariable UUID id) {
        return ok(getAppPermissionUseCase.execute(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo mới quyền shared core")
    public ResponseEntity<ApiResponse<UUID>> createPermission(
            @Valid @RequestBody CreateAppPermissionRequest request) {
        return created(createAppPermissionUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật quyền shared core",
        description = "Partial update — field null trong body nghĩa là không đổi.")
    public ResponseEntity<ApiResponse<UUID>> updatePermission(
            @Parameter(description = "Permission ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateAppPermissionRequest request) {
        return ok(updateAppPermissionUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm quyền shared core")
    public ResponseEntity<ApiResponse<UUID>> deletePermission(
            @Parameter(description = "Permission ID", required = true) @PathVariable UUID id) {
        return ok(deleteAppPermissionUseCase.execute(id));
    }
}
