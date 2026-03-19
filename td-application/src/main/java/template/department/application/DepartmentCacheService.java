package template.department.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.td.application.common.models.PaginationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentCacheService {

    public static final String DEPARTMENT_BY_ID_CACHE = "departments:by-id";
    public static final String DEPARTMENT_LIST_CACHE = "departments:list";

    private final CacheManager cacheManager;
    private final ObjectMapper cacheKeyObjectMapper = buildCacheKeyObjectMapper();

    private final LongAdder byIdHits = new LongAdder();
    private final LongAdder byIdMisses = new LongAdder();
    private final LongAdder byIdPuts = new LongAdder();
    private final LongAdder byIdEvictions = new LongAdder();
    private final LongAdder listHits = new LongAdder();
    private final LongAdder listMisses = new LongAdder();
    private final LongAdder listPuts = new LongAdder();
    private final LongAdder listEvictions = new LongAdder();

    public DepartmentDto get(UUID id) {
        if (id == null) {
            return null;
        }
        Cache cache = resolve(DEPARTMENT_BY_ID_CACHE);
        if (cache == null) {
            return null;
        }
        try {
            var value = cache.get(idKey(id), DepartmentDto.class);
            if (value == null) {
                byIdMisses.increment();
                return null;
            }
            byIdHits.increment();
            return value;
        } catch (Exception ex) {
            byIdMisses.increment();
            log.debug("Department by-id cache read failed for {}: {}", id, ex.getMessage());
            return null;
        }
    }

    public void put(UUID id, DepartmentDto dto) {
        if (id == null || dto == null) {
            return;
        }
        Cache cache = resolve(DEPARTMENT_BY_ID_CACHE);
        if (cache == null) {
            return;
        }
        try {
            cache.put(idKey(id), dto);
            byIdPuts.increment();
        } catch (Exception ex) {
            log.debug("Department by-id cache put failed: {}", ex.getMessage());
        }
    }

    public void evict(UUID id) {
        if (id == null) {
            return;
        }
        Cache cache = resolve(DEPARTMENT_BY_ID_CACHE);
        if (cache == null) {
            return;
        }
        try {
            cache.evict(idKey(id));
            byIdEvictions.increment();
        } catch (Exception ex) {
            log.debug("Department by-id cache evict failed: {}", ex.getMessage());
        }
    }

    public boolean isCachedById(UUID id) {
        if (id == null) {
            return false;
        }
        Cache cache = resolve(DEPARTMENT_BY_ID_CACHE);
        if (cache == null) {
            return false;
        }
        try {
            return cache.get(idKey(id)) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    public String getDepartmentByIdCacheKey(UUID id) {
        return DEPARTMENT_BY_ID_CACHE + "::" + idKey(id);
    }

    public PaginationResponse<DepartmentDto> getList(SearchDepartmentsRequest request) {
        if (request == null) {
            return null;
        }
        Cache cache = resolve(DEPARTMENT_LIST_CACHE);
        if (cache == null) {
            return null;
        }
        try {
            var value = cache.get(listKey(request), DepartmentListCacheEntry.class);
            if (value == null) {
                listMisses.increment();
                return null;
            }
            listHits.increment();
            return value.toPaginationResponse();
        } catch (Exception ex) {
            listMisses.increment();
            log.debug("Department list cache read failed: {}", ex.getMessage());
            return null;
        }
    }

    public void putList(SearchDepartmentsRequest request, PaginationResponse<DepartmentDto> response) {
        if (request == null || response == null) {
            return;
        }
        Cache cache = resolve(DEPARTMENT_LIST_CACHE);
        if (cache == null) {
            return;
        }
        try {
            cache.put(listKey(request), DepartmentListCacheEntry.from(response));
            listPuts.increment();
        } catch (Exception ex) {
            log.debug("Department list cache put failed: {}", ex.getMessage());
        }
    }

    public void evictAllListCaches() {
        Cache cache = resolve(DEPARTMENT_LIST_CACHE);
        if (cache == null) {
            return;
        }
        try {
            cache.clear();
            listEvictions.increment();
        } catch (Exception ex) {
            log.debug("Department list cache clear failed: {}", ex.getMessage());
        }
    }

    public boolean isListCached(SearchDepartmentsRequest request) {
        if (request == null) {
            return false;
        }
        Cache cache = resolve(DEPARTMENT_LIST_CACHE);
        if (cache == null) {
            return false;
        }
        try {
            return cache.get(listKey(request)) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    public String getDepartmentListCacheKey(SearchDepartmentsRequest request) {
        return DEPARTMENT_LIST_CACHE + "::" + listKey(request);
    }

    public DepartmentCacheStatsDto getStats() {
        return DepartmentCacheStatsDto.builder()
            .departmentByIdHits(byIdHits.sum())
            .departmentByIdMisses(byIdMisses.sum())
            .departmentByIdPuts(byIdPuts.sum())
            .departmentByIdEvictions(byIdEvictions.sum())
            .departmentListHits(listHits.sum())
            .departmentListMisses(listMisses.sum())
            .departmentListPuts(listPuts.sum())
            .departmentListEvictions(listEvictions.sum())
            .build();
    }

    private Cache resolve(@NonNull String name) {
        return cacheManager.getCache(name);
    }

    @NonNull
    private String idKey(UUID id) {
        return "department:" + id;
    }

    @NonNull
    private String listKey(SearchDepartmentsRequest request) {
        return "search:" + sha256(serialize(request));
    }

    private String serialize(SearchDepartmentsRequest request) {
        try {
            return cacheKeyObjectMapper.writeValueAsString(request);
        } catch (Exception ex) {
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
        ObjectMapper mapper = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return mapper;
    }
}