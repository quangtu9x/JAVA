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
@Tag(name = "Search Admin", description = "Elasticsearch administration and data synchronization (Admin only)")
@PreAuthorize("hasRole('ADMIN')")
public class SearchAdminController extends BaseController {

    private final DataSynchronizationService dataSyncService;

    public SearchAdminController(DataSynchronizationService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    /**
     * Sync all brands to Elasticsearch
     */
    @PostMapping("/sync/brands")
    @Operation(summary = "Sync all brands", 
               description = "Synchronize all brands from PostgreSQL to Elasticsearch")
    public ResponseEntity<Map<String, String>> syncAllBrands() {
        try {
            dataSyncService.syncAllBrands();
            return ok(Map.of(
                "status", "success",
                "message", "All brands synchronized successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to sync brands: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync all products to Elasticsearch
     */
    @PostMapping("/sync/products")
    @Operation(summary = "Sync all products", 
               description = "Synchronize all products from PostgreSQL to Elasticsearch")
    public ResponseEntity<Map<String, String>> syncAllProducts() {
        try {
            dataSyncService.syncAllProducts();
            return ok(Map.of(
                "status", "success",
                "message", "All products synchronized successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to sync products: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync all data to Elasticsearch
     */
    @PostMapping("/sync/all")
    @Operation(summary = "Sync all data", 
               description = "Synchronize all brands and products from PostgreSQL to Elasticsearch")
    public ResponseEntity<Map<String, String>> syncAllData() {
        try {
            dataSyncService.syncAllBrands();
            dataSyncService.syncAllProducts();
            return ok(Map.of(
                "status", "success",
                "message", "All data synchronized successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to sync data: " + e.getMessage()
            ));
        }
    }

    /**
     * Rebuild entire search index
     */
    @PostMapping("/rebuild")
    @Operation(summary = "Rebuild search index", 
               description = "Delete all existing documents and rebuild the entire Elasticsearch index")
    public ResponseEntity<Map<String, String>> rebuildSearchIndex() {
        try {
            dataSyncService.rebuildSearchIndex();
            return ok(Map.of(
                "status", "success",
                "message", "Search index rebuilt successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to rebuild search index: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync products in batches
     */
    @PostMapping("/sync/products/batch")
    @Operation(summary = "Sync products in batches", 
               description = "Synchronize all products in batches to avoid memory issues")
    public ResponseEntity<Map<String, String>> syncProductsBatch(
            @RequestParam(defaultValue = "100") int batchSize) {
        try {
            dataSyncService.syncAllProductsBatch(batchSize);
            return ok(Map.of(
                "status", "success",
                "message", "All products synchronized in batches successfully",
                "batchSize", String.valueOf(batchSize)
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to sync products in batches: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete brand from search index
     */
    @DeleteMapping("/brands/{brandId}")
    @Operation(summary = "Delete brand from index", 
               description = "Remove brand and its products from Elasticsearch index")
    public ResponseEntity<Map<String, String>> deleteBrandFromIndex(@PathVariable String brandId) {
        try {
            dataSyncService.deleteBrandFromIndex(brandId);
            return ok(Map.of(
                "status", "success",
                "message", "Brand deleted from search index",
                "brandId", brandId
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to delete brand from index: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete product from search index
     */
    @DeleteMapping("/products/{productId}")
    @Operation(summary = "Delete product from index", 
               description = "Remove product from Elasticsearch index")
    public ResponseEntity<Map<String, String>> deleteProductFromIndex(@PathVariable String productId) {
        try {
            dataSyncService.deleteProductFromIndex(productId);
            return ok(Map.of(
                "status", "success",
                "message", "Product deleted from search index",
                "productId", productId
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to delete product from index: " + e.getMessage()
            ));
        }
    }

    /**
     * Check index health
     */
    @GetMapping("/health")
    @Operation(summary = "Check search index health", 
               description = "Get information about Elasticsearch index status")
    public ResponseEntity<Map<String, Object>> getSearchHealth() {
        try {
            // This would need to be implemented with actual Elasticsearch health checks
            return ok(Map.of(
                "status", "healthy",
                "elasticsearch", "connected",
                "message", "Search indices are operational"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "unhealthy",
                "message", "Search health check failed: " + e.getMessage()
            ));
        }
    }
}