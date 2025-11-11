package com.td.infrastructure.search.repository;

import com.td.domain.search.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product Elasticsearch Repository
 * 
 * Repository interface cho Product search operations sử dụng Spring Data Elasticsearch.
 * Cung cấp các method tìm kiếm phức tạp, filtering, và aggregations cho products.
 */
@Repository
public interface ProductElasticsearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    /**
     * Tìm kiếm products theo tên với fuzzy matching
     * 
     * @param name product name
     * @param pageable phân trang
     * @return page of products
     */
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"nameAutocomplete^2\"], \"fuzziness\": \"AUTO\"}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<ProductDocument> searchByName(String name, Pageable pageable);

    /**
     * Advanced search với multiple fields và fuzzy matching
     * 
     * @param query search query
     * @param pageable phân trang
     * @return page of products
     */
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^4\", \"description^2\", \"brandName^3\", \"nameAutocomplete^2\", \"brandNameAutocomplete^2\"], \"fuzziness\": \"AUTO\", \"operator\": \"and\"}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<ProductDocument> advancedSearch(String query, Pageable pageable);

    /**
     * Tìm products theo brand
     * 
     * @param brandId brand ID
     * @param pageable phân trang
     * @return page of products
     */
    Page<ProductDocument> findByBrandIdAndIsActive(String brandId, Boolean isActive, Pageable pageable);

    /**
     * Tìm products theo brand name
     * 
     * @param brandName brand name
     * @param pageable phân trang
     * @return page of products
     */
    Page<ProductDocument> findByBrandNameAndIsActive(String brandName, Boolean isActive, Pageable pageable);

    /**
     * Tìm products theo price range
     * 
     * @param minPrice minimum price
     * @param maxPrice maximum price
     * @param pageable phân trang
     * @return page of products
     */
    @Query("{\"bool\": {\"must\": [{\"range\": {\"price\": {\"gte\": \"?0\", \"lte\": \"?1\"}}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<ProductDocument> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    /**
     * Autocomplete suggestions cho product names
     * 
     * @param prefix prefix để autocomplete
     * @param pageable phân trang
     * @return page of matching products
     */
    @Query("{\"bool\": {\"must\": [{\"match_phrase_prefix\": {\"nameAutocomplete\": \"?0\"}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<ProductDocument> findAutocompleteByNamePrefix(String prefix, Pageable pageable);

    /**
     * Tìm products theo categories
     * 
     * @param categories list of categories
     * @param pageable phân trang
     * @return page of products
     */
    @Query("{\"bool\": {\"must\": [{\"terms\": {\"categories\": [?0]}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<ProductDocument> findByCategories(List<String> categories, Pageable pageable);

    /**
     * Tìm products theo tags
     * 
     * @param tags list of tags
     * @param pageable phân trang
     * @return page of products
     */
    @Query("{\"bool\": {\"must\": [{\"terms\": {\"tags\": [?0]}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<ProductDocument> findByTags(List<String> tags, Pageable pageable);

    /**
     * Tìm top-rated products
     * 
     * @param minRating minimum rating
     * @param pageable phân trang
     * @return page of products sorted by rating
     */
    @Query("{\"bool\": {\"must\": [{\"range\": {\"rating\": {\"gte\": \"?0\"}}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<ProductDocument> findTopRatedProducts(Float minRating, Pageable pageable);

    /**
     * Tìm popular products (nhiều views/orders)
     * 
     * @param pageable phân trang
     * @return page of popular products
     */
    @Query("{\"bool\": {\"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<ProductDocument> findPopularProducts(Pageable pageable);

    /**
     * Advanced filtered search với multiple criteria
     * 
     * @param query search query (có thể null)
     * @param brandIds list of brand IDs (có thể null)
     * @param categories list of categories (có thể null)
     * @param minPrice minimum price (có thể null)
     * @param maxPrice maximum price (có thể null)
     * @param minRating minimum rating (có thể null)
     * @param pageable phân trang
     * @return page of filtered products
     */
    @Query("{\"bool\": {" +
           "\"must\": [" +
           "{\"bool\": {\"should\": [" +
           "{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^4\", \"description^2\", \"brandName^3\"], \"fuzziness\": \"AUTO\"}}," +
           "{\"match_all\": {}}" +
           "]}}], " +
           "\"filter\": [" +
           "{\"term\": {\"isActive\": true}}," +
           "{\"bool\": {\"should\": [{\"terms\": {\"brandId\": \"?1\"}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"brandId\"}}}}]}}," +
           "{\"bool\": {\"should\": [{\"terms\": {\"categories\": \"?2\"}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"categories\"}}}}]}}," +
           "{\"bool\": {\"should\": [{\"range\": {\"price\": {\"gte\": \"?3\", \"lte\": \"?4\"}}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"price\"}}}}]}}," +
           "{\"bool\": {\"should\": [{\"range\": {\"rating\": {\"gte\": \"?5\"}}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"rating\"}}}}]}}" +
           "]}}")
    Page<ProductDocument> advancedFilteredSearch(String query, List<String> brandIds, List<String> categories, 
                                                BigDecimal minPrice, BigDecimal maxPrice, Float minRating, Pageable pageable);

    /**
     * Tìm products theo slug
     * 
     * @param slug product slug
     * @return product document
     */
    ProductDocument findBySlug(String slug);

    /**
     * Tìm similar products (based on name and brand)
     * 
     * @param productName product name để tìm similar
     * @param brandName brand name
     * @param currentProductId exclude current product
     * @param pageable phân trang
     * @return page of similar products
     */
    @Query("{\"bool\": {" +
           "\"must\": [" +
           "{\"more_like_this\": {\"fields\": [\"name\", \"description\", \"brandName\"], \"like\": \"?0 ?1\", \"min_term_freq\": 1, \"min_doc_freq\": 1}}" +
           "], " +
           "\"filter\": [" +
           "{\"term\": {\"isActive\": true}}" +
           "], " +
           "\"must_not\": [" +
           "{\"term\": {\"id\": \"?2\"}}" +
           "]}}")
    Page<ProductDocument> findSimilarProducts(String productName, String brandName, String currentProductId, Pageable pageable);

    /**
     * Count products by brand
     * 
     * @param brandId brand ID
     * @return count of products
     */
    long countByBrandIdAndIsActive(String brandId, Boolean isActive);

    /**
     * Count total active products
     * 
     * @return count of active products
     */
    long countByIsActive(Boolean isActive);

    /**
     * Tìm products được tạo bởi user
     * 
     * @param createdBy user id
     * @param pageable phân trang
     * @return page of products
     */
    Page<ProductDocument> findByCreatedBy(String createdBy, Pageable pageable);

    /**
     * Search suggestions (cho search-as-you-type)
     * 
     * @param prefix search prefix
     * @param pageable phân trang
     * @return page of suggestions
     */
    @Query("{\"bool\": {\"should\": [" +
           "{\"match_phrase_prefix\": {\"nameAutocomplete\": \"?0\"}}," +
           "{\"match_phrase_prefix\": {\"brandNameAutocomplete\": \"?0\"}}" +
           "], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<ProductDocument> findSearchSuggestions(String prefix, Pageable pageable);
}