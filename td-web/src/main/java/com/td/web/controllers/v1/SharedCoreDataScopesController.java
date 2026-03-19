package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.sharedcore.GetUserDataScopeUseCase;
import com.td.application.sharedcore.SearchUserDataScopesRequest;
import com.td.application.sharedcore.SearchUserDataScopesUseCase;
import com.td.application.sharedcore.UserDataScopeDto;
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
@RequestMapping("/api/v1/shared-core/data-scopes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shared Core - Data Scopes", description = "Tra cứu phạm vi dữ liệu trong shared core")
public class SharedCoreDataScopesController extends BaseController {

    private final SearchUserDataScopesUseCase searchUserDataScopesUseCase;
    private final GetUserDataScopeUseCase getUserDataScopeUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách data scope shared core")
    public ResponseEntity<PaginationResponse<UserDataScopeDto>> listDataScopes(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "scopeModule") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String scopeModule,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) UUID scopeOrgId,
            @RequestParam(required = false) Boolean isActive) {

        var request = new SearchUserDataScopesRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setUserId(userId);
        request.setScopeModule(scopeModule);
        request.setScopeType(scopeType);
        request.setScopeOrgId(scopeOrgId);
        request.setIsActive(isActive);

        return ok(searchUserDataScopesUseCase.execute(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chi tiết data scope shared core")
    public ResponseEntity<Result<UserDataScopeDto>> getDataScope(
            @Parameter(description = "Data Scope ID", required = true)
            @PathVariable UUID id) {
        return ok(getUserDataScopeUseCase.execute(id));
    }
}
