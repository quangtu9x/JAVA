package com.td.web.controllers.v1;

import com.td.application.categories.CategoryCacheService;
import com.td.application.categories.CategoryCacheStatsDto;
import com.td.application.categories.CategoryDto;
import com.td.application.categories.CreateCategoryRequest;
import com.td.application.categories.CreateCategoryUseCase;
import com.td.application.categories.DeleteCategoryUseCase;
import com.td.application.categories.GetCategoryUseCase;
import com.td.application.categories.SearchCategoriesRequest;
import com.td.application.categories.SearchCategoriesUseCase;
import com.td.application.categories.UpdateCategoryRequest;
import com.td.application.categories.UpdateCategoryUseCase;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Validated
@Tag(name = "Categories", description = "Quản lý danh mục phân cấp")
public class CategoriesController extends BaseController {

    private final CreateCategoryUseCase     createCategoryUseCase;
    private final UpdateCategoryUseCase     updateCategoryUseCase;
    private final DeleteCategoryUseCase     deleteCategoryUseCase;
    private final GetCategoryUseCase        getCategoryUseCase;
    private final SearchCategoriesUseCase   searchCategoriesUseCase;
    private final CategoryCacheService      categoryCacheService;

    // ── List ─────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách danh mục",
               description = "Hỗ trợ lọc theo keyword, parentId, level, isActive; sắp xếp theo sortOrder mặc định")
    public ResponseEntity<PaginationResponse<CategoryDto>> listCategories(
            @RequestParam(defaultValue = "0")          int     pageNumber,
            @RequestParam(defaultValue = "20")         int     pageSize,
            @RequestParam(defaultValue = "sortOrder")  String  sortBy,
            @RequestParam(defaultValue = "asc")        String  sortDirection,
            @RequestParam(required = false)            String  keyword,
            @RequestParam(required = false)            UUID    parentId,
            @RequestParam(required = false)            Integer level,
            @RequestParam(required = false)            Boolean isActive,
            @Parameter(description = "false = bypass cache, luôn lấy từ DB và làm mới cache")
            @RequestParam(defaultValue = "true")       boolean useCache) {

        var request = new SearchCategoriesRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setParentId(parentId);
        request.setLevel(level);
        request.setIsActive(isActive);

        if (useCache) {
            var cached = categoryCacheService.getList(request);
            if (cached != null) {
                return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(new CachedPaginationResponse<>(cached,
                        categoryCacheService.getCategoryListCacheKey(request)));
            }
        }

        var response = searchCategoriesUseCase.execute(request);
        categoryCacheService.putList(request, response);
        return ResponseEntity.ok().header("X-Cache", "MISS").body(response);
    }

    // ── Search ───────────────────────────────────────────────────

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tìm kiếm danh mục theo bộ lọc")
    public ResponseEntity<PaginationResponse<CategoryDto>> searchCategories(
            @Valid @RequestBody SearchCategoriesRequest request,
            @Parameter(description = "false = bypass cache")
            @RequestParam(defaultValue = "true") boolean useCache) {

        if (useCache) {
            var cached = categoryCacheService.getList(request);
            if (cached != null) {
                return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(new CachedPaginationResponse<>(cached,
                        categoryCacheService.getCategoryListCacheKey(request)));
            }
        }

        var response = searchCategoriesUseCase.execute(request);
        categoryCacheService.putList(request, response);
        return ResponseEntity.ok().header("X-Cache", "MISS").body(response);
    }

    // ── Detail ────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xem chi tiết danh mục")
    public ResponseEntity<Result<CategoryDto>> getCategory(
            @Parameter(description = "Category ID", required = true) @PathVariable UUID id,
            @Parameter(description = "false = bypass cache, luôn lấy từ DB và làm mới cache")
            @RequestParam(defaultValue = "true") boolean useCache) {

        boolean cached = useCache && categoryCacheService.isCachedById(id);
        if (!useCache) {
            categoryCacheService.evict(id);
        }

        var result = getCategoryUseCase.execute(id);
        return ResponseEntity.ok()
            .header("X-Cache", cached ? "HIT" : "MISS")
            .body(cached
                ? new CachedResult<>(result, categoryCacheService.getCategoryByIdCacheKey(id))
                : result);
    }

    // ── Cache stats ───────────────────────────────────────────────

    @GetMapping("/cache/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Thống kê cache danh mục")
    public ResponseEntity<Result<CategoryCacheStatsDto>> getCacheStats() {
        return ok(Result.success(categoryCacheService.getStats()));
    }

    // ── Mutations ─────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo danh mục mới",
               description = "Tự động tính level và fullPath từ parentId.\n" +
                             "code được NFC normalize, viết HOA, khoảng trắng → underscore (_)")
    public ResponseEntity<Result<UUID>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return created(createCategoryUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật danh mục",
               description = "Truyền parentId để thay đổi vị trí trong cây (null = lên gốc).\n" +
                             "Không truyền parentId = giữ nguyên parent hiện tại.\n" +
                             "fullPath và level được tính lại tự động.")
    public ResponseEntity<Result<UUID>> updateCategory(
            @Parameter(description = "Category ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ok(updateCategoryUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm danh mục")
    public ResponseEntity<Result<UUID>> deleteCategory(
            @Parameter(description = "Category ID", required = true) @PathVariable UUID id) {
        return ok(deleteCategoryUseCase.execute(id));
    }
}
