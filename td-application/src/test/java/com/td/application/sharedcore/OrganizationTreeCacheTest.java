package com.td.application.sharedcore;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** 15/10/2026: cache cây cơ cấu tổ chức (Redis + JVM) — xem javadoc {@link OrganizationTreeCache}. */
class OrganizationTreeCacheTest {

    private final InMemoryOrganizationTreeCacheStore store = new InMemoryOrganizationTreeCacheStore();
    private final AtomicInteger builds = new AtomicInteger();

    private Supplier<List<LegacyOrganizationTreeNode>> builder(String rootName) {
        return () -> {
            builds.incrementAndGet();
            return List.of(node(rootName));
        };
    }

    @Test
    void getFullTree_sameVersion_shouldBuildOnce() {
        OrganizationTreeCache cache = new OrganizationTreeCache(store, null);

        cache.getFullTree("org", builder("A"));
        List<LegacyOrganizationTreeNode> second = cache.getFullTree("org", builder("A"));

        assertThat(builds).hasValue(1);
        assertThat(second.get(0).getText()).isEqualTo("A");
    }

    @Test
    void invalidate_shouldRebuildWithNewData() {
        OrganizationTreeCache cache = new OrganizationTreeCache(store, null);
        cache.getFullTree("org", builder("A"));

        cache.invalidate();
        List<LegacyOrganizationTreeNode> afterChange = cache.getFullTree("org", builder("B"));

        assertThat(builds).hasValue(2);
        assertThat(afterChange.get(0).getText()).isEqualTo("B");
    }

    // Instance khác (cùng Redis) đọc cây đã dựng sẵn, không dựng lại.
    @Test
    void getFullTree_otherInstance_shouldReadSharedTreeWithoutBuilding() {
        new OrganizationTreeCache(store, null).getFullTree("org", builder("A"));
        OrganizationTreeCache otherInstance = new OrganizationTreeCache(store, null);

        List<LegacyOrganizationTreeNode> tree = otherInstance.getFullTree("org", builder("A"));

        assertThat(builds).hasValue(1);
        assertThat(tree.get(0).getText()).isEqualTo("A");
        assertThat(tree.get(0).getData()).containsEntry("identifier", "A-ID");
    }

    // Invalidate ở instance này -> instance khác thấy phiên bản mới, không dùng cây cũ trong JVM.
    @Test
    void invalidate_onOneInstance_shouldBeSeenByOtherInstance() {
        OrganizationTreeCache first = new OrganizationTreeCache(store, null);
        OrganizationTreeCache second = new OrganizationTreeCache(store, null);
        second.getFullTree("org", builder("A"));

        first.invalidate();
        List<LegacyOrganizationTreeNode> tree = second.getFullTree("org", builder("B"));

        assertThat(tree.get(0).getText()).isEqualTo("B");
    }

    @Test
    void getFullTree_variantsAreIndependent() {
        OrganizationTreeCache cache = new OrganizationTreeCache(store, null);

        cache.getFullTree("org", builder("A"));
        List<LegacyOrganizationTreeNode> withUsers = cache.getFullTree("org+users", builder("U"));

        assertThat(builds).hasValue(2);
        assertThat(withUsers.get(0).getText()).isEqualTo("U");
    }

    // Redis lỗi không được chặn nghiệp vụ — vẫn trả cây, và dùng lại cây trong JVM.
    @Test
    void getFullTree_whenRedisDown_shouldStillServeAndReuseLocal() {
        store.failing = true;
        OrganizationTreeCache cache = new OrganizationTreeCache(store, null);

        List<LegacyOrganizationTreeNode> first = cache.getFullTree("org", builder("A"));
        cache.getFullTree("org", builder("A"));
        cache.invalidate();

        assertThat(first.get(0).getText()).isEqualTo("A");
        assertThat(builds).hasValue(1);
    }

    // Chống dựng đồng loạt: nhiều request cùng lúc khi cache trống -> chỉ 1 lần dựng.
    @Test
    void getFullTree_concurrentMisses_shouldBuildOnce() throws Exception {
        OrganizationTreeCache cache = new OrganizationTreeCache(store, null);
        CountDownLatch start = new CountDownLatch(1);
        Supplier<List<LegacyOrganizationTreeNode>> slowBuilder = () -> {
            builds.incrementAndGet();
            sleep(200);
            return List.of(node("A"));
        };
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<List<LegacyOrganizationTreeNode>>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return cache.getFullTree("org", slowBuilder);
                }));
            }
            start.countDown();
            for (Future<List<LegacyOrganizationTreeNode>> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS).get(0).getText()).isEqualTo("A");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(builds).hasValue(1);
    }

    @Test
    void passThrough_shouldAlwaysBuild() {
        OrganizationTreeCache cache = OrganizationTreeCache.passThrough();

        cache.getFullTree("org", builder("A"));
        cache.getFullTree("org", builder("A"));

        assertThat(builds).hasValue(2);
    }

    private static LegacyOrganizationTreeNode node(String name) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("identifier", name + "-ID");
        return new LegacyOrganizationTreeNode(name, name, "agency", data, new ArrayList<>());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
