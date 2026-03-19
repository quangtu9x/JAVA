package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.sharedcore.AppUserDto;
import com.td.application.sharedcore.GetAppUserUseCase;
import com.td.application.sharedcore.SearchAppUsersRequest;
import com.td.application.sharedcore.SearchAppUsersUseCase;
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
@RequestMapping("/api/v1/shared-core/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shared Core - Users", description = "Tra cứu người dùng trong shared core")
public class SharedCoreUsersController extends BaseController {

    private final SearchAppUsersUseCase searchAppUsersUseCase;
    private final GetAppUserUseCase getAppUserUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách người dùng shared core")
    public ResponseEntity<PaginationResponse<AppUserDto>> listUsers(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "fullName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) Boolean isActive) {

        var request = new SearchAppUsersRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setOrganizationId(organizationId);
        request.setIsActive(isActive);

        return ok(searchAppUsersUseCase.execute(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chi tiết người dùng shared core")
    public ResponseEntity<Result<AppUserDto>> getUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable UUID id) {
        return ok(getAppUserUseCase.execute(id));
    }
}
