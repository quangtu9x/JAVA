package com.td.application.search;

import com.td.application.common.cqrs.UseCase;
import com.td.domain.search.ProductDocument;
import com.td.infrastructure.search.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Advanced Search Products Use Case
 * 
 * Use case thực hiện tìm kiếm nâng cao cho products với multiple filters,
 * sorting, và pagination sử dụng Elasticsearch.
 */
@Component
public class AdvancedSearchProductsUseCase implements UseCase<AdvancedSearchProductsRequest, Page<ProductDocument>> {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedSearchProductsUseCase.class);

    private final SearchService searchService;

    public AdvancedSearchProductsUseCase(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public Page<ProductDocument> execute(AdvancedSearchProductsRequest request) {
        logger.info("Executing advanced product search: {}", request);

        // Validate request
        validateRequest(request);

        // Perform search
        Page<ProductDocument> results = searchService.searchProducts(
                request.getQuery(),
                request.getBrandIds(),
                request.getCategories(),
                request.getMinPrice(),
                request.getMaxPrice(),
                request.getMinRating(),
                request.getPage(),
                request.getSize(),
                request.getSortBy(),
                request.getSortDirection()
        );

        logger.info("Advanced product search completed. Found {} results (Total: {})", 
                   results.getNumberOfElements(), results.getTotalElements());

        return results;
    }

    /**
     * Validate search request
     */
    private void validateRequest(AdvancedSearchProductsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Search request cannot be null");
        }

        // Validate price range
        if (request.getMinPrice() != null && request.getMaxPrice() != null) {
            if (request.getMinPrice().compareTo(request.getMaxPrice()) > 0) {
                throw new IllegalArgumentException("Min price cannot be greater than max price");
            }
        }

        // Validate rating
        if (request.getMinRating() != null) {
            if (request.getMinRating() < 0 || request.getMinRating() > 5) {
                throw new IllegalArgumentException("Rating must be between 0 and 5");
            }
        }

        // Validate pagination
        if (request.getPage() < 0) {
            request.setPage(0);
        }

        if (request.getSize() <= 0) {
            request.setSize(20);
        } else if (request.getSize() > 100) {
            request.setSize(100);
        }

        // Validate sort direction
        if (request.getSortDirection() != null) {
            String sortDir = request.getSortDirection().toLowerCase();
            if (!"asc".equals(sortDir) && !"desc".equals(sortDir)) {
                request.setSortDirection("asc");
            }
        }

        logger.debug("Search request validated successfully");
    }
}