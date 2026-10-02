package com.td.web.controllers.v1;

import com.td.application.categories.CategoryCacheService;
import com.td.application.categories.CategoryCacheStatsDto;
import com.td.application.categories.CategoryDto;
import com.td.application.categories.CategoryFileDto;
import com.td.application.categories.CreateCategoryRequest;
import com.td.application.categories.CreateCategoryUseCase;
import com.td.application.categories.DeleteCategoryFileRequest;
import com.td.application.categories.DeleteCategoryFileUseCase;
import com.td.application.categories.DeleteCategoryUseCase;
import com.td.application.categories.DownloadCategoryFileRequest;
import com.td.application.categories.GetCategoryFileUseCase;
import com.td.application.categories.GetCategoryUseCase;
import com.td.application.categories.ListCategoryFilesUseCase;
import com.td.application.categories.SearchCategoriesRequest;
import com.td.application.categories.SearchCategoriesUseCase;
import com.td.application.categories.UpdateCategoryRequest;
import com.td.application.categories.UpdateCategoryUseCase;
import com.td.application.categories.UploadCategoryFileRequest;
import com.td.application.categories.UploadCategoryFileUseCase;
import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.CachedPaginationResponse;
import com.td.application.common.models.CachedResult;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.infrastructure.config.MinioService;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
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

    // BL-118: tệp đính kèm Category dùng chung (Banner/Footer/Hướng dẫn sử dụng).
    private final UploadCategoryFileUseCase uploadCategoryFileUseCase;
    private final ListCategoryFilesUseCase  listCategoryFilesUseCase;
    private final GetCategoryFileUseCase    getCategoryFileUseCase;
    private final DeleteCategoryFileUseCase deleteCategoryFileUseCase;
    private final MinioService              minioService;

    // ── List ─────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách danh mục",
               description = "Hỗ trợ lọc theo keyword, parentId, level, isActive; sắp xếp theo sortOrder mặc định")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> listCategories(
            @RequestParam(defaultValue = "0")          int     pageNumber,
            @RequestParam(defaultValue = "20")         int     pageSize,
            @RequestParam(defaultValue = "sortOrder")  String  sortBy,
            @RequestParam(defaultValue = "asc")        String  sortDirection,
            @RequestParam(required = false)            String  keyword,
            @RequestParam(required = false)            UUID    parentId,
            @RequestParam(required = false)            Integer level,
            @RequestParam(required = false)            Boolean isActive,
            @Parameter(description = "Lọc theo 1 hoặc nhiều form cùng lúc, vd "
                + "?forms=external_unit_level&forms=external_unit")
            @RequestParam(required = false)            List<String> forms,
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
        request.setForms(forms);

        if (useCache) {
            var cached = categoryCacheService.getList(request);
            if (cached != null) {
                return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(toApiResponse(new CachedPaginationResponse<>(cached,
                        categoryCacheService.getCategoryListCacheKey(request))));
            }
        }

        var response = searchCategoriesUseCase.execute(request);
        categoryCacheService.putList(request, response);
        return ResponseEntity.ok().header("X-Cache", "MISS").body(toApiResponse(response));
    }

    // ── Search ───────────────────────────────────────────────────

    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tìm kiếm danh mục theo bộ lọc")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> searchCategories(
            @Valid @RequestBody SearchCategoriesRequest request,
            @Parameter(description = "false = bypass cache")
            @RequestParam(defaultValue = "true") boolean useCache) {

        if (useCache) {
            var cached = categoryCacheService.getList(request);
            if (cached != null) {
                return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(toApiResponse(new CachedPaginationResponse<>(cached,
                        categoryCacheService.getCategoryListCacheKey(request))));
            }
        }

        var response = searchCategoriesUseCase.execute(request);
        categoryCacheService.putList(request, response);
        return ResponseEntity.ok().header("X-Cache", "MISS").body(toApiResponse(response));
    }

    // ── Detail ────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xem chi tiết danh mục")
    public ResponseEntity<ApiResponse<CategoryDto>> getCategory(
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
            .body(toApiResponse(cached
                ? new CachedResult<>(result, categoryCacheService.getCategoryByIdCacheKey(id))
                : result));
    }

    // ── Cache stats ───────────────────────────────────────────────

    @GetMapping("/cache/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Thống kê cache danh mục")
    public ResponseEntity<ApiResponse<CategoryCacheStatsDto>> getCacheStats() {
        return ok(Result.success(categoryCacheService.getStats()));
    }

    // ── Mutations ─────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tạo danh mục mới",
               description = "Tự động tính level và fullPath từ parentId.\n" +
                             "code được NFC normalize, viết HOA, khoảng trắng → underscore (_)")
    public ResponseEntity<ApiResponse<UUID>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return created(createCategoryUseCase.execute(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Cập nhật danh mục",
               description = "Truyền parentId để thay đổi vị trí trong cây (null = lên gốc).\n" +
                             "Không truyền parentId = giữ nguyên parent hiện tại.\n" +
                             "fullPath và level được tính lại tự động.")
    public ResponseEntity<ApiResponse<UUID>> updateCategory(
            @Parameter(description = "Category ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ok(updateCategoryUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa mềm danh mục")
    public ResponseEntity<ApiResponse<UUID>> deleteCategory(
            @Parameter(description = "Category ID", required = true) @PathVariable UUID id) {
        return ok(deleteCategoryUseCase.execute(id));
    }

    // ── Tệp đính kèm (BL-118 — Banner/Footer/Hướng dẫn sử dụng) ─────

    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tải lên tệp đính kèm cho danh mục",
               description = "Dùng cho Banner/Footer/Hướng dẫn sử dụng (danh mục dùng chung)")
    public ResponseEntity<ApiResponse<UUID>> uploadCategoryFile(
            @Parameter(description = "Category ID", required = true) @PathVariable UUID id,
            @Parameter(description = "Tệp cần tải lên", required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Mô tả tệp") @RequestParam(value = "description", required = false) String description) {
        // try-with-resources: đóng stream sau khi upload xong — stream còn mở giữ khoá tệp tạm multipart
        // trên Windows, Tomcat không xoá được ("Failed to deleted temporary file used for part [file]").
        try (java.io.InputStream fileStream = file.getInputStream()) {
            var request = UploadCategoryFileRequest.builder()
                    .categoryId(id)
                    .fileContent(fileStream)
                    .fileName(file.getOriginalFilename())
                    .mimeType(file.getContentType())
                    .fileSize(file.getSize())
                    .description(description)
                    .build();
            return created(uploadCategoryFileUseCase.execute(request));
        } catch (Exception e) {
            return badRequest(Result.failure("Lỗi xử lý tệp tin: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Danh sách tệp đính kèm của danh mục")
    public ResponseEntity<ApiResponse<List<CategoryFileDto>>> listCategoryFiles(
            @Parameter(description = "Category ID", required = true) @PathVariable UUID id) {
        PaginationResponse<CategoryFileDto> response = listCategoryFilesUseCase.execute(id);
        return ok(response);
    }

    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Xóa tệp đính kèm khỏi danh mục",
               description = "Cùng tier quyền với xóa danh mục (dữ liệu dùng chung — ảnh hưởng mọi đơn vị)")
    public ResponseEntity<ApiResponse<UUID>> deleteCategoryFile(
            @Parameter(description = "Category ID", required = true) @PathVariable UUID id,
            @Parameter(description = "File ID", required = true) @PathVariable UUID fileId,
            @Parameter(description = "Lý do xóa") @RequestParam(value = "reason", required = false) String reason) {
        var request = DeleteCategoryFileRequest.builder()
                .categoryId(id)
                .fileId(fileId)
                .reason(reason)
                .build();
        return ok(deleteCategoryFileUseCase.execute(request));
    }

    @GetMapping("/{id}/files/{fileId}/download")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER', 'BRAND_MANAGER')")
    @Operation(summary = "Tải xuống tệp đính kèm của danh mục")
    public ResponseEntity<?> downloadCategoryFile(
            @Parameter(description = "Category ID", required = true) @PathVariable UUID id,
            @Parameter(description = "File ID", required = true) @PathVariable UUID fileId) {
        var request = DownloadCategoryFileRequest.builder()
                .categoryId(id)
                .fileId(fileId)
                .build();
        var fileResult = getCategoryFileUseCase.execute(request);
        if (!fileResult.isSuccess() || fileResult.getData() == null) {
            return badRequest(Result.failure(fileResult.getError() != null ? fileResult.getError() : "File không tồn tại"));
        }

        var fileMeta = fileResult.getData();
        try {
            InputStream inputStream = minioService.getObject(fileMeta.getStoragePath());
            var mediaType = (fileMeta.getMimeType() != null && !fileMeta.getMimeType().isBlank())
                    ? MediaType.parseMediaType(fileMeta.getMimeType())
                    : MediaType.APPLICATION_OCTET_STREAM;
            var fileName = fileMeta.getOriginalFileName() != null ? fileMeta.getOriginalFileName() : fileMeta.getFileName();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentLength(fileMeta.getFileSize() != null ? fileMeta.getFileSize() : -1)
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            return badRequest(Result.failure("Lỗi tải tệp tin: " + e.getMessage()));
        }
    }
}
