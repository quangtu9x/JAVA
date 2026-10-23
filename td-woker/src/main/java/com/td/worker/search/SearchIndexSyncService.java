package com.td.worker.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.worker.infrastructure.tenant.TenantDataSourceManager;
import com.td.worker.infrastructure.tenant.TenantRecord;
import com.td.worker.infrastructure.tenant.TenantRegistry;
import com.td.worker.search.ElasticsearchHttpClient.BulkItemResult;
import com.td.worker.search.ElasticsearchHttpClient.ElasticsearchUnavailableException;
import com.td.worker.search.SearchIndexQueueStore.QueueItem;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Đồng bộ {@code search_index_queue} (mọi tenant) → Elasticsearch. Xem {@code docs/21-dong-bo-elasticsearch.md}.
 *
 * <p><b>Đảm bảo khớp dữ liệu</b>:</p>
 * <ul>
 *   <li>Hàng đợi do TRIGGER ghi trong cùng transaction với dữ liệu → không sót thay đổi nào, kể cả ghi
 *   bằng JDBC/SQL tay.</li>
 *   <li>Luôn đọc TRẠNG THÁI HIỆN TẠI của dòng (còn → upsert, mất → xoá), nên xử lý lặp/nhiều lần vẫn đúng.</li>
 *   <li>Version external = id hàng đợi (tăng dần): bản đọc cũ gửi muộn bị ES từ chối (409) — không đè bản mới.</li>
 *   <li>Dòng hàng đợi chỉ bị xoá SAU KHI ES xác nhận; lỗi → giữ lại, thử lại có lùi lịch, không bao giờ bỏ.</li>
 *   <li>ES mất kết nối → rollback, hàng đợi giữ nguyên (không tính lần thử) và tự bù khi ES trở lại.</li>
 *   <li>Đối soát định kỳ số lượng DB ↔ ES, lệch thì tự đưa lại vào hàng đợi.</li>
 * </ul>
 */
@Slf4j
public class SearchIndexSyncService {

    private static final Duration COLUMN_CACHE_TTL = Duration.ofMinutes(10);
    private static final int SCAN_PAGE_SIZE = 1000;
    // Dòng đang chờ thử lại: không đánh thức sớm hơn khoảng này (tránh vòng lặp dày khi next_try_at sát now()).
    private static final Duration MIN_RETRY_WAKE = Duration.ofSeconds(1);

    private final SearchSyncProperties props;
    private final ElasticsearchHttpClient client;
    private final SearchIndexQueueStore store;
    private final SearchDocumentMapper mapper;
    private final TenantRegistry tenantRegistry;
    private final TenantDataSourceManager dataSources;
    private final ObjectMapper objectMapper;
    private final FileExtractStore fileExtractStore = new FileExtractStore();
    /** tenant → bảng file_content_extracts đã có (migration V1.0.74). */
    private final Map<String, Boolean> extractsTableByTenant = new ConcurrentHashMap<>();

    /** (tenant|entity) đã kiểm tra alias trỏ đúng index phiên bản hiện tại. */
    private final Set<String> ensuredIndices = ConcurrentHashMap.newKeySet();
    private final Map<String, CachedColumns> columnCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastReconcile = new ConcurrentHashMap<>();
    private final Map<String, long[]> queueStatsByTenant = new ConcurrentHashMap<>();

    private final Counter indexedCounter;
    private final Counter deletedCounter;
    private final Counter failedCounter;
    private final AtomicLong queueTotal = new AtomicLong();
    private final AtomicLong queueFailing = new AtomicLong();
    private volatile Instant lastCycleOkAt;
    /** Lỗi gần nhất theo tenant — xoá khi tenant đó chạy lại thành công. */
    private final Map<String, String> tenantErrors = new ConcurrentHashMap<>();

    public SearchIndexSyncService(SearchSyncProperties props, ElasticsearchHttpClient client,
                                  SearchIndexQueueStore store, SearchDocumentMapper mapper,
                                  TenantRegistry tenantRegistry, TenantDataSourceManager dataSources,
                                  ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.props = props;
        this.client = client;
        this.store = store;
        this.mapper = mapper;
        this.tenantRegistry = tenantRegistry;
        this.dataSources = dataSources;
        this.objectMapper = objectMapper;
        props.getEntities().values().forEach(SearchIndexNaming::requireSqlIdentifier);
        this.indexedCounter = Counter.builder("worker_search_sync_indexed_total").register(meterRegistry);
        this.deletedCounter = Counter.builder("worker_search_sync_deleted_total").register(meterRegistry);
        this.failedCounter = Counter.builder("worker_search_sync_failed_total").register(meterRegistry);
        Gauge.builder("worker_search_sync_queue_size", queueTotal, AtomicLong::get).register(meterRegistry);
        Gauge.builder("worker_search_sync_queue_failing", queueFailing, AtomicLong::get).register(meterRegistry);
    }

    /**
     * 1 lượt cho 1 tenant (gọi bởi {@link SearchSyncCoordinator}, mỗi tenant tối đa 1 luồng). Trả về sau bao lâu
     * cần chạy lại tenant này: {@link Duration#ZERO} = hết lượt mà còn việc (xếp lại cuối hàng), khoảng thời gian
     * = tới hạn dòng đang chờ thử lại, {@code null} = hàng đợi rỗng (chờ thông báo kế tiếp).
     *
     * @param sweep lượt quét dự phòng — thêm đối soát + số liệu hàng đợi (truy vấn đếm, không chạy mỗi lượt)
     * @throws ElasticsearchUnavailableException ES không kết nối được (hàng đợi giữ nguyên)
     */
    public Duration syncTenant(String tenantId, boolean sweep) throws SQLException {
        DataSource dataSource = dataSources.getDataSource(tenantId).orElse(null);
        if (dataSource == null) {
            return null;
        }
        return syncTenant(tenantId, dataSource, sweep);
    }

    Duration syncTenant(String tenantId, DataSource dataSource, boolean sweep) throws SQLException {
        try {
            ensureIndices(tenantId, dataSource);
            boolean moreWork = false;
            for (int i = 0; i < props.getMaxBatchesPerTenantPerCycle(); i++) {
                moreWork = processBatch(tenantId, dataSource) >= props.getBatchSize();
                if (!moreWork) {
                    break;
                }
            }
            if (sweep) {
                reconcileIfDue(tenantId, dataSource);
                try (Connection connection = dataSource.getConnection()) {
                    queueStatsByTenant.put(tenantId, store.queueStats(connection));
                }
                refreshQueueGauges();
            }
            tenantErrors.remove(tenantId);
            lastCycleOkAt = Instant.now();
            if (moreWork) {
                return Duration.ZERO;
            }
            try (Connection connection = dataSource.getConnection()) {
                Instant nextDue = store.nextDueAt(connection);
                if (nextDue == null) {
                    return null;
                }
                Duration wait = Duration.between(Instant.now(), nextDue);
                return wait.compareTo(MIN_RETRY_WAKE) < 0 ? MIN_RETRY_WAKE : wait;
            }
        } catch (ElasticsearchUnavailableException ex) {
            ensuredIndices.clear();
            tenantErrors.put(tenantId, ex.getMessage());
            throw ex;
        } catch (SQLException | RuntimeException ex) {
            tenantErrors.put(tenantId, ex.getMessage());
            throw ex;
        }
    }

    // ------------------------------------------------------------------ lô hàng đợi

