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

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Products", description = "Product management endpoints")
public class ProductsController extends BaseController {

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final SearchProductsUseCase searchProductsUseCase;
    private final ExportProductsUseCase exportProductsUseCase;

    @PostMapping("/search")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Search products using available filters")
    public ResponseEntity<PaginationResponse<ProductDto>> searchProducts(
            @Valid @RequestBody SearchProductsRequest request) {
        var response = searchProductsUseCase.execute(request);
        return ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get product details")
    public ResponseEntity<Result<ProductDetailsDto>> getProduct(@PathVariable UUID id) {
        var result = getProductUseCase.execute(new GetProductRequest(id));
        return ok(result);
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create a new product")
    public ResponseEntity<Result<UUID>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        var result = createProductUseCase.execute(request);
        return created(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Update a product")
    public ResponseEntity<Result<UUID>> updateProduct(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateProductRequest request) {
        if (!id.equals(request.getId())) {
            return badRequest(Result.<UUID>failure("Product ID mismatch"));
        }
        var result = updateProductUseCase.execute(request);
        return ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Delete a product")
    public ResponseEntity<Result<UUID>> deleteProduct(@PathVariable UUID id) {
        var result = deleteProductUseCase.execute(new DeleteProductRequest(id));
        return ok(result);
    }

    @PostMapping("/export")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Export products")
    public ResponseEntity<byte[]> exportProducts(@Valid @RequestBody ExportProductsRequest request) {
        var result = exportProductsUseCase.execute(request);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=ProductExports.xlsx")
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(result);
    }
}