package com.td.infrastructure.search.repository;

import com.td.domain.search.BrandDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Brand Elasticsearch Repository
 * 
 * Repository interface cho Brand search operations sử dụng Spring Data Elasticsearch.
 * Cung cấp các method tìm kiếm nâng cao và autocomplete cho brands.
 */
@Repository
public interface BrandElasticsearchRepository extends ElasticsearchRepository<BrandDocument, String> {

    /**
     * Tìm kiếm brands theo tên với fuzzy matching
     * 
     * @param name tên brand cần tìm
     * @param pageable phân trang
     * @return page of brands
     */
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^2\", \"nameAutocomplete\"], \"fuzziness\": \"AUTO\"}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<BrandDocument> searchByName(String name, Pageable pageable);

    /**
     * Tìm kiếm brands theo tên hoặc mô tả
     * 
     * @param query search query
     * @param pageable phân trang
     * @return page of brands
     */
    @Query("{\"bool\": {\"must\": [{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"description^1\", \"nameAutocomplete^2\"], \"fuzziness\": \"AUTO\", \"operator\": \"and\"}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<BrandDocument> searchByNameOrDescription(String query, Pageable pageable);

    /**
     * Autocomplete suggestions cho brand names
     * 
     * @param prefix prefix để autocomplete
     * @param pageable phân trang
     * @return page of matching brands
     */
    @Query("{\"bool\": {\"must\": [{\"match_phrase_prefix\": {\"nameAutocomplete\": \"?0\"}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<BrandDocument> findAutocompleteByNamePrefix(String prefix, Pageable pageable);

    /**
     * Tìm active brands
     * 
     * @param isActive trạng thái active
     * @param pageable phân trang
     * @return page of brands
     */
    Page<BrandDocument> findByIsActive(Boolean isActive, Pageable pageable);

    /**
     * Tìm brands theo slug
     * 
     * @param slug brand slug
     * @return brand document
     */
    BrandDocument findBySlug(String slug);

    /**
     * Tìm top brands có nhiều products nhất
     * 
     * @param pageable phân trang
     * @return page of brands sorted by product count
     */
    @Query("{\"bool\": {\"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<BrandDocument> findTopBrandsByProductCount(Pageable pageable);

    /**
     * Tìm brands được tạo bởi user
     * 
     * @param createdBy user id
     * @param pageable phân trang
     * @return page of brands
     */
    Page<BrandDocument> findByCreatedBy(String createdBy, Pageable pageable);

    /**
     * Advanced search với multiple filters
     * 
     * @param query search query (có thể null)
     * @param isActive active status filter (có thể null)
     * @param minProductCount minimum product count (có thể null)
     * @param pageable phân trang
     * @return page of brands
     */
    @Query("{\"bool\": {\"must\": [{\"bool\": {\"should\": [" +
           "{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"description^1\"], \"fuzziness\": \"AUTO\"}}," +
           "{\"match_all\": {}}" +
           "]}}], " +
           "\"filter\": [" +
           "{\"bool\": {\"should\": [{\"term\": {\"isActive\": \"?1\"}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"isActive\"}}}}]}}," +
           "{\"bool\": {\"should\": [{\"range\": {\"productCount\": {\"gte\": \"?2\"}}}, {\"bool\": {\"must_not\": {\"exists\": {\"field\": \"productCount\"}}}}]}}" +
           "]}}")
    Page<BrandDocument> advancedSearch(String query, Boolean isActive, Integer minProductCount, Pageable pageable);

    /**
     * Count total active brands
     * 
     * @return count of active brands
     */
    long countByIsActive(Boolean isActive);

    /**
     * Tìm similar brands (based on name similarity)
     * 
     * @param brandName tên brand để tìm similar
     * @param pageable phân trang
     * @return page of similar brands
     */
    @Query("{\"bool\": {\"must\": [{\"more_like_this\": {\"fields\": [\"name\", \"description\"], \"like\": \"?0\", \"min_term_freq\": 1, \"min_doc_freq\": 1}}], \"filter\": [{\"term\": {\"isActive\": true}}]}}")
    Page<BrandDocument> findSimilarBrands(String brandName, Pageable pageable);
}