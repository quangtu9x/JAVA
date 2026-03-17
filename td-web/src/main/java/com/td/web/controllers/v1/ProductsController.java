package com.td.web.controllers.v1;

import com.td.application.catalog.products.*;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

/**
 * REST API Controller - Quản lý sản phẩm
 * 
 * Base URL: /api/v1/products
 * 
 * Các endpoint:
 * - POST   /search      : Tìm kiếm sản phẩm với filters (name, brandId, price range, etc.)
 * - GET    /{id}        : Lấy chi tiết sản phẩm theo ID
 * - POST   /            : Tạo sản phẩm mới
 * - PUT    /{id}        : Cập nhật thông tin sản phẩm
 * - DELETE /{id}        : Xóa sản phẩm (soft delete)
 * - POST   /export      : Export danh sách sản phẩm ra Excel
 * 
 * Bảo mật:
 * - Tất cả endpoint yêu cầu role USER (được cấp từ Keycloak)
 * - Token JWT từ Keycloak trong header: Authorization: Bearer {token}
 * 
 * Swagger UI: http://localhost:8080/swagger-ui.html
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Products", description = "Quản lý sản phẩm")
public class ProductsController extends BaseController {

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final SearchProductsUseCase searchProductsUseCase;
    private final ExportProductsUseCase exportProductsUseCase;

    /**
     * API - Tìm kiếm sản phẩm với filters
     * 
     * @param request Request chứa điều kiện tìm kiếm:
     *                - name: Tìm theo tên (LIKE search)
     *                - description: Tìm theo mô tả
     *                - brandId: Lọc theo thương hiệu
     *                - brandName: Tìm theo tên thương hiệu
     *                - minRate, maxRate: Lọc theo khoảng giá
     *                - pageNumber, pageSize: Phân trang (mặc định 0, 10)
     *                - sortBy, sortDirection: Sắp xếp (mặc định createdOn, desc)
     * @return PaginationResponse với danh sách ProductDto
     * 
     * Ví dụ request body:
     * {
     *   "name": "iPhone",
     *   "minRate": 1000,
     *   "maxRate": 5000,
     *   "brandId": "uuid-here",
     *   "pageNumber": 0,
     *   "pageSize": 10,
     *   "sortBy": "rate",
     *   "sortDirection": "desc"
     * }
     */
    @PostMapping("/search")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Tìm kiếm sản phẩm")
    public ResponseEntity<PaginationResponse<ProductDto>> searchProducts(
            @Valid @RequestBody SearchProductsRequest request) {
        var response = searchProductsUseCase.execute(request);
        return ok(response);
    }

    /**
     * API - Lấy chi tiết sản phẩm theo ID
     * 
     * @param id UUID của sản phẩm
     * @return Result<ProductDetailsDto> với thông tin chi tiết sản phẩm
     * 
     * Ví dụ: GET /api/v1/products/123e4567-e89b-12d3-a456-426614174000
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Xem chi tiết sản phẩm")
    public ResponseEntity<Result<ProductDetailsDto>> getProduct(@PathVariable UUID id) {
        var result = getProductUseCase.execute(new GetProductRequest(id));
        return ok(result);
    }

    /**
     * API - Tạo sản phẩm mới
     * 
     * @param request Request chứa thông tin sản phẩm:
     *                - name: Tên sản phẩm (bắt buộc, max 200 ký tự)
     *                - description: Mô tả
     *                - rate: Giá (bắt buộc, > 0)
     *                - brandId: ID thương hiệu (bắt buộc)
     *                - imagePath: Đường dẫn ảnh
     * @return Result<UUID> với ID của sản phẩm vừa tạo
     * 
     * Ví dụ request body:
     * {
     *   "name": "iPhone 15 Pro",
     *   "description": "Flagship phone",
     *   "rate": 29990000,
     *   "brandId": "uuid-of-apple-brand",
     *   "imagePath": "/images/iphone15pro.jpg"
     * }
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Tạo sản phẩm mới")
    public ResponseEntity<Result<UUID>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        var result = createProductUseCase.execute(request);
        return created(result);
    }

    /**
     * API - Cập nhật thông tin sản phẩm
     * 
     * @param id UUID của sản phẩm cần update
     * @param request Request chứa thông tin mới (các field null sẽ không được update)
     * @return Result<UUID> với ID của sản phẩm đã update
     * 
     * Ví dụ: PUT /api/v1/products/123e4567-e89b-12d3-a456-426614174000
     * Body: { "name": "iPhone 15 Pro Max", "rate": 35990000 }
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Cập nhật sản phẩm")
    public ResponseEntity<Result<UUID>> updateProduct(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateProductRequest request) {
        if (!id.equals(request.getId())) {
            return badRequest(Result.<UUID>failure("ID sản phẩm không khớp"));
        }
        var result = updateProductUseCase.execute(request);
        return ok(result);
    }

    /**
     * API - Xóa sản phẩm (soft delete)
     * 
     * @param id UUID của sản phẩm cần xóa
     * @return Result<UUID> với ID của sản phẩm đã xóa
     * 
     * Lưu ý: Đây là soft delete, sản phẩm vẫn còn trong DB nhưng có deletedOn != null
     * 
     * Ví dụ: DELETE /api/v1/products/123e4567-e89b-12d3-a456-426614174000
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Xóa sản phẩm")
    public ResponseEntity<Result<UUID>> deleteProduct(@PathVariable UUID id) {
        var result = deleteProductUseCase.execute(new DeleteProductRequest(id));
        return ok(result);
    }

    /**
     * API - Export danh sách sản phẩm ra file Excel
     * 
     * @param request Request chứa điều kiện filter (giống SearchProductsRequest)
     * @return byte[] - File Excel (.xlsx)
     * 
     * Response headers:
     * - Content-Disposition: attachment; filename=ProductExports.xlsx
     * - Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
     * 
     * Ví dụ: POST /api/v1/products/export
     * Body: { "brandId": "uuid-here", "minRate": 1000 }
     */
    @PostMapping("/export")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Xuất danh sách sản phẩm")
    public ResponseEntity<byte[]> exportProducts(@Valid @RequestBody ExportProductsRequest request) {
        var result = exportProductsUseCase.execute(request);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=ProductExports.xlsx")
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(result);
    }
}