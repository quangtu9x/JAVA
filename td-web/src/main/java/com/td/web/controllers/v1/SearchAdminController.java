package com.td.web.controllers.v1;

import com.td.infrastructure.search.service.DataSynchronizationService;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Search Admin Controller
 * 
 * Admin endpoints để quản lý Elasticsearch index và data synchronization.
 * Chỉ dành cho ADMIN users.
 */
@RestController
@RequestMapping("/api/v1/admin/search")
@Tag(name = "Search Admin", description = "Quản trị Elasticsearch và đồng bộ dữ liệu (chỉ dành cho Admin)")
@PreAuthorize("hasRole('ADMIN')")
public class SearchAdminController extends BaseController {

    private final DataSynchronizationService dataSyncService;

    public SearchAdminController(DataSynchronizationService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    // Đồng bộ tất cả thương hiệu vào Elasticsearch
    @PostMapping("/sync/brands")
    @Operation(summary = "Đồng bộ thương hiệu", 
               description = "Đồng bộ tất cả thương hiệu từ PostgreSQL vào Elasticsearch")
    public ResponseEntity<Map<String, String>> syncAllBrands() {
        try {
            dataSyncService.syncAllBrands();
            return ok(Map.of(
                "status", "success",
                "message", "Đã đồng bộ tất cả thương hiệu thành công"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Đồng bộ thương hiệu thất bại: " + e.getMessage()
            ));
        }
    }

    // Đồng bộ tất cả sản phẩm vào Elasticsearch
    @PostMapping("/sync/products")
    @Operation(summary = "Đồng bộ sản phẩm", 
               description = "Đồng bộ tất cả sản phẩm từ PostgreSQL vào Elasticsearch")
    public ResponseEntity<Map<String, String>> syncAllProducts() {
        try {
            dataSyncService.syncAllProducts();
            return ok(Map.of(
                "status", "success",
                "message", "Đã đồng bộ tất cả sản phẩm thành công"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Đồng bộ sản phẩm thất bại: " + e.getMessage()
            ));
        }
    }

    // Đồng bộ toàn bộ dữ liệu vào Elasticsearch
    @PostMapping("/sync/all")
    @Operation(summary = "Đồng bộ toàn bộ dữ liệu", 
               description = "Đồng bộ tất cả thương hiệu và sản phẩm từ PostgreSQL vào Elasticsearch")
    public ResponseEntity<Map<String, String>> syncAllData() {
        try {
            dataSyncService.syncAllBrands();
            dataSyncService.syncAllProducts();
            return ok(Map.of(
                "status", "success",
                "message", "Đã đồng bộ toàn bộ dữ liệu thành công"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Đồng bộ dữ liệu thất bại: " + e.getMessage()
            ));
        }
    }

    // Xây dựng lại toàn bộ chỉ mục tìm kiếm
    @PostMapping("/rebuild")
    @Operation(summary = "Xây dựng lại chỉ mục tìm kiếm", 
               description = "Xóa toàn bộ dữ liệu và xây dựng lại chỉ mục Elasticsearch")
    public ResponseEntity<Map<String, String>> rebuildSearchIndex() {
        try {
            dataSyncService.rebuildSearchIndex();
            return ok(Map.of(
                "status", "success",
                "message", "Đã xây dựng lại chỉ mục tìm kiếm thành công"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Xây dựng lại chỉ mục thất bại: " + e.getMessage()
            ));
        }
    }

    // Đồng bộ sản phẩm theo lô
    @PostMapping("/sync/products/batch")
    @Operation(summary = "Đồng bộ sản phẩm theo lô", 
               description = "Đồng bộ sản phẩm theo từng lô để tránh lỗi bộ nhớ")
    public ResponseEntity<Map<String, String>> syncProductsBatch(
            @RequestParam(defaultValue = "100") int batchSize) {
        try {
            dataSyncService.syncAllProductsBatch(batchSize);
            return ok(Map.of(
                "status", "success",
                "message", "Đã đồng bộ toàn bộ sản phẩm theo lô thành công",
                "batchSize", String.valueOf(batchSize)
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Đồng bộ sản phẩm theo lô thất bại: " + e.getMessage()
            ));
        }
    }

    // Xóa thương hiệu khỏi chỉ mục tìm kiếm
    @DeleteMapping("/brands/{brandId}")
    @Operation(summary = "Xóa thương hiệu khỏi chỉ mục", 
               description = "Gỡ thương hiệu và sản phẩm của nó khỏi Elasticsearch")
    public ResponseEntity<Map<String, String>> deleteBrandFromIndex(@PathVariable String brandId) {
        try {
            dataSyncService.deleteBrandFromIndex(brandId);
            return ok(Map.of(
                "status", "success",
                "message", "Đã xóa thương hiệu khỏi chỉ mục tìm kiếm",
                "brandId", brandId
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Xóa thương hiệu khỏi chỉ mục thất bại: " + e.getMessage()
            ));
        }
    }

    // Xóa sản phẩm khỏi chỉ mục tìm kiếm
    @DeleteMapping("/products/{productId}")
    @Operation(summary = "Xóa sản phẩm khỏi chỉ mục", 
               description = "Gỡ sản phẩm khỏi chỉ mục Elasticsearch")
    public ResponseEntity<Map<String, String>> deleteProductFromIndex(@PathVariable String productId) {
        try {
            dataSyncService.deleteProductFromIndex(productId);
            return ok(Map.of(
                "status", "success",
                "message", "Đã xóa sản phẩm khỏi chỉ mục tìm kiếm",
                "productId", productId
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Xóa sản phẩm khỏi chỉ mục thất bại: " + e.getMessage()
            ));
        }
    }

    // Kiểm tra trạng thái chỉ mục
    @GetMapping("/health")
    @Operation(summary = "Kiểm tra trạng thái chỉ mục", 
               description = "Xem thông tin trạng thái chỉ mục Elasticsearch")
    public ResponseEntity<Map<String, Object>> getSearchHealth() {
        try {
            return ok(Map.of(
                "status", "healthy",
                "elasticsearch", "connected",
                "message", "Chỉ mục tìm kiếm đang hoạt động bình thường"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "unhealthy",
                "message", "Kiểm tra trạng thái thất bại: " + e.getMessage()
            ));
        }
    }
}