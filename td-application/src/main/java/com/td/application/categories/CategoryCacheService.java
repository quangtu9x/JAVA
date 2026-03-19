package com.td.application.categories;

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
public class CategoryCacheService {

    public static final String CATEGORY_BY_ID_CACHE = "categories:by-id";
    public static final String CATEGORY_LIST_CACHE  = "categories:list";

    private final CacheManager cacheManager;
    private final ObjectMapper cacheKeyObjectMapper = buildCacheKeyObjectMapper();

    private final LongAdder byIdHits      = new LongAdder();
    private final LongAdder byIdMisses    = new LongAdder();
    private final LongAdder byIdPuts      = new LongAdder();
    private final LongAdder byIdEvictions = new LongAdder();
    private final LongAdder listHits      = new LongAdder();
    private final LongAdder listMisses    = new LongAdder();
    private final LongAdder listPuts      = new LongAdder();
    private final LongAdder listEvictions = new LongAdder();

    // ── By-ID cache ──────────────────────────────────────────────

    public CategoryDto get(UUID id) {
        if (id == null) return null;
        Cache cache = resolve(CATEGORY_BY_ID_CACHE);
        if (cache == null) return null;
        try {
            var v = cache.get(idKey(id), CategoryDto.class);
            if (v == null) { byIdMisses.increment(); return null; }
            byIdHits.increment();
            return v;
        } catch (Exception ex) {
            byIdMisses.increment();
            log.debug("Category by-id cache read failed for {}: {}", id, ex.getMessage());
            return null;
        }
    }

    public void put(UUID id, CategoryDto dto) {
        if (id == null || dto == null) return;
        Cache cache = resolve(CATEGORY_BY_ID_CACHE);
        if (cache == null) return;
        try { cache.put(idKey(id), dto); byIdPuts.increment(); }
        catch (Exception ex) { log.debug("Category by-id cache put failed: {}", ex.getMessage()); }
    }

    public void evict(UUID id) {
        if (id == null) return;
        Cache cache = resolve(CATEGORY_BY_ID_CACHE);
        if (cache == null) return;
        try { cache.evict(idKey(id)); byIdEvictions.increment(); }
        catch (Exception ex) { log.debug("Category by-id cache evict failed: {}", ex.getMessage()); }
    }

    public boolean isCachedById(UUID id) {
        if (id == null) return false;
        Cache cache = resolve(CATEGORY_BY_ID_CACHE);
        if (cache == null) return false;
        try { return cache.get(idKey(id)) != null; }
        catch (Exception ex) { return false; }
    }

    public String getCategoryByIdCacheKey(UUID id) {
        return CATEGORY_BY_ID_CACHE + "::" + idKey(id);
    }

    // ── List cache ───────────────────────────────────────────────

    public PaginationResponse<CategoryDto> getList(SearchCategoriesRequest request) {
        if (request == null) return null;
        Cache cache = resolve(CATEGORY_LIST_CACHE);
        if (cache == null) return null;
        try {
            var v = cache.get(listKey(request), CategoryListCacheEntry.class);
            if (v == null) { listMisses.increment(); return null; }
            listHits.increment();
            return v.toPaginationResponse();
        } catch (Exception ex) {
            listMisses.increment();
            log.debug("Category list cache read failed: {}", ex.getMessage());
            return null;
        }
    }

    public void putList(SearchCategoriesRequest request, PaginationResponse<CategoryDto> response) {
        if (request == null || response == null) return;
        Cache cache = resolve(CATEGORY_LIST_CACHE);
        if (cache == null) return;
        try { cache.put(listKey(request), CategoryListCacheEntry.from(response)); listPuts.increment(); }
        catch (Exception ex) { log.debug("Category list cache put failed: {}", ex.getMessage()); }
    }

    public void evictAllListCaches() {
        Cache cache = resolve(CATEGORY_LIST_CACHE);
        if (cache == null) return;
        try { cache.clear(); listEvictions.increment(); }
        catch (Exception ex) { log.debug("Category list cache clear failed: {}", ex.getMessage()); }
    }

    public boolean isListCached(SearchCategoriesRequest request) {
        if (request == null) return false;
        Cache cache = resolve(CATEGORY_LIST_CACHE);
        if (cache == null) return false;
        try { return cache.get(listKey(request)) != null; }
        catch (Exception ex) { return false; }
    }

    public String getCategoryListCacheKey(SearchCategoriesRequest request) {
        return CATEGORY_LIST_CACHE + "::" + listKey(request);
    }

    // ── Stats ────────────────────────────────────────────────────

    public CategoryCacheStatsDto getStats() {
        return CategoryCacheStatsDto.builder()
            .categoryByIdHits(byIdHits.sum())
            .categoryByIdMisses(byIdMisses.sum())
            .categoryByIdPuts(byIdPuts.sum())
            .categoryByIdEvictions(byIdEvictions.sum())
            .categoryListHits(listHits.sum())
            .categoryListMisses(listMisses.sum())
            .categoryListPuts(listPuts.sum())
            .categoryListEvictions(listEvictions.sum())
            .build();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private Cache resolve(String name) { return cacheManager.getCache(name); }

    private String idKey(UUID id) { return "category:" + id; }

    private String listKey(SearchCategoriesRequest request) {
        return "search:" + sha256(serialize(request));
    }

    private String serialize(SearchCategoriesRequest request) {
        try { return cacheKeyObjectMapper.writeValueAsString(request); }
        catch (Exception ex) { return request.toString(); }
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
        ObjectMapper m = new ObjectMapper();
        m.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        m.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        return m;
    }
}
