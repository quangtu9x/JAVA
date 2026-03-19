package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.sharedcore.AppPermissionDto;
import com.td.application.sharedcore.GetAppPermissionUseCase;
import com.td.application.sharedcore.SearchAppPermissionsRequest;
import com.td.application.sharedcore.SearchAppPermissionsUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shared-core/permissions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shared Core - Permissions", description = "Tra cứu quyền trong shared core")
public class SharedCorePermissionsController extends BaseController {

    private final SearchAppPermissionsUseCase searchAppPermissionsUseCase;
    private final GetAppPermissionUseCase getAppPermissionUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách quyền shared core")
    public ResponseEntity<PaginationResponse<AppPermissionDto>> listPermissions(
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
    public ResponseEntity<Result<AppPermissionDto>> getPermission(
            @Parameter(description = "Permission ID", required = true)
            @PathVariable UUID id) {
        return ok(getAppPermissionUseCase.execute(id));
    }
}
