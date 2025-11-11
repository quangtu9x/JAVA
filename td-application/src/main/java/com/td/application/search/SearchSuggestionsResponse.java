package com.td.application.search;

import com.td.domain.search.BrandDocument;
import com.td.domain.search.ProductDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Search Suggestions Response
 * 
 * Response DTO chứa kết quả autocomplete và search suggestions.
 */
public class SearchSuggestionsResponse {

    /**
     * Product suggestions
     */
    private List<ProductSuggestion> products = new ArrayList<>();

    /**
     * Brand suggestions
     */
    private List<BrandSuggestion> brands = new ArrayList<>();

    /**
     * Query được sử dụng
     */
    private String query;

    /**
     * Total number of suggestions
     */
    private int totalSuggestions;

    /**
     * Execution time (ms)
     */
    private long executionTime;

    // Constructors
    public SearchSuggestionsResponse() {}

    public SearchSuggestionsResponse(String query) {
        this.query = query;
    }

    // Getters and Setters
    public List<ProductSuggestion> getProducts() {
        return products;
    }

    public void setProducts(List<ProductSuggestion> products) {
        this.products = products;
        updateTotalSuggestions();
    }

    public List<BrandSuggestion> getBrands() {
        return brands;
    }

    public void setBrands(List<BrandSuggestion> brands) {
        this.brands = brands;
        updateTotalSuggestions();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTotalSuggestions() {
        return totalSuggestions;
    }

    public void setTotalSuggestions(int totalSuggestions) {
        this.totalSuggestions = totalSuggestions;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    // Helper methods
    public void addProductSuggestion(ProductDocument product) {
        if (product != null) {
            this.products.add(new ProductSuggestion(product));
            updateTotalSuggestions();
        }
    }

    public void addBrandSuggestion(BrandDocument brand) {
        if (brand != null) {
            this.brands.add(new BrandSuggestion(brand));
            updateTotalSuggestions();
        }
    }

    private void updateTotalSuggestions() {
        this.totalSuggestions = this.products.size() + this.brands.size();
    }

    /**
     * Product Suggestion DTO
     */
    public static class ProductSuggestion {
        private String id;
        private String name;
        private String slug;
        private String brandName;
        private String price;
        private Float rating;
        private String imageUrl;

        public ProductSuggestion() {}

        public ProductSuggestion(ProductDocument product) {
            this.id = product.getId();
            this.name = product.getName();
            this.slug = product.getSlug();
            this.brandName = product.getBrandName();
            this.price = product.getPrice() != null ? product.getPrice().toString() : null;
            this.rating = product.getRating();
        }

        // Getters and Setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getBrandName() {
            return brandName;
        }

        public void setBrandName(String brandName) {
            this.brandName = brandName;
        }

        public String getPrice() {
            return price;
        }

        public void setPrice(String price) {
            this.price = price;
        }

        public Float getRating() {
            return rating;
        }

        public void setRating(Float rating) {
            this.rating = rating;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }
    }

    /**
     * Brand Suggestion DTO
     */
    public static class BrandSuggestion {
        private String id;
        private String name;
        private String slug;
        private String description;
        private Integer productCount;
        private String logoUrl;

        public BrandSuggestion() {}

        public BrandSuggestion(BrandDocument brand) {
            this.id = brand.getId();
            this.name = brand.getName();
            this.slug = brand.getSlug();
            this.description = brand.getDescription();
            this.productCount = brand.getProductCount();
        }

        // Getters and Setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getProductCount() {
            return productCount;
        }

        public void setProductCount(Integer productCount) {
            this.productCount = productCount;
        }

        public String getLogoUrl() {
            return logoUrl;
        }

        public void setLogoUrl(String logoUrl) {
            this.logoUrl = logoUrl;
        }
    }

    @Override
    public String toString() {
        return "SearchSuggestionsResponse{" +
                "query='" + query + '\'' +
                ", totalSuggestions=" + totalSuggestions +
                ", products=" + products.size() +
                ", brands=" + brands.size() +
                ", executionTime=" + executionTime + "ms" +
                '}';
    }
}