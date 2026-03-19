package com.td.web.controllers.v1;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.sharedcore.CreateOrganizationRequest;
import com.td.application.sharedcore.CreateOrganizationUseCase;
import com.td.application.sharedcore.DeleteOrganizationUseCase;
import com.td.application.sharedcore.GetLegacyOrganizationTreeUseCase;
import com.td.application.sharedcore.GetOrganizationUseCase;
import com.td.application.sharedcore.LegacyOrganizationTreeResponse;
import com.td.application.sharedcore.OrganizationDto;
import com.td.application.sharedcore.SearchOrganizationsRequest;
import com.td.application.sharedcore.SearchOrganizationsUseCase;
import com.td.application.sharedcore.UpdateOrganizationRequest;
import com.td.application.sharedcore.UpdateOrganizationUseCase;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shared-core/organizations")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shared Core - Organizations", description = "Tra cứu tổ chức trong shared core")
public class SharedCoreOrganizationsController extends BaseController {

    private final GetLegacyOrganizationTreeUseCase getLegacyOrganizationTreeUseCase;
    private final SearchOrganizationsUseCase searchOrganizationsUseCase;
    private final GetOrganizationUseCase getOrganizationUseCase;
    private final CreateOrganizationUseCase createOrganizationUseCase;
    private final UpdateOrganizationUseCase updateOrganizationUseCase;
    private final DeleteOrganizationUseCase deleteOrganizationUseCase;

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cay co cau to chuc theo format legacy")
    public ResponseEntity<LegacyOrganizationTreeResponse> getOrganizationTree(
            @RequestParam(name = "jedis_key", required = false) String jedisKey) {
        return ok(getLegacyOrganizationTreeUseCase.execute(jedisKey));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách tổ chức shared core")
    public ResponseEntity<PaginationResponse<OrganizationDto>> listOrganizations(
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(name = "sortBy", defaultValue = "sortOrder") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "asc") String sortDirection,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "parentId", required = false) UUID parentId,
            @RequestParam(name = "level", required = false) Integer level,
            @RequestParam(name = "isActive", required = false) Boolean isActive) {

        var request = new SearchOrganizationsRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setParentId(parentId);
        request.setLevel(level);
        request.setIsActive(isActive);

        return ok(searchOrganizationsUseCase.execute(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chi tiết tổ chức shared core")
    public ResponseEntity<Result<OrganizationDto>> getOrganization(
            @Parameter(description = "Organization ID", required = true)
            @PathVariable("id") UUID id) {
        return ok(getOrganizationUseCase.execute(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo node tổ chức shared core")
    public ResponseEntity<Result<UUID>> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        return created(createOrganizationUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật node tổ chức shared core")
    public ResponseEntity<Result<UUID>> updateOrganization(
            @Parameter(description = "Organization ID", required = true)
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return ok(updateOrganizationUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm node tổ chức shared core")
    public ResponseEntity<Result<UUID>> deleteOrganization(
            @Parameter(description = "Organization ID", required = true)
            @PathVariable("id") UUID id) {
        return ok(deleteOrganizationUseCase.execute(id));
    }
}
