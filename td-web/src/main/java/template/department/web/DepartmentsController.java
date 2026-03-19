package template.department.web;

import com.td.application.common.models.CachedPaginationResponse;
import com.td.application.common.models.CachedResult;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
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
import template.department.application.CreateDepartmentRequest;
import template.department.application.CreateDepartmentUseCase;
import template.department.application.DeleteDepartmentUseCase;
import template.department.application.DepartmentCacheService;
import template.department.application.DepartmentCacheStatsDto;
import template.department.application.DepartmentDto;
import template.department.application.GetDepartmentUseCase;
import template.department.application.SearchDepartmentsRequest;
import template.department.application.SearchDepartmentsUseCase;
import template.department.application.UpdateDepartmentRequest;
import template.department.application.UpdateDepartmentUseCase;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Validated
@Tag(name = "Departments", description = "Template quản lý phòng ban phân cấp")
public class DepartmentsController extends BaseController {

    private final CreateDepartmentUseCase createDepartmentUseCase;
    private final UpdateDepartmentUseCase updateDepartmentUseCase;
    private final DeleteDepartmentUseCase deleteDepartmentUseCase;
    private final GetDepartmentUseCase getDepartmentUseCase;
    private final SearchDepartmentsUseCase searchDepartmentsUseCase;
    private final DepartmentCacheService departmentCacheService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách phòng ban template")
    public ResponseEntity<PaginationResponse<DepartmentDto>> listDepartments(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "sortOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "true") boolean useCache) {

        var request = new SearchDepartmentsRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setParentId(parentId);
        request.setLevel(level);
        request.setIsActive(isActive);

        if (useCache) {
            var cached = departmentCacheService.getList(request);
            if (cached != null) {
                return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(new CachedPaginationResponse<>(cached,
                        departmentCacheService.getDepartmentListCacheKey(request)));
            }
        }

        var response = searchDepartmentsUseCase.execute(request);
        departmentCacheService.putList(request, response);
        return ResponseEntity.ok().header("X-Cache", "MISS").body(response);
    }

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tìm kiếm phòng ban template")
    public ResponseEntity<PaginationResponse<DepartmentDto>> searchDepartments(
            @Valid @RequestBody SearchDepartmentsRequest request,
            @RequestParam(defaultValue = "true") boolean useCache) {

        if (useCache) {
            var cached = departmentCacheService.getList(request);
            if (cached != null) {
                return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(new CachedPaginationResponse<>(cached,
                        departmentCacheService.getDepartmentListCacheKey(request)));
            }
        }

        var response = searchDepartmentsUseCase.execute(request);
        departmentCacheService.putList(request, response);
        return ResponseEntity.ok().header("X-Cache", "MISS").body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Chi tiết phòng ban template")
    public ResponseEntity<Result<DepartmentDto>> getDepartment(
            @Parameter(description = "Department ID", required = true) @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean useCache) {

        boolean cached = useCache && departmentCacheService.isCachedById(id);
        if (!useCache) {
            departmentCacheService.evict(id);
        }

        var result = getDepartmentUseCase.execute(id);
        return ResponseEntity.ok()
            .header("X-Cache", cached ? "HIT" : "MISS")
            .body(cached
                ? new CachedResult<>(result, departmentCacheService.getDepartmentByIdCacheKey(id))
                : result);
    }

    @GetMapping("/cache/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Thống kê cache phòng ban template")
    public ResponseEntity<Result<DepartmentCacheStatsDto>> getCacheStats() {
        return ok(Result.success(departmentCacheService.getStats()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo phòng ban template")
    public ResponseEntity<Result<UUID>> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        return created(createDepartmentUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật phòng ban template")
    public ResponseEntity<Result<UUID>> updateDepartment(
            @Parameter(description = "Department ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ok(updateDepartmentUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm phòng ban template")
    public ResponseEntity<Result<UUID>> deleteDepartment(
            @Parameter(description = "Department ID", required = true) @PathVariable UUID id) {
        return ok(deleteDepartmentUseCase.execute(id));
    }
}