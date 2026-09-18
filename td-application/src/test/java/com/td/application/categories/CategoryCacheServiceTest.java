package com.td.application.categories;

import com.td.application.common.models.PaginationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-105 (DanhMuc Module) — Definition of Done (2): cache danh mục hoạt động đúng (MISS lần đầu,
 * HIT lần sau, evict làm mới dữ liệu). Dùng {@link ConcurrentMapCacheManager} thật (không mock
 * {@code Cache}) để chứng minh đúng hành vi get/put/evict thật của Spring Cache abstraction —
 * hành vi tương đương Redis cho các thao tác get/put/evict/clear được kiểm thử ở đây.
 */
class CategoryCacheServiceTest {

    private CategoryCacheService cacheService;

    @BeforeEach
    void setUp() {
        var cacheManager = new ConcurrentMapCacheManager(
            CategoryCacheService.CATEGORY_BY_ID_CACHE, CategoryCacheService.CATEGORY_LIST_CACHE);
        cacheService = new CategoryCacheService(cacheManager);
    }

    @Test
    void listCache_shouldMissThenHit() {
        var request = new SearchCategoriesRequest();
        assertThat(cacheService.getList(request)).isNull();

        var dto = new CategoryDto();
        dto.setId(UUID.randomUUID());
        dto.setName("Danh muc A");
        var response = new PaginationResponse<>(List.of(dto), 0, 20, 1L, 1, true, true);
        cacheService.putList(request, response);

        var cached = cacheService.getList(request);
        assertThat(cached).isNotNull();
        assertThat(cached.getItems()).hasSize(1);
        assertThat(cached.getItems().get(0).getName()).isEqualTo("Danh muc A");

        assertThat(cacheService.getStats().getCategoryListMisses()).isEqualTo(1L);
        assertThat(cacheService.getStats().getCategoryListHits()).isEqualTo(1L);
    }

    @Test
    void evictAllListCaches_shouldForceFreshMissAfterward() {
        var request = new SearchCategoriesRequest();
        var response = new PaginationResponse<>(List.of(new CategoryDto()), 0, 20, 1L, 1, true, true);
        cacheService.putList(request, response);
        assertThat(cacheService.getList(request)).isNotNull();

        cacheService.evictAllListCaches();

        assertThat(cacheService.getList(request)).isNull();
    }

    @Test
    void byIdCache_shouldMissThenHitThenEvict() {
        UUID id = UUID.randomUUID();
        assertThat(cacheService.get(id)).isNull();
        assertThat(cacheService.isCachedById(id)).isFalse();

        var dto = new CategoryDto();
        dto.setId(id);
        cacheService.put(id, dto);

        assertThat(cacheService.isCachedById(id)).isTrue();
        assertThat(cacheService.get(id)).isNotNull();

        cacheService.evict(id);
        assertThat(cacheService.isCachedById(id)).isFalse();
    }

    @Test
    void differentRequests_shouldUseIndependentCacheKeys() {
        var requestA = new SearchCategoriesRequest();
        requestA.setKeyword("a");
        var requestB = new SearchCategoriesRequest();
        requestB.setKeyword("b");

        cacheService.putList(requestA, new PaginationResponse<>(List.of(new CategoryDto()), 0, 20, 1L, 1, true, true));

        assertThat(cacheService.getList(requestA)).isNotNull();
        assertThat(cacheService.getList(requestB)).isNull();
    }
}
