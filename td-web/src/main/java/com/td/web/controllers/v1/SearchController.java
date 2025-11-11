package com.td.web.controllers.v1;

import com.td.application.search.*;
import com.td.domain.search.BrandDocument;
import com.td.domain.search.ProductDocument;
import com.td.infrastructure.search.service.SearchService;
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
@Tag(name = "Search", description = "Search and autocomplete operations using Elasticsearch")
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
    @Operation(summary = "Advanced product search", 
               description = "Search products with multiple filters, sorting, and pagination using Elasticsearch")
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
    @Operation(summary = "Quick product search", 
               description = "Simple product search by query string")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> searchProducts(
            @Parameter(description = "Search query") @RequestParam(required = false) String q,
            @Parameter(description = "Brand IDs") @RequestParam(required = false) List<String> brandIds,
            @Parameter(description = "Categories") @RequestParam(required = false) List<String> categories,
            @Parameter(description = "Minimum price") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price") @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Minimum rating") @RequestParam(required = false) Float minRating,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "asc") String sortDirection) {
        
        Page<ProductDocument> results = searchService.searchProducts(
                q, brandIds, categories, minPrice, maxPrice, minRating,
                page, size, sortBy, sortDirection);
        
        return ok(results);
    }

    /**
     * Search Brands
     */
    @GetMapping("/brands")
    @Operation(summary = "Search brands", 
               description = "Search brands with filters and pagination")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<BrandDocument>> searchBrands(
            @Parameter(description = "Search query") @RequestParam(required = false) String q,
            @Parameter(description = "Active status filter") @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "Minimum product count") @RequestParam(required = false) Integer minProductCount,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "asc") String sortDirection) {
        
        Page<BrandDocument> results = searchService.searchBrands(
                q, isActive, minProductCount, page, size, sortBy, sortDirection);
        
        return ok(results);
    }

    /**
     * Global Search (Products + Brands)
     */
    @GetMapping("/global")
    @Operation(summary = "Global search", 
               description = "Search across products and brands simultaneously")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<SearchService.GlobalSearchResult> globalSearch(
            @Parameter(description = "Search query", required = true) @RequestParam String q,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        
        SearchService.GlobalSearchResult results = searchService.globalSearch(q, page, size);
        return ok(results);
    }

    /**
     * Autocomplete Suggestions
     */
    @GetMapping("/suggestions")
    @Operation(summary = "Get search suggestions", 
               description = "Get autocomplete suggestions for search queries")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<SearchSuggestionsResponse> getSuggestions(
            @Parameter(description = "Search prefix", required = true) @RequestParam String q,
            @Parameter(description = "Suggestion type") @RequestParam(defaultValue = "ALL") SearchSuggestionsRequest.SuggestionType type,
            @Parameter(description = "Maximum suggestions") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Include brands") @RequestParam(defaultValue = "true") boolean includeBrands,
            @Parameter(description = "Include products") @RequestParam(defaultValue = "true") boolean includeProducts) {
        
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
    @Operation(summary = "Product autocomplete", 
               description = "Get product name autocomplete suggestions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> getProductAutocomplete(
            @Parameter(description = "Search prefix", required = true) @RequestParam String q,
            @Parameter(description = "Maximum suggestions") @RequestParam(defaultValue = "10") int limit) {
        
        Page<ProductDocument> suggestions = searchService.getProductSuggestions(q, limit);
        return ok(suggestions);
    }

    /**
     * Brand Autocomplete
     */
    @GetMapping("/autocomplete/brands")
    @Operation(summary = "Brand autocomplete", 
               description = "Get brand name autocomplete suggestions")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<BrandDocument>> getBrandAutocomplete(
            @Parameter(description = "Search prefix", required = true) @RequestParam String q,
            @Parameter(description = "Maximum suggestions") @RequestParam(defaultValue = "10") int limit) {
        
        Page<BrandDocument> suggestions = searchService.getBrandSuggestions(q, limit);
        return ok(suggestions);
    }

    /**
     * Similar Products
     */
    @GetMapping("/products/{productId}/similar")
    @Operation(summary = "Get similar products", 
               description = "Find products similar to the given product")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> getSimilarProducts(
            @Parameter(description = "Product ID") @PathVariable String productId,
            @Parameter(description = "Product name") @RequestParam String productName,
            @Parameter(description = "Brand name") @RequestParam String brandName,
            @Parameter(description = "Maximum results") @RequestParam(defaultValue = "10") int limit) {
        
        Page<ProductDocument> similarProducts = searchService.getSimilarProducts(
                productId, productName, brandName, limit);
        
        return ok(similarProducts);
    }

    /**
     * Popular Products
     */
    @GetMapping("/products/popular")
    @Operation(summary = "Get popular products", 
               description = "Get popular products based on views and orders")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> getPopularProducts(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        
        Page<ProductDocument> popularProducts = searchService.getPopularProducts(page, size);
        return ok(popularProducts);
    }

    /**
     * Top Rated Products
     */
    @GetMapping("/products/top-rated")
    @Operation(summary = "Get top rated products", 
               description = "Get highest rated products")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PRODUCT_MANAGER')")
    public ResponseEntity<Page<ProductDocument>> getTopRatedProducts(
            @Parameter(description = "Minimum rating") @RequestParam(defaultValue = "4.0") Float minRating,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        
        Page<ProductDocument> topRatedProducts = searchService.getTopRatedProducts(minRating, page, size);
        return ok(topRatedProducts);
    }

    /**
     * Search Analytics
     */
    @GetMapping("/analytics")
    @Operation(summary = "Get search analytics", 
               description = "Get aggregation data for search results (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SearchService.SearchAnalytics> getSearchAnalytics(
            @Parameter(description = "Search query") @RequestParam(required = false) String q,
            @Parameter(description = "Brand IDs") @RequestParam(required = false) List<String> brandIds,
            @Parameter(description = "Categories") @RequestParam(required = false) List<String> categories) {
        
        SearchService.SearchAnalytics analytics = searchService.getSearchAnalytics(q, brandIds, categories);
        return ok(analytics);
    }
}