package com.td.application.search;

import com.td.application.common.cqrs.UseCase;
import com.td.domain.search.ProductDocument;
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
        logger.info("Bắt đầu tìm kiếm sản phẩm nâng cao: {}", request);

        // Kiểm tra dữ liệu đầu vào
        validateRequest(request);

        // Thực hiện tìm kiếm
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

        logger.info("Tìm kiếm sản phẩm nâng cao hoàn thành. Tìm thấy {} kết quả (Tổng: {})", 
                   results.getNumberOfElements(), results.getTotalElements());

        return results;
    }

    private void validateRequest(AdvancedSearchProductsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu tìm kiếm không được null");
        }

        // Kiểm tra khoảng giá
        if (request.getMinPrice() != null && request.getMaxPrice() != null) {
            if (request.getMinPrice().compareTo(request.getMaxPrice()) > 0) {
                throw new IllegalArgumentException("Giá nhỏ nhất không được lớn hơn giá lớn nhất");
            }
        }

        // Kiểm tra đánh giá
        if (request.getMinRating() != null) {
            if (request.getMinRating() < 0 || request.getMinRating() > 5) {
                throw new IllegalArgumentException("Đánh giá phải trong khoảng 0 đến 5");
            }
        }

        // Kiểm tra phân trang
        if (request.getPage() < 0) {
            request.setPage(0);
        }

        if (request.getSize() <= 0) {
            request.setSize(20);
        } else if (request.getSize() > 100) {
            request.setSize(100);
        }

        // Kiểm tra chiều sắp xếp
        if (request.getSortDirection() != null) {
            String sortDir = request.getSortDirection().toLowerCase();
            if (!"asc".equals(sortDir) && !"desc".equals(sortDir)) {
                request.setSortDirection("asc");
            }
        }

        logger.debug("Kiểm tra yêu cầu tìm kiếm thành công");
    }
}
