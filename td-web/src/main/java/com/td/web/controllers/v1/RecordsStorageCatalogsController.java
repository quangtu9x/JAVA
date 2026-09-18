package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.recordsstorage.CreateRecordsStorageCatalogRequest;
import com.td.application.recordsstorage.CreateRecordsStorageCatalogUseCase;
import com.td.application.recordsstorage.DeleteRecordsStorageCatalogUseCase;
import com.td.application.recordsstorage.GetRecordsStorageCatalogUseCase;
import com.td.application.recordsstorage.RecordsStorageCatalogDto;
import com.td.application.recordsstorage.SearchRecordsStorageCatalogsRequest;
import com.td.application.recordsstorage.SearchRecordsStorageCatalogsUseCase;
import com.td.application.recordsstorage.UpdateRecordsStorageCatalogRequest;
import com.td.application.recordsstorage.UpdateRecordsStorageCatalogUseCase;
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

// BL-116: "Danh mục hồ sơ lưu trữ" (phongtailieu/demuchoso/danhmuchosocoquan — 1 loại bản ghi lá
// dùng chung bởi cả 3 thư mục FE, xem RecordsStorageCatalog). Cây phân loại (Phông/Đề mục lớn/Đề
// mục nhỏ) mà bản ghi thuộc về dùng API `/api/v1/private-categories` (tái dùng PrivateCategory,
// form=phongtailieu/demuclon/demucnho — xem PrivateCategoriesController).
@RestController
@RequestMapping("/api/v1/records-storage-catalogs")
@RequiredArgsConstructor
@Validated
@Tag(name = "Records Storage Catalogs", description = "Danh mục hồ sơ lưu trữ theo đơn vị (BL-116)")
public class RecordsStorageCatalogsController extends BaseController {

    private final CreateRecordsStorageCatalogUseCase createRecordsStorageCatalogUseCase;
    private final UpdateRecordsStorageCatalogUseCase updateRecordsStorageCatalogUseCase;
    private final DeleteRecordsStorageCatalogUseCase deleteRecordsStorageCatalogUseCase;
    private final GetRecordsStorageCatalogUseCase getRecordsStorageCatalogUseCase;
    private final SearchRecordsStorageCatalogsUseCase searchRecordsStorageCatalogsUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách danh mục hồ sơ lưu trữ",
               description = "Lọc theo keyword, identifier, selectedNodeId (id node Phông/Đề mục lớn/Đề mục nhỏ), yearInception")
    public ResponseEntity<ApiResponse<List<RecordsStorageCatalogDto>>> listRecordsStorageCatalogs(
            @RequestParam(defaultValue = "0")        int     pageNumber,
            @RequestParam(defaultValue = "20")       int     pageSize,
            @RequestParam(defaultValue = "category") String  sortBy,
            @RequestParam(defaultValue = "asc")      String  sortDirection,
            @RequestParam(required = false)          String  keyword,
            @RequestParam(required = false)          String  identifier,
            @RequestParam(required = false)          UUID    selectedNodeId,
            @RequestParam(required = false)          Integer yearInception) {

        var request = new SearchRecordsStorageCatalogsRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setKeyword(keyword);
        request.setIdentifier(identifier);
        request.setSelectedNodeId(selectedNodeId);
        request.setYearInception(yearInception);

        return ok(searchRecordsStorageCatalogsUseCase.execute(request));
    }

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tìm kiếm danh mục hồ sơ lưu trữ theo bộ lọc (dùng cho export toàn bộ, pageSize lớn)")
    public ResponseEntity<ApiResponse<List<RecordsStorageCatalogDto>>> searchRecordsStorageCatalogs(
            @Valid @RequestBody SearchRecordsStorageCatalogsRequest request) {
        return ok(searchRecordsStorageCatalogsUseCase.execute(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xem chi tiết danh mục hồ sơ lưu trữ")
    public ResponseEntity<ApiResponse<RecordsStorageCatalogDto>> getRecordsStorageCatalog(
            @Parameter(description = "Records storage catalog ID", required = true) @PathVariable UUID id) {
        return ok(getRecordsStorageCatalogUseCase.execute(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo danh mục hồ sơ lưu trữ mới")
    public ResponseEntity<ApiResponse<UUID>> createRecordsStorageCatalog(
            @Valid @RequestBody CreateRecordsStorageCatalogRequest request) {
        return created(createRecordsStorageCatalogUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật danh mục hồ sơ lưu trữ")
    public ResponseEntity<ApiResponse<UUID>> updateRecordsStorageCatalog(
            @Parameter(description = "Records storage catalog ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateRecordsStorageCatalogRequest request) {
        return ok(updateRecordsStorageCatalogUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER', 'USER')")
    @Operation(summary = "Xóa mềm danh mục hồ sơ lưu trữ")
    public ResponseEntity<ApiResponse<UUID>> deleteRecordsStorageCatalog(
            @Parameter(description = "Records storage catalog ID", required = true) @PathVariable UUID id) {
        return ok(deleteRecordsStorageCatalogUseCase.execute(id));
    }
}
