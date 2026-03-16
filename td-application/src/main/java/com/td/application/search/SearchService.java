package com.td.application.search;

import com.td.domain.search.BrandDocument;
import com.td.domain.search.ProductDocument;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface SearchService {

    Page<ProductDocument> searchProducts(String query, List<String> brandIds, List<String> categories,
                                         BigDecimal minPrice, BigDecimal maxPrice, Float minRating,
                                         int page, int size, String sortBy, String sortDirection);

    Page<BrandDocument> searchBrands(String query, Boolean isActive, Integer minProductCount,
                                     int page, int size, String sortBy, String sortDirection);

    Page<ProductDocument> getProductSuggestions(String prefix, int limit);

    Page<BrandDocument> getBrandSuggestions(String prefix, int limit);

    GlobalSearchResult globalSearch(String query, int page, int size);

    Page<ProductDocument> getSimilarProducts(String productId, String productName, String brandName, int limit);

    Page<ProductDocument> getPopularProducts(int page, int size);

    Page<ProductDocument> getTopRatedProducts(Float minRating, int page, int size);

    SearchAnalytics getSearchAnalytics(String query, List<String> brandIds, List<String> categories);

    class GlobalSearchResult {
        private final Page<ProductDocument> products;
        private final Page<BrandDocument> brands;

        public GlobalSearchResult(Page<ProductDocument> products, Page<BrandDocument> brands) {
            this.products = products;
            this.brands = brands;
        }

        public Page<ProductDocument> getProducts() {
            return products;
        }

        public Page<BrandDocument> getBrands() {
            return brands;
        }

        public long getTotalResults() {
            return products.getTotalElements() + brands.getTotalElements();
        }
    }

    class SearchAnalytics {
        private Map<String, Long> brandCounts = new HashMap<>();
        private Map<String, Long> categoryCounts = new HashMap<>();
        private Map<String, Long> priceRanges = new HashMap<>();
        private Double averagePrice = 0.0;
        private Double averageRating = 0.0;

        public Map<String, Long> getBrandCounts() {
            return brandCounts;
        }

        public void setBrandCounts(Map<String, Long> brandCounts) {
            this.brandCounts = brandCounts;
        }

        public Map<String, Long> getCategoryCounts() {
            return categoryCounts;
        }

        public void setCategoryCounts(Map<String, Long> categoryCounts) {
            this.categoryCounts = categoryCounts;
        }

        public Map<String, Long> getPriceRanges() {
            return priceRanges;
        }

        public void setPriceRanges(Map<String, Long> priceRanges) {
            this.priceRanges = priceRanges;
        }

        public Double getAveragePrice() {
            return averagePrice;
        }

        public void setAveragePrice(Double averagePrice) {
            this.averagePrice = averagePrice;
        }

        public Double getAverageRating() {
            return averageRating;
        }

        public void setAverageRating(Double averageRating) {
            this.averageRating = averageRating;
        }
    }
}