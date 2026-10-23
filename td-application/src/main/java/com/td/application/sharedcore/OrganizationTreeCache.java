package com.td.application.sharedcore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 15/10/2026 (yêu cầu người dùng: cây cơ cấu thật có thể ~50k bản ghi; Domino thật từng treo hệ thống
 * khi không cache — xem Domino {@code CayCoCauToChuc.java}/{@code CayCapCoQuan.java} lưu Redis
 * {@code qlvb_CoCauToChuc_#}). Cache cây ĐẦY ĐỦ (chưa lọc theo cơ quan người xem, chưa gắn ủy quyền —
 * 2 phần đó áp lúc đọc ở {@link GetLegacyOrganizationTreeUseCase}) theo 2 tầng:
 *
 * <ol>
 *   <li><b>Redis</b> ({@link OrganizationTreeCacheStore}, dùng chung mọi instance): cây nén gzip, khoá
 *   theo PHIÊN BẢN {@code tree:v{version}:{variant}}. Thay đổi tổ chức/người dùng chỉ cần tăng
 *   phiên bản ({@link #invalidate()}) — bản cũ tự hết hạn theo TTL. Nhờ khoá theo phiên bản, 1 lần dựng
 *   cây chậm bắt đầu TRƯỚC khi có thay đổi sẽ ghi vào khoá của phiên bản cũ, không đè cây mới.</li>
 *   <li><b>JVM</b>: giữ cây đã giải nén kèm phiên bản — request thường chỉ tốn 1 lệnh GET phiên bản
 *   (vài byte) rồi dùng lại cây trong bộ nhớ, không tải/giải nén lại vài MB mỗi lần.</li>
 * </ol>
 *
 * <p><b>Chống dựng đồng loạt</b> (nguyên nhân treo kiểu Domino: cache hết hạn → hàng trăm request cùng
 * dựng cây): trong 1 JVM chỉ 1 luồng/biến thể được dựng (khoá {@code synchronized}); giữa các instance
 * dùng khoá Redis — instance không giành được khoá chờ instance kia ghi xong (tối đa
 * {@link #WAIT_FOR_OTHER_BUILDER}) rồi mới tự dựng.</p>
 *
 * <p><b>Redis lỗi</b>: không chặn nghiệp vụ — dùng cây trong JVM nếu còn trẻ hơn {@link #LOCAL_ONLY_TTL},
 * không thì dựng lại (vẫn 1 luồng/biến thể).</p>
 */
@Service
@Slf4j
public class OrganizationTreeCache {

    static final Duration TREE_TTL = Duration.ofHours(24);
    static final Duration LOCAL_ONLY_TTL = Duration.ofMinutes(5);
    static final Duration BUILD_LOCK_TTL = Duration.ofMinutes(2);
    static final Duration WAIT_FOR_OTHER_BUILDER = Duration.ofSeconds(30);
    private static final long POLL_INTERVAL_MS = 250;
    private static final long UNKNOWN_VERSION = -1;
    private static final TypeReference<List<LegacyOrganizationTreeNode>> TREE_TYPE = new TypeReference<>() { };

    private final OrganizationTreeCacheStore store;
    // Transaction read-only CHỈ bao quanh lần dựng cây (không bao cả request): request đang chờ luồng
    // khác dựng xong không giữ kết nối DB (50 request chờ = 50 kết nối treo -> cạn pool).
    private final TransactionTemplate buildTransaction;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, LocalEntry> localEntries = new ConcurrentHashMap<>();
    private final Map<String, Object> buildLocks = new ConcurrentHashMap<>();

    @Autowired
    public OrganizationTreeCache(ObjectProvider<OrganizationTreeCacheStore> storeProvider,
                                 @Qualifier("identityTransactionManager") ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        this(storeProvider.getIfAvailable(), transactionManagerProvider.getIfAvailable());
    }

    OrganizationTreeCache(OrganizationTreeCacheStore store, PlatformTransactionManager transactionManager) {
        this.store = store;
        if (transactionManager == null) {
            this.buildTransaction = null;
        } else {
            this.buildTransaction = new TransactionTemplate(transactionManager);
            this.buildTransaction.setReadOnly(true);
        }
    }

    /** Không cache gì — luôn gọi builder (dùng cho unit test). */
    public static OrganizationTreeCache passThrough() {
        return new OrganizationTreeCache((OrganizationTreeCacheStore) null, (PlatformTransactionManager) null) {
            @Override
            public List<LegacyOrganizationTreeNode> getFullTree(String variant, Supplier<List<LegacyOrganizationTreeNode>> builder) {
                return builder.get();
            }

            @Override
            public void invalidate() {
                // không có gì để xoá
            }
        };
    }

    /**
     * Trả về cây đầy đủ của {@code variant} (vd "org", "org+users"). KẾT QUẢ DÙNG CHUNG giữa các
     * request — người gọi KHÔNG được sửa node/data/children, phải sao chép nếu cần biến đổi.
     */
    public List<LegacyOrganizationTreeNode> getFullTree(String variant, Supplier<List<LegacyOrganizationTreeNode>> builder) {
        long version = readVersion();
        LocalEntry local = localEntries.get(variant);
        if (isUsable(local, version)) {
            return local.nodes();
        }

        synchronized (buildLocks.computeIfAbsent(variant, key -> new Object())) {
            local = localEntries.get(variant);
            if (isUsable(local, version)) {
                return local.nodes();
            }
            List<LegacyOrganizationTreeNode> nodes = version == UNKNOWN_VERSION
                ? build(builder)
                : loadOrBuildShared(variant, version, builder);
            localEntries.put(variant, new LocalEntry(version, System.currentTimeMillis(), nodes));
            return nodes;
        }
    }

    /** Gọi khi tổ chức/người dùng thay đổi (sau commit). */
    public void invalidate() {
        localEntries.clear();
        if (store == null) {
            return;
        }
        try {
            long newVersion = store.bumpVersion();
            log.debug("Organization tree cache invalidated, version={}", newVersion);
        } catch (Exception ex) {
            log.warn("Organization tree cache: cannot bump version in Redis ({}). Local cache cleared only.", ex.getMessage());
        }
    }

    private List<LegacyOrganizationTreeNode> loadOrBuildShared(
            String variant, long version, Supplier<List<LegacyOrganizationTreeNode>> builder) {
        String dataKey = "tree:v" + version + ":" + variant;
        List<LegacyOrganizationTreeNode> cached = readShared(dataKey);
        if (cached != null) {
            return cached;
        }

        String lockKey = "lock:v" + version + ":" + variant;
        String token = UUID.randomUUID().toString();
        boolean locked = tryLock(lockKey, token);
        if (!locked) {
            cached = waitForOtherBuilder(dataKey);
            if (cached != null) {
                return cached;
            }
            log.warn("Organization tree cache: timed out waiting for {}, building locally", dataKey);
        }
        try {
            long started = System.currentTimeMillis();
            List<LegacyOrganizationTreeNode> nodes = build(builder);
            writeShared(dataKey, nodes);
            log.info("Organization tree cache rebuilt: key={}, rootNodes={}, took={}ms",
                dataKey, nodes.size(), System.currentTimeMillis() - started);
            return nodes;
        } finally {
            if (locked) {
                unlock(lockKey, token);
            }
        }
    }

    private List<LegacyOrganizationTreeNode> build(Supplier<List<LegacyOrganizationTreeNode>> builder) {
        return buildTransaction == null ? builder.get() : buildTransaction.execute(status -> builder.get());
    }

    private List<LegacyOrganizationTreeNode> waitForOtherBuilder(String dataKey) {
        long deadline = System.currentTimeMillis() + WAIT_FOR_OTHER_BUILDER.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
            List<LegacyOrganizationTreeNode> cached = readShared(dataKey);
            if (cached != null) {
                return cached;
            }
        }
        return null;
    }

    private boolean isUsable(LocalEntry entry, long version) {
        if (entry == null) {
            return false;
        }
        long age = System.currentTimeMillis() - entry.loadedAtMillis();
        if (version == UNKNOWN_VERSION) {
            return age < LOCAL_ONLY_TTL.toMillis();
        }
        // Giới hạn tuổi phòng khi Redis bị xoá sạch (phiên bản quay về 0 trùng phiên bản cũ trong JVM).
        return entry.version() == version && age < TREE_TTL.toMillis();
    }

    private long readVersion() {
        if (store == null) {
            return UNKNOWN_VERSION;
        }
        try {
            return store.currentVersion();
        } catch (Exception ex) {
            log.warn("Organization tree cache: Redis unavailable ({}), using local cache only", ex.getMessage());
            return UNKNOWN_VERSION;
        }
    }

    private List<LegacyOrganizationTreeNode> readShared(String key) {
        try {
            byte[] bytes = store.get(key);
            return bytes == null ? null : decode(bytes);
        } catch (Exception ex) {
            log.warn("Organization tree cache: cannot read {} ({})", key, ex.getMessage());
            return null;
        }
    }

    private void writeShared(String key, List<LegacyOrganizationTreeNode> nodes) {
        try {
            store.put(key, encode(nodes), TREE_TTL);
        } catch (Exception ex) {
            log.warn("Organization tree cache: cannot write {} ({})", key, ex.getMessage());
        }
    }

    private boolean tryLock(String key, String token) {
        try {
            return store.tryLock(key, token, BUILD_LOCK_TTL);
        } catch (Exception ex) {
            // Không lấy được khoá vì Redis lỗi -> coi như đã giữ khoá, tự dựng (đã có khoá JVM).
            return true;
        }
    }

    private void unlock(String key, String token) {
        try {
            store.unlock(key, token);
        } catch (Exception ex) {
            log.debug("Organization tree cache: cannot unlock {} ({})", key, ex.getMessage());
        }
    }

    byte[] encode(List<LegacyOrganizationTreeNode> nodes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            objectMapper.writeValue(gzip, nodes);
        }
        return out.toByteArray();
    }

    List<LegacyOrganizationTreeNode> decode(byte[] bytes) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return objectMapper.readValue(gzip, TREE_TYPE);
        }
    }

    private record LocalEntry(long version, long loadedAtMillis, List<LegacyOrganizationTreeNode> nodes) {
    }
}
