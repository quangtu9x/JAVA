package com.td.application.search;

/**
 * Search Suggestions Request
 * 
 * Request DTO cho autocomplete và search suggestions.
 */
public class SearchSuggestionsRequest {

    /**
     * Search prefix hoặc query
     */
    private String query;

    /**
     * Type of suggestions cần lấy
     */
    private SuggestionType type = SuggestionType.ALL;

    /**
     * Maximum number of suggestions
     */
    private int limit = 10;

    /**
     * Include brand suggestions
     */
    private boolean includeBrands = true;

    /**
     * Include product suggestions
     */
    private boolean includeProducts = true;

    /**
     * Minimum characters required for suggestions
     */
    private int minLength = 2;

    // Constructors
    public SearchSuggestionsRequest() {}

    public SearchSuggestionsRequest(String query) {
        this.query = query;
    }

    public SearchSuggestionsRequest(String query, SuggestionType type, int limit) {
        this.query = query;
        this.type = type;
        this.limit = limit;
    }

    // Getters and Setters
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public SuggestionType getType() {
        return type;
    }

    public void setType(SuggestionType type) {
        this.type = type;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = Math.min(Math.max(1, limit), 50); // Limit between 1-50
    }

    public boolean isIncludeBrands() {
        return includeBrands;
    }

    public void setIncludeBrands(boolean includeBrands) {
        this.includeBrands = includeBrands;
    }

    public boolean isIncludeProducts() {
        return includeProducts;
    }

    public void setIncludeProducts(boolean includeProducts) {
        this.includeProducts = includeProducts;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = Math.max(1, minLength);
    }

    /**
     * Suggestion Types
     */
    public enum SuggestionType {
        ALL,          // Tất cả suggestions
        PRODUCTS,     // Chỉ product suggestions
        BRANDS,       // Chỉ brand suggestions
        AUTOCOMPLETE  // Autocomplete suggestions
    }

    @Override
    public String toString() {
        return "SearchSuggestionsRequest{" +
                "query='" + query + '\'' +
                ", type=" + type +
                ", limit=" + limit +
                ", includeBrands=" + includeBrands +
                ", includeProducts=" + includeProducts +
                '}';
    }
}