package com.td.infrastructure.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.td.domain.search.BrandDocument;
import com.td.domain.search.ProductDocument;
import com.td.infrastructure.search.repository.BrandElasticsearchRepository;
import com.td.infrastructure.search.repository.ProductElasticsearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Search Service
 * 
 * Service thực hiện các operations tìm kiếm nâng cao với Elasticsearch.
 * Cung cấp search, filtering, aggregations, và analytics functions.
 */
@Service
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private final ProductElasticsearchRepository productRepository;
    private final BrandElasticsearchRepository brandRepository;
    private final ElasticsearchClient elasticsearchClient;

    public SearchService(ProductElasticsearchRepository productRepository,
                        BrandElasticsearchRepository brandRepository,
                        ElasticsearchClient elasticsearchClient) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * Advanced Product Search với multiple filters
     */
    public Page<ProductDocument> searchProducts(String query, List<String> brandIds, List<String> categories,
                                              BigDecimal minPrice, BigDecimal maxPrice, Float minRating,
                                              int page, int size, String sortBy, String sortDirection) {
        
        logger.info("Advanced product search - Query: {}, Brands: {}, Categories: {}, Price: {}-{}, Rating: {}", 
                    query, brandIds, categories, minPrice, maxPrice, minRating);

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortBy != null ? sortBy : "name");
        Pageable pageable = PageRequest.of(page, size, sort);

        return productRepository.advancedFilteredSearch(query, brandIds, categories, minPrice, maxPrice, minRating, pageable);
    }

    /**
     * Product Autocomplete Suggestions
     */
    public Page<ProductDocument> getProductSuggestions(String prefix, int limit) {
        logger.info("Getting product suggestions for prefix: {}", prefix);
        
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "name"));
        return productRepository.findAutocompleteByNamePrefix(prefix, pageable);
    }

    /**
     * Brand Search với filters
     */
    public Page<BrandDocument> searchBrands(String query, Boolean isActive, Integer minProductCount, 
                                          int page, int size, String sortBy, String sortDirection) {
        
        logger.info("Brand search - Query: {}, Active: {}, MinProducts: {}", query, isActive, minProductCount);

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortBy != null ? sortBy : "name");
        Pageable pageable = PageRequest.of(page, size, sort);

        return brandRepository.advancedSearch(query, isActive, minProductCount, pageable);
    }

    /**
     * Brand Autocomplete Suggestions
     */
    public Page<BrandDocument> getBrandSuggestions(String prefix, int limit) {
        logger.info("Getting brand suggestions for prefix: {}", prefix);
        
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "name"));
        return brandRepository.findAutocompleteByNamePrefix(prefix, pageable);
    }

    /**
     * Global Search - tìm kiếm cả products và brands
     */
    public GlobalSearchResult globalSearch(String query, int page, int size) {
        logger.info("Global search for query: {}", query);

        Pageable pageable = PageRequest.of(page, size);
        
        Page<ProductDocument> products = productRepository.advancedSearch(query, pageable);
        Page<BrandDocument> brands = brandRepository.searchByNameOrDescription(query, pageable);

        return new GlobalSearchResult(products, brands);
    }

    /**
     * Get Similar Products
     */
    public Page<ProductDocument> getSimilarProducts(String productId, String productName, String brandName, int limit) {
        logger.info("Getting similar products for: {} - {}", productName, brandName);
        
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "rating"));
        return productRepository.findSimilarProducts(productName, brandName, productId, pageable);
    }

    /**
     * Get Popular Products
     */
    public Page<ProductDocument> getPopularProducts(int page, int size) {
        logger.info("Getting popular products");
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderCount", "viewCount"));
        return productRepository.findPopularProducts(pageable);
    }

    /**
     * Get Top Rated Products
     */
    public Page<ProductDocument> getTopRatedProducts(Float minRating, int page, int size) {
        logger.info("Getting top rated products with min rating: {}", minRating);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "rating"));
        return productRepository.findTopRatedProducts(minRating, pageable);
    }

    /**
     * Search Analytics - lấy aggregations cho search results
     */
    public SearchAnalytics getSearchAnalytics(String query, List<String> brandIds, List<String> categories) {
        try {
            logger.info("Getting search analytics for query: {}", query);

            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index("products")
                .query(buildProductQuery(query, brandIds, categories))
                .size(0) // Chỉ lấy aggregations, không lấy documents
                .aggregations("brands", Aggregation.of(a -> a
                    .terms(t -> t.field("brandName").size(20))))
                .aggregations("categories", Aggregation.of(a -> a
                    .terms(t -> t.field("categories").size(20))))
                .aggregations("priceRanges", Aggregation.of(a -> a
                    .range(r -> r.field("price")
                        .ranges(range -> range.to("50"))
                        .ranges(range -> range.from("50").to("100"))
                        .ranges(range -> range.from("100").to("500"))
                        .ranges(range -> range.from("500")))))
                .aggregations("avgPrice", Aggregation.of(a -> a
                    .avg(avg -> avg.field("price"))))
                .aggregations("avgRating", Aggregation.of(a -> a
                    .avg(avg -> avg.field("rating"))))
            );

            SearchResponse<ProductDocument> response = elasticsearchClient.search(searchRequest, ProductDocument.class);
            
            return buildSearchAnalytics(response.aggregations());

        } catch (Exception e) {
            logger.error("Error getting search analytics", e);
            return new SearchAnalytics();
        }
    }

    /**
     * Build Elasticsearch query cho products
     */
    private Query buildProductQuery(String query, List<String> brandIds, List<String> categories) {
        return Query.of(q -> q.bool(b -> {
            // Must conditions
            if (query != null && !query.trim().isEmpty()) {
                b.must(Query.of(mq -> mq.multiMatch(mm -> mm
                    .query(query)
                    .fields("name^4", "description^2", "brandName^3")
                    .fuzziness("AUTO")
                )));
            }
            
            // Filters
            b.filter(Query.of(f -> f.term(t -> t.field("isActive").value(true))));
            
            if (brandIds != null && !brandIds.isEmpty()) {
                b.filter(Query.of(f -> f.terms(terms -> terms.field("brandId").terms(
                    brandIds.stream().map(id -> co.elastic.clients.elasticsearch._types.FieldValue.of(id))
                           .collect(Collectors.toList())))));
            }
            
            if (categories != null && !categories.isEmpty()) {
                b.filter(Query.of(f -> f.terms(terms -> terms.field("categories").terms(
                    categories.stream().map(cat -> co.elastic.clients.elasticsearch._types.FieldValue.of(cat))
                             .collect(Collectors.toList())))));
            }
            
            return b;
        }));
    }

    /**
     * Build SearchAnalytics từ Elasticsearch aggregations
     */
    private SearchAnalytics buildSearchAnalytics(Map<String, Aggregate> aggregations) {
        SearchAnalytics analytics = new SearchAnalytics();
        
        // Brand aggregation
        Aggregate brandsAgg = aggregations.get("brands");
        if (brandsAgg != null && brandsAgg.isStringTerms()) {
            StringTermsAggregate brandsTerms = brandsAgg.stringTerms();
            Map<String, Long> brandCounts = brandsTerms.buckets().array().stream()
                .collect(Collectors.toMap(
                    StringTermsBucket::key,
                    StringTermsBucket::docCount
                ));
            analytics.setBrandCounts(brandCounts);
        }
        
        // Categories aggregation
        Aggregate categoriesAgg = aggregations.get("categories");
        if (categoriesAgg != null && categoriesAgg.isStringTerms()) {
            StringTermsAggregate categoriesTerms = categoriesAgg.stringTerms();
            Map<String, Long> categoryCounts = categoriesTerms.buckets().array().stream()
                .collect(Collectors.toMap(
                    StringTermsBucket::key,
                    StringTermsBucket::docCount
                ));
            analytics.setCategoryCounts(categoryCounts);
        }
        
        // Average price
        Aggregate avgPriceAgg = aggregations.get("avgPrice");
        if (avgPriceAgg != null && avgPriceAgg.isAvg()) {
            analytics.setAveragePrice(avgPriceAgg.avg().value());
        }
        
        // Average rating
        Aggregate avgRatingAgg = aggregations.get("avgRating");
        if (avgRatingAgg != null && avgRatingAgg.isAvg()) {
            analytics.setAverageRating(avgRatingAgg.avg().value());
        }

        return analytics;
    }

    /**
     * Global Search Result container
     */
    public static class GlobalSearchResult {
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

    /**
     * Search Analytics result container
     */
    public static class SearchAnalytics {
        private Map<String, Long> brandCounts = new HashMap<>();
        private Map<String, Long> categoryCounts = new HashMap<>();
        private Map<String, Long> priceRanges = new HashMap<>();
        private Double averagePrice = 0.0;
        private Double averageRating = 0.0;

        // Getters and Setters
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