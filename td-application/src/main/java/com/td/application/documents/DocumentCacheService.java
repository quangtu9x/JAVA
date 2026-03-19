package com.td.application.documents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.td.application.common.models.PaginationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentCacheService {

    public static final String DOCUMENT_BY_ID_CACHE = "documents:by-id";
    public static final String DOCUMENT_LIST_CACHE = "documents:list";

    private final CacheManager cacheManager;
    private final ObjectMapper cacheKeyObjectMapper = buildCacheKeyObjectMapper();
    private final LongAdder documentByIdHits = new LongAdder();
    private final LongAdder documentByIdMisses = new LongAdder();
    private final LongAdder documentByIdPuts = new LongAdder();
    private final LongAdder documentByIdEvictions = new LongAdder();
    private final LongAdder documentListHits = new LongAdder();
    private final LongAdder documentListMisses = new LongAdder();
    private final LongAdder documentListPuts = new LongAdder();
    private final LongAdder documentListEvictions = new LongAdder();

    public DocumentDto get(UUID documentId) {
        if (documentId == null) {
            return null;
        }

        Cache cache = resolveCache(DOCUMENT_BY_ID_CACHE);
        if (cache == null) {
            return null;
        }

        try {
            var cachedDocument = cache.get(buildDocumentKey(documentId), DocumentDto.class);
            if (cachedDocument == null) {
                documentByIdMisses.increment();
                return null;
            }

            documentByIdHits.increment();
            return cachedDocument;
        } catch (Exception ex) {
            documentByIdMisses.increment();
            log.debug("Failed to read document {} from Redis cache: {}", documentId, ex.getMessage());
            return null;
        }
    }

    public void put(UUID documentId, DocumentDto document) {
        if (documentId == null || document == null) {
            return;
        }

        Cache cache = resolveCache(DOCUMENT_BY_ID_CACHE);
        if (cache == null) {
            return;
        }

        try {
            cache.put(buildDocumentKey(documentId), document);
            documentByIdPuts.increment();
        } catch (Exception ex) {
            log.debug("Failed to write document {} to Redis cache: {}", documentId, ex.getMessage());
        }
    }

    public void evict(UUID documentId) {
        if (documentId == null) {
            return;
        }

        Cache cache = resolveCache(DOCUMENT_BY_ID_CACHE);
        if (cache == null) {
            return;
        }

        try {
            cache.evict(buildDocumentKey(documentId));
            documentByIdEvictions.increment();
        } catch (Exception ex) {
            log.debug("Failed to evict document {} from Redis cache: {}", documentId, ex.getMessage());
        }
    }

    /**
     * Kiểm tra document có trong cache không, không tính vào stats counter.
     * Dùng để xác định giá trị header X-Cache trước khi gọi use case.
     */
    public boolean isCachedById(UUID documentId) {
        if (documentId == null) {
            return false;
        }

        Cache cache = resolveCache(DOCUMENT_BY_ID_CACHE);
        if (cache == null) {
            return false;
        }

        try {
            return cache.get(buildDocumentKey(documentId)) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Kiểm tra list/search có trong cache không, không tính vào stats counter.
     * Dùng để xác định giá trị header X-Cache trước khi gọi use case.
     */
    public boolean isListCached(SearchDocumentsRequest request) {
        if (request == null) {
            return false;
        }

        Cache cache = resolveCache(DOCUMENT_LIST_CACHE);
        if (cache == null) {
            return false;
        }

        try {
            return cache.get(buildListKey(request)) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    public PaginationResponse<DocumentDto> getList(SearchDocumentsRequest request) {
        if (request == null) {
            return null;
        }

        Cache cache = resolveCache(DOCUMENT_LIST_CACHE);
        if (cache == null) {
            return null;
        }

        try {
            var cachedPage = cache.get(buildListKey(request), DocumentListCacheEntry.class);
            if (cachedPage == null) {
                documentListMisses.increment();
                return null;
            }

            documentListHits.increment();
            return cachedPage.toPaginationResponse();
        } catch (Exception ex) {
            documentListMisses.increment();
            log.debug("Failed to read document list from Redis cache: {}", ex.getMessage());
            return null;
        }
    }

    public void putList(SearchDocumentsRequest request, PaginationResponse<DocumentDto> response) {
        if (request == null || response == null) {
            return;
        }

        Cache cache = resolveCache(DOCUMENT_LIST_CACHE);
        if (cache == null) {
            return;
        }

        try {
            cache.put(buildListKey(request), DocumentListCacheEntry.from(response));
            documentListPuts.increment();
        } catch (Exception ex) {
            log.debug("Failed to write document list to Redis cache: {}", ex.getMessage());
        }
    }

    public void evictAllListCaches() {
        Cache cache = resolveCache(DOCUMENT_LIST_CACHE);
        if (cache == null) {
            return;
        }

        try {
            cache.clear();
            documentListEvictions.increment();
        } catch (Exception ex) {
            log.debug("Failed to clear document list cache: {}", ex.getMessage());
        }
    }

    public DocumentCacheStatsDto getStats() {
        return DocumentCacheStatsDto.builder()
            .documentByIdHits(documentByIdHits.sum())
            .documentByIdMisses(documentByIdMisses.sum())
            .documentByIdPuts(documentByIdPuts.sum())
            .documentByIdEvictions(documentByIdEvictions.sum())
            .documentListHits(documentListHits.sum())
            .documentListMisses(documentListMisses.sum())
            .documentListPuts(documentListPuts.sum())
            .documentListEvictions(documentListEvictions.sum())
            .build();
    }

    private Cache resolveCache(String cacheName) {
        return cacheManager.getCache(cacheName);
    }

    /**
     * Trả về key nội bộ trong cache (không kể tên cache).
     * Dùng để truyền vào CachedResult/CachedPaginationResponse.
     */
    public String getDocumentCacheKey(UUID documentId) {
        return DOCUMENT_BY_ID_CACHE + "::" + buildDocumentKey(documentId);
    }

    /**
     * Trả về key nội bộ trong list cache (không kể tên cache).
     * Dùng để truyền vào CachedPaginationResponse.
     */
    public String getListCacheKey(SearchDocumentsRequest request) {
        return DOCUMENT_LIST_CACHE + "::" + buildListKey(request);
    }

    private String buildDocumentKey(UUID documentId) {
        return "doc:" + documentId;
    }

    private String buildListKey(SearchDocumentsRequest request) {
        return "search:" + sha256(serializeRequest(request));
    }

    private String serializeRequest(SearchDocumentsRequest request) {
        try {
            return cacheKeyObjectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            log.debug("Failed to serialize SearchDocumentsRequest for cache key: {}", ex.getMessage());
            return request.toString();
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private ObjectMapper buildCacheKeyObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        return objectMapper;
    }
}
