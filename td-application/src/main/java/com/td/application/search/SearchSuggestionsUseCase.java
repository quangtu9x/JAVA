package com.td.application.search;

import com.td.application.common.cqrs.UseCase;
import com.td.domain.search.BrandDocument;
import com.td.domain.search.ProductDocument;
import com.td.infrastructure.search.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Search Suggestions Use Case
 * 
 * Use case cung cấp autocomplete và search suggestions cho users.
 * Hỗ trợ cả product và brand suggestions với configurable options.
 */
@Component
public class SearchSuggestionsUseCase implements UseCase<SearchSuggestionsRequest, SearchSuggestionsResponse> {

    private static final Logger logger = LoggerFactory.getLogger(SearchSuggestionsUseCase.class);

    private final SearchService searchService;

    public SearchSuggestionsUseCase(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public SearchSuggestionsResponse execute(SearchSuggestionsRequest request) {
        long startTime = System.currentTimeMillis();
        
        logger.info("Executing search suggestions: {}", request);

        // Validate request
        validateRequest(request);

        SearchSuggestionsResponse response = new SearchSuggestionsResponse(request.getQuery());

        try {
            // Get suggestions based on type
            switch (request.getType()) {
                case PRODUCTS -> getProductSuggestions(request, response);
                case BRANDS -> getBrandSuggestions(request, response);
                case AUTOCOMPLETE -> getAutocompleteSuggestions(request, response);
                case ALL -> getAllSuggestions(request, response);
            }

            long executionTime = System.currentTimeMillis() - startTime;
            response.setExecutionTime(executionTime);

            logger.info("Search suggestions completed. Found {} suggestions in {}ms", 
                       response.getTotalSuggestions(), executionTime);

            return response;

        } catch (Exception e) {
            logger.error("Error getting search suggestions for query: {}", request.getQuery(), e);
            throw new RuntimeException("Failed to get search suggestions", e);
        }
    }

    /**
     * Get product suggestions only
     */
    private void getProductSuggestions(SearchSuggestionsRequest request, SearchSuggestionsResponse response) {
        Page<ProductDocument> products = searchService.getProductSuggestions(request.getQuery(), request.getLimit());
        
        for (ProductDocument product : products.getContent()) {
            response.addProductSuggestion(product);
        }
    }

    /**
     * Get brand suggestions only
     */
    private void getBrandSuggestions(SearchSuggestionsRequest request, SearchSuggestionsResponse response) {
        Page<BrandDocument> brands = searchService.getBrandSuggestions(request.getQuery(), request.getLimit());
        
        for (BrandDocument brand : brands.getContent()) {
            response.addBrandSuggestion(brand);
        }
    }

    /**
     * Get autocomplete suggestions (mixed products and brands)
     */
    private void getAutocompleteSuggestions(SearchSuggestionsRequest request, SearchSuggestionsResponse response) {
        int halfLimit = request.getLimit() / 2;
        int productLimit = request.isIncludeProducts() ? halfLimit : 0;
        int brandLimit = request.isIncludeBrands() ? halfLimit : 0;

        // Adjust limits if one type is disabled
        if (!request.isIncludeProducts()) {
            brandLimit = request.getLimit();
        } else if (!request.isIncludeBrands()) {
            productLimit = request.getLimit();
        }

        // Get product suggestions
        if (productLimit > 0) {
            Page<ProductDocument> products = searchService.getProductSuggestions(request.getQuery(), productLimit);
            for (ProductDocument product : products.getContent()) {
                response.addProductSuggestion(product);
            }
        }

        // Get brand suggestions
        if (brandLimit > 0) {
            Page<BrandDocument> brands = searchService.getBrandSuggestions(request.getQuery(), brandLimit);
            for (BrandDocument brand : brands.getContent()) {
                response.addBrandSuggestion(brand);
            }
        }
    }

    /**
     * Get all suggestions (comprehensive search)
     */
    private void getAllSuggestions(SearchSuggestionsRequest request, SearchSuggestionsResponse response) {
        // First try autocomplete approach
        getAutocompleteSuggestions(request, response);

        // If we don't have enough results, try broader search
        if (response.getTotalSuggestions() < request.getLimit()) {
            int remainingLimit = request.getLimit() - response.getTotalSuggestions();
            
            // Try global search for more results
            SearchService.GlobalSearchResult globalResult = searchService.globalSearch(
                request.getQuery(), 0, remainingLimit);

            // Add additional products
            for (ProductDocument product : globalResult.getProducts().getContent()) {
                if (response.getTotalSuggestions() >= request.getLimit()) break;
                
                // Check if already added
                boolean alreadyExists = response.getProducts().stream()
                    .anyMatch(p -> p.getId().equals(product.getId()));
                
                if (!alreadyExists) {
                    response.addProductSuggestion(product);
                }
            }

            // Add additional brands
            for (BrandDocument brand : globalResult.getBrands().getContent()) {
                if (response.getTotalSuggestions() >= request.getLimit()) break;
                
                // Check if already added
                boolean alreadyExists = response.getBrands().stream()
                    .anyMatch(b -> b.getId().equals(brand.getId()));
                
                if (!alreadyExists) {
                    response.addBrandSuggestion(brand);
                }
            }
        }
    }

    /**
     * Validate suggestions request
     */
    private void validateRequest(SearchSuggestionsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Search suggestions request cannot be null");
        }

        if (!StringUtils.hasText(request.getQuery())) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }

        String query = request.getQuery().trim();
        if (query.length() < request.getMinLength()) {
            throw new IllegalArgumentException(
                String.format("Query must be at least %d characters long", request.getMinLength()));
        }

        // Set cleaned query back
        request.setQuery(query);

        // Validate limits
        if (request.getLimit() <= 0) {
            request.setLimit(10);
        } else if (request.getLimit() > 50) {
            request.setLimit(50);
        }

        // Validate type settings
        if (request.getType() == null) {
            request.setType(SearchSuggestionsRequest.SuggestionType.ALL);
        }

        // Ensure at least one type is enabled
        if (!request.isIncludeProducts() && !request.isIncludeBrands()) {
            request.setIncludeProducts(true);
            request.setIncludeBrands(true);
        }

        logger.debug("Search suggestions request validated successfully");
    }
}