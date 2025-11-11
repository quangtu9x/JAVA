package com.td.application.search;

import java.math.BigDecimal;
import java.util.List;

/**
 * Advanced Search Products Request
 * 
 * Request DTO cho advanced product search với multiple filters.
 */
public class AdvancedSearchProductsRequest {

    /**
     * Search query (tìm kiếm trong name, description, brand name)
     */
    private String query;

    /**
     * Filter theo brand IDs
     */
    private List<String> brandIds;

    /**
     * Filter theo categories
     */
    private List<String> categories;

    /**
     * Filter theo tags
     */
    private List<String> tags;

    /**
     * Price range filter
     */
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    /**
     * Rating filter
     */
    private Float minRating;

    /**
     * Pagination
     */
    private int page = 0;
    private int size = 20;

    /**
     * Sorting
     */
    private String sortBy = "name";
    private String sortDirection = "asc";

    /**
     * Search options
     */
    private boolean includeInactive = false;
    private boolean fuzzySearch = true;

    // Constructors
    public AdvancedSearchProductsRequest() {}

    public AdvancedSearchProductsRequest(String query) {
        this.query = query;
    }

    // Getters and Setters
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<String> getBrandIds() {
        return brandIds;
    }

    public void setBrandIds(List<String> brandIds) {
        this.brandIds = brandIds;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public Float getMinRating() {
        return minRating;
    }

    public void setMinRating(Float minRating) {
        this.minRating = minRating;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = Math.min(Math.max(1, size), 100); // Limit to max 100 items per page
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    public boolean isIncludeInactive() {
        return includeInactive;
    }

    public void setIncludeInactive(boolean includeInactive) {
        this.includeInactive = includeInactive;
    }

    public boolean isFuzzySearch() {
        return fuzzySearch;
    }

    public void setFuzzySearch(boolean fuzzySearch) {
        this.fuzzySearch = fuzzySearch;
    }

    @Override
    public String toString() {
        return "AdvancedSearchProductsRequest{" +
                "query='" + query + '\'' +
                ", brandIds=" + brandIds +
                ", categories=" + categories +
                ", minPrice=" + minPrice +
                ", maxPrice=" + maxPrice +
                ", minRating=" + minRating +
                ", page=" + page +
                ", size=" + size +
                ", sortBy='" + sortBy + '\'' +
                ", sortDirection='" + sortDirection + '\'' +
                '}';
    }
}