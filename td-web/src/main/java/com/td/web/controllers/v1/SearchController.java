package com.td.web.controllers.v1;

import com.td.application.search.*;
import com.td.domain.search.BrandDocument;
import com.td.domain.search.ProductDocument;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

/**
 * Search Controller
 * 
 * REST endpoints cho Elasticsearch search functionality.
 * Cung cấp advanced search, autocomplete, và analytics features.
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Tim kiem", description = "Cac thao tac tim kiem va goi y bang Elasticsearch")
public class SearchController extends BaseController {

    private final AdvancedSearchProductsUseCase advancedSearchProductsUseCase;
    private final SearchSuggestionsUseCase searchSuggestionsUseCase;
    private final SearchService searchService;

    public SearchController(AdvancedSearchProductsUseCase advancedSearchProductsUseCase,
                          SearchSuggestionsUseCase searchSuggestionsUseCase,
                          SearchService searchService) {
        this.advancedSearchProductsUseCase = advancedSearchProductsUseCase;
        this.searchSuggestionsUseCase = searchSuggestionsUseCase;
        this.searchService = searchService;
    }

    /**
     * Advanced Product Search
     */
    @PostMapping("/products/advanced")
    @Operation(summary = "Tim kiem san pham nang cao", 
               description = "Tim kiem san pham voi nhieu bo loc, sap xep va phan trang bang Elasticsearch")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> advancedSearchProducts(
            @Valid @RequestBody AdvancedSearchProductsRequest request) {
        
        Page<ProductDocument> results = advancedSearchProductsUseCase.execute(request);
        return ok(results);
    }

    /**
     * Quick Product Search
     */
    @GetMapping("/products")
    @Operation(summary = "Tim nhanh san pham", 
               description = "Tim nhanh san pham theo tu khoa")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> searchProducts(
            @Parameter(description = "Tu khoa tim kiem") @RequestParam(required = false) String q,
            @Parameter(description = "Danh sach ID thuong hieu") @RequestParam(required = false) List<String> brandIds,
            @Parameter(description = "Danh muc") @RequestParam(required = false) List<String> categories,
            @Parameter(description = "Gia toi thieu") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Gia toi da") @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Danh gia toi thieu") @RequestParam(required = false) Float minRating,
            @Parameter(description = "So trang") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kich thuoc trang") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sap xep theo truong") @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Huong sap xep") @RequestParam(defaultValue = "asc") String sortDirection) {
        
        Page<ProductDocument> results = searchService.searchProducts(
                q, brandIds, categories, minPrice, maxPrice, minRating,
                page, size, sortBy, sortDirection);
        
        return ok(results);
    }

    /**
     * Search Brands
     */
    @GetMapping("/brands")
    @Operation(summary = "Tim kiem thuong hieu", 
               description = "Tim kiem thuong hieu voi bo loc va phan trang")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<BrandDocument>> searchBrands(
            @Parameter(description = "Tu khoa tim kiem") @RequestParam(required = false) String q,
            @Parameter(description = "Bo loc trang thai kich hoat") @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "So luong san pham toi thieu") @RequestParam(required = false) Integer minProductCount,
            @Parameter(description = "So trang") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kich thuoc trang") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sap xep theo truong") @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Huong sap xep") @RequestParam(defaultValue = "asc") String sortDirection) {
        
        Page<BrandDocument> results = searchService.searchBrands(
                q, isActive, minProductCount, page, size, sortBy, sortDirection);
        
        return ok(results);
    }

    /**
     * Global Search (Products + Brands)
     */
    @GetMapping("/global")
    @Operation(summary = "Tim kiem tong hop", 
               description = "Tim kiem dong thoi tren san pham va thuong hieu")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<SearchService.GlobalSearchResult> globalSearch(
            @Parameter(description = "Tu khoa tim kiem", required = true) @RequestParam String q,
            @Parameter(description = "So trang") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kich thuoc trang") @RequestParam(defaultValue = "20") int size) {
        
        SearchService.GlobalSearchResult results = searchService.globalSearch(q, page, size);
        return ok(results);
    }

    /**
     * Autocomplete Suggestions
     */
    @GetMapping("/suggestions")
    @Operation(summary = "Lay goi y tim kiem", 
               description = "Lay goi y tu dong hoan thanh cho tu khoa tim kiem")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<SearchSuggestionsResponse> getSuggestions(
            @Parameter(description = "Tien to tim kiem", required = true) @RequestParam String q,
            @Parameter(description = "Loai goi y") @RequestParam(defaultValue = "ALL") SearchSuggestionsRequest.SuggestionType type,
            @Parameter(description = "So luong goi y toi da") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Bao gom thuong hieu") @RequestParam(defaultValue = "true") boolean includeBrands,
            @Parameter(description = "Bao gom san pham") @RequestParam(defaultValue = "true") boolean includeProducts) {
        
        SearchSuggestionsRequest request = new SearchSuggestionsRequest(q);
        request.setType(type);
        request.setLimit(limit);
        request.setIncludeBrands(includeBrands);
        request.setIncludeProducts(includeProducts);
        
        SearchSuggestionsResponse suggestions = searchSuggestionsUseCase.execute(request);
        return ok(suggestions);
    }

    /**
     * Product Autocomplete
     */
    @GetMapping("/autocomplete/products")
    @Operation(summary = "Tu dong hoan thanh san pham", 
               description = "Lay goi y tu dong hoan thanh ten san pham")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> getProductAutocomplete(
            @Parameter(description = "Tien to tim kiem", required = true) @RequestParam String q,
            @Parameter(description = "So luong goi y toi da") @RequestParam(defaultValue = "10") int limit) {
        
        Page<ProductDocument> suggestions = searchService.getProductSuggestions(q, limit);
        return ok(suggestions);
    }

    /**
     * Brand Autocomplete
     */
    @GetMapping("/autocomplete/brands")
    @Operation(summary = "Tu dong hoan thanh thuong hieu", 
               description = "Lay goi y tu dong hoan thanh ten thuong hieu")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<BrandDocument>> getBrandAutocomplete(
            @Parameter(description = "Tien to tim kiem", required = true) @RequestParam String q,
            @Parameter(description = "So luong goi y toi da") @RequestParam(defaultValue = "10") int limit) {
        
        Page<BrandDocument> suggestions = searchService.getBrandSuggestions(q, limit);
        return ok(suggestions);
    }

    /**
     * Similar Products
     */
    @GetMapping("/products/{productId}/similar")
    @Operation(summary = "Lay san pham tuong tu", 
               description = "Tim cac san pham tuong tu voi san pham da cho")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> getSimilarProducts(
            @Parameter(description = "ID san pham") @PathVariable String productId,
            @Parameter(description = "Ten san pham") @RequestParam String productName,
            @Parameter(description = "Ten thuong hieu") @RequestParam String brandName,
            @Parameter(description = "So ket qua toi da") @RequestParam(defaultValue = "10") int limit) {
        
        Page<ProductDocument> similarProducts = searchService.getSimilarProducts(
                productId, productName, brandName, limit);
        
        return ok(similarProducts);
    }

    /**
     * Popular Products
     */
    @GetMapping("/products/popular")
    @Operation(summary = "Lay san pham pho bien", 
               description = "Lay san pham pho bien based on views and orders")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> getPopularProducts(
            @Parameter(description = "So trang") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kich thuoc trang") @RequestParam(defaultValue = "20") int size) {
        
        Page<ProductDocument> popularProducts = searchService.getPopularProducts(page, size);
        return ok(popularProducts);
    }

    /**
     * Top Rated Products
     */
    @GetMapping("/products/top-rated")
    @Operation(summary = "Lay san pham danh gia cao", 
               description = "Lay cac san pham co diem danh gia cao nhat")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> getTopRatedProducts(
            @Parameter(description = "Danh gia toi thieu") @RequestParam(defaultValue = "4.0") Float minRating,
            @Parameter(description = "So trang") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Kich thuoc trang") @RequestParam(defaultValue = "20") int size) {
        
        Page<ProductDocument> topRatedProducts = searchService.getTopRatedProducts(minRating, page, size);
        return ok(topRatedProducts);
    }

    /**
     * Search Analytics
     */
    @GetMapping("/analytics")
    @Operation(summary = "Lay thong ke tim kiem", 
               description = "Lay du lieu tong hop cho ket qua tim kiem (chi Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SearchService.SearchAnalytics> getSearchAnalytics(
            @Parameter(description = "Tu khoa tim kiem") @RequestParam(required = false) String q,
            @Parameter(description = "Danh sach ID thuong hieu") @RequestParam(required = false) List<String> brandIds,
            @Parameter(description = "Danh muc") @RequestParam(required = false) List<String> categories) {
        
        SearchService.SearchAnalytics analytics = searchService.getSearchAnalytics(q, brandIds, categories);
        return ok(analytics);
    }
}