    int processBatch(String tenantId, DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<QueueItem> items = store.claim(connection, props.getBatchSize());
                if (items.isEmpty()) {
                    connection.commit();
                    return 0;
                }
                BatchOutcome outcome = indexBatch(tenantId, connection, items);
                store.delete(connection, outcome.done());
                for (Map.Entry<String, List<Long>> failed : outcome.failedByError().entrySet()) {
                    store.markRetry(connection, failed.getValue(), failed.getKey(),
                        props.getRetryInitialBackoff(), props.getRetryMaxBackoff());
                }
                connection.commit();
                return items.size();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private BatchOutcome indexBatch(String tenantId, Connection connection, List<QueueItem> items) throws SQLException {
        // Gom theo (entity, id): 1 thao tác ES / document, version = id hàng đợi lớn nhất của nó.
        Map<String, Map<UUID, List<QueueItem>>> byEntity = new LinkedHashMap<>();
        for (QueueItem item : items) {
            byEntity.computeIfAbsent(item.entityType(), k -> new LinkedHashMap<>())
                .computeIfAbsent(item.entityId(), k -> new ArrayList<>()).add(item);
        }

        List<Long> done = new ArrayList<>();
        Map<String, List<Long>> failedByError = new HashMap<>();
        StringBuilder bulk = new StringBuilder();
        List<List<Long>> actionQueueIds = new ArrayList<>();
        List<String> actionEntity = new ArrayList<>();

        for (Map.Entry<String, Map<UUID, List<QueueItem>>> entityGroup : byEntity.entrySet()) {
            String entityType = entityGroup.getKey();
            String table = props.getEntities().get(entityType);
            if (table == null) {
                failedByError.computeIfAbsent("entity_type '" + entityType + "' chưa khai ở worker.search-sync.entities",
                    k -> new ArrayList<>()).addAll(queueIds(entityGroup.getValue().values()));
                continue;
            }
            String alias = SearchIndexNaming.alias(props.getIndexPrefix(), tenantId, entityType);
            Map<String, String> columns = columnTypes(tenantId, connection, table);
            Map<UUID, String> rows = store.loadRows(connection, table, entityGroup.getValue().keySet());
            Map<UUID, String> filesText = filesText(tenantId, connection, entityGroup.getValue().keySet());
            for (Map.Entry<UUID, List<QueueItem>> entry : entityGroup.getValue().entrySet()) {
                UUID id = entry.getKey();
                List<Long> ids = queueIds(List.of(entry.getValue()));
                long version = ids.stream().mapToLong(Long::longValue).max().orElse(0);
                String row = rows.get(id);
                try {
                    if (row == null) {
                        bulk.append(action("delete", alias, id, version)).append('\n');
                    } else {
                        ObjectNode doc = mapper.toDocument(tenantId, entityType, row, columns, filesText.get(id));
                        bulk.append(action("index", alias, id, version)).append('\n')
                            .append(objectMapper.writeValueAsString(doc)).append('\n');
                    }
                    actionQueueIds.add(ids);
                    actionEntity.add(tenantId + "|" + entityType);
                } catch (Exception ex) {
                    failedByError.computeIfAbsent("Không dựng được document: " + ex.getMessage(),
                        k -> new ArrayList<>()).addAll(ids);
                }
            }
        }

        if (!actionQueueIds.isEmpty()) {
            List<BulkItemResult> results = client.bulk(bulk.toString());
            for (int i = 0; i < actionQueueIds.size(); i++) {
                BulkItemResult result = i < results.size() ? results.get(i) : null;
                List<Long> ids = actionQueueIds.get(i);
                if (result != null && result.isDone()) {
                    done.addAll(ids);
                    if ("delete".equals(result.action())) {
                        deletedCounter.increment();
                    } else {
                        indexedCounter.increment();
                    }
                } else {
                    if (result != null && result.isMissingAlias()) {
                        ensuredIndices.remove(actionEntity.get(i));
                    }
                    failedCounter.increment();
                    String error = result == null ? "Thiếu kết quả bulk" : "HTTP " + result.status() + " " + result.error();
                    failedByError.computeIfAbsent(error, k -> new ArrayList<>()).addAll(ids);
                }
            }
        }
        if (!failedByError.isEmpty()) {
            log.warn("Elasticsearch tenant {}: {} dòng hàng đợi lỗi, sẽ thử lại. Lỗi: {}",
                tenantId, failedByError.values().stream().mapToInt(List::size).sum(), failedByError.keySet());
        }
        return new BatchOutcome(done, failedByError);
    }

    private String action(String type, String alias, UUID id, long version) {
        ObjectNode meta = objectMapper.createObjectNode();
        meta.putObject(type)
            .put("_index", alias)
            .put("_id", id.toString())
            .put("version", version)
            .put("version_type", "external_gte");
        return meta.toString();
    }

    private static List<Long> queueIds(java.util.Collection<List<QueueItem>> groups) {
        List<Long> ids = new ArrayList<>();
        for (List<QueueItem> group : groups) {
            for (QueueItem item : group) {
                ids.add(item.id());
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------ index / alias

    void ensureIndices(String tenantId, DataSource dataSource) throws SQLException {
        for (Map.Entry<String, String> entity : props.getEntities().entrySet()) {
            String key = tenantId + "|" + entity.getKey();
            if (ensuredIndices.contains(key)) {
                continue;
            }
            String alias = SearchIndexNaming.alias(props.getIndexPrefix(), tenantId, entity.getKey());
            String physical = SearchIndexNaming.physical(alias, props.getMappingVersion());
            Set<String> targets = client.aliasTargets(alias);
            if (targets.equals(Set.of(physical))) {
                ensuredIndices.add(key);
                continue;
            }
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    if (!store.tryAdvisoryLock(connection, "search-index:" + entity.getKey())) {
                        connection.rollback();
                        continue; // instance khác đang tạo — kiểm tra lại chu kỳ sau
                    }
                    Map<String, String> columns = store.columnTypes(connection, entity.getValue());
                    if (columns.isEmpty()) {
                        log.warn("Elasticsearch: bảng {} không tồn tại ở tenant {}, bỏ qua entity {}",
                            entity.getValue(), tenantId, entity.getKey());
                        connection.rollback();
                        continue;
                    }
                    client.createIndex(physical, mapper.indexDefinition(columns));
                    client.pointAlias(alias, physical, targets);
                    boolean firstTime = targets.isEmpty();
                    if (!firstTime || props.isBootstrapOnMissingIndex()) {
                        int queued = store.enqueueAll(connection, entity.getKey(), entity.getValue());
                        log.info("Elasticsearch: {} index {} (alias {}), đưa {} dòng vào hàng đợi để nạp {}",
                            firstTime ? "tạo mới" : "đổi mapping sang", physical, alias, queued,
                            firstTime ? "lần đầu" : "lại toàn bộ");
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException ex) {
                    connection.rollback();
                    throw ex;
                }
            }
            if (props.isDeleteOldIndices()) {
                for (String old : targets) {
                    if (!old.equals(physical)) {
                        client.deleteIndex(old);
                    }
                }
            }
            ensuredIndices.add(key);
        }
    }

    // ------------------------------------------------------------------ đối soát

    void reconcileIfDue(String tenantId, DataSource dataSource) throws SQLException {
        Duration interval = props.getReconcileInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }
        Instant last = lastReconcile.get(tenantId);
        if (last != null && last.plus(interval).isAfter(Instant.now())) {
            return;
        }
        lastReconcile.put(tenantId, Instant.now());
        for (Map.Entry<String, String> entity : props.getEntities().entrySet()) {
            reconcile(tenantId, dataSource, entity.getKey(), entity.getValue());
        }
    }

    void reconcile(String tenantId, DataSource dataSource, String entityType, String table) throws SQLException {
        String alias = SearchIndexNaming.alias(props.getIndexPrefix(), tenantId, entityType);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!store.tryAdvisoryLock(connection, "search-reconcile:" + entityType)
                        || store.pending(connection, entityType) > 0) {
                    connection.rollback();
                    return; // còn thay đổi chưa xử lý → số lượng chưa so được
                }
                long dbCount = store.countRows(connection, table);
                long esCount = client.count(alias);
                if (dbCount == esCount) {
                    connection.rollback();
                    return;
                }
                log.warn("Đối soát Elasticsearch {}: DB={} ES={} — đưa lại vào hàng đợi", alias, dbCount, esCount);
                if (esCount > dbCount) {
                    // ES thừa: tìm id không còn trong DB → hàng đợi (worker sẽ xoá khỏi ES).
                    String after = null;
                    while (true) {
                        List<String> page = client.scanIds(alias, after, SCAN_PAGE_SIZE);
                        if (page.isEmpty()) {
                            break;
                        }
                        List<UUID> ids = page.stream().map(UUID::fromString).toList();
                        Set<UUID> existing = store.existingIds(connection, table, ids);
                        store.enqueueIds(connection, entityType, ids.stream().filter(id -> !existing.contains(id)).toList());
                        after = page.get(page.size() - 1);
                    }
                }
                if (dbCount > esCount) {
                    // ES thiếu: nạp lại toàn bộ bảng — upsert có version nên chạy lại không sai lệch. (Lệch cả
                    // 2 chiều: lần này dọn bản thừa, lần đối soát sau sẽ bù phần thiếu.)
                    store.enqueueAll(connection, entityType, table);
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    // ------------------------------------------------------------------ phụ trợ

    /** Tenant cần đồng bộ (active, lọc theo worker.search-sync.tenants). */
    public List<String> tenantIds() {
        // "${WORKER_SEARCH_SYNC_TENANTS:}" rỗng bind thành [""] chứ không phải [] — bỏ phần tử trống.
        List<String> only = props.getTenants() == null ? List.of()
            : props.getTenants().stream().filter(t -> t != null && !t.isBlank()).map(String::trim).toList();
        return tenantRegistry.all().values().stream()
            .filter(TenantRecord::active)
            .map(TenantRecord::tenantId)
            .filter(id -> only.isEmpty() || only.contains(id))
            .toList();
    }

    // Nội dung tệp đính kèm đã trích (file_content_extracts) → trường files_text (docs/architecture/14).
    private Map<UUID, String> filesText(String tenantId, Connection connection, java.util.Collection<UUID> ids) throws SQLException {
        Boolean exists = extractsTableByTenant.get(tenantId);
        if (exists == null || !exists) {
            exists = fileExtractStore.extractsTableExists(connection);
            extractsTableByTenant.put(tenantId, exists);
        }
        return exists ? fileExtractStore.filesText(connection, ids, props.getFileExtract().getMaxIndexedChars()) : Map.of();
    }

    private Map<String, String> columnTypes(String tenantId, Connection connection, String table) throws SQLException {
        String key = tenantId + "|" + table;
        CachedColumns cached = columnCache.get(key);
        if (cached != null && cached.loadedAt().plus(COLUMN_CACHE_TTL).isAfter(Instant.now())) {
            return cached.types();
        }
        Map<String, String> types = store.columnTypes(connection, table);
        columnCache.put(key, new CachedColumns(types, Instant.now()));
        return types;
    }

    private void refreshQueueGauges() {
        long total = 0;
        long failing = 0;
        for (long[] stats : queueStatsByTenant.values()) {
            total += stats[0];
            failing += stats[1];
        }
        queueTotal.set(total);
        queueFailing.set(failing);
    }

    public Instant lastCycleOkAt() {
        return lastCycleOkAt;
    }

    public String lastError() {
        return tenantErrors.entrySet().stream().findFirst()
            .map(e -> e.getKey() + ": " + e.getValue()).orElse(null);
    }

    public long queueTotal() {
        return queueTotal.get();
    }

    public long queueFailing() {
        return queueFailing.get();
    }

    private record CachedColumns(Map<String, String> types, Instant loadedAt) {
    }

    record BatchOutcome(List<Long> done, Map<String, List<Long>> failedByError) {
    }
}
