package com.td.infrastructure.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.application.search.AdvancedSearchIndex;
import com.td.application.search.AdvancedSearchQuery;
import com.td.application.search.AdvancedSearchQuery.AllOf;
import com.td.application.search.AdvancedSearchQuery.AnyOf;
import com.td.application.search.AdvancedSearchQuery.Condition;
import com.td.application.search.AdvancedSearchQuery.DateRange;
import com.td.application.search.AdvancedSearchQuery.Exists;
import com.td.application.search.AdvancedSearchQuery.Match;
import com.td.application.search.AdvancedSearchQuery.Not;
import com.td.application.search.AdvancedSearchQuery.Source;
import com.td.application.search.AdvancedSearchQuery.Terms;
import com.td.infrastructure.tenant.TenantContext;
import com.td.infrastructure.tenant.TenantProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 17/10/2026 — {@link AdvancedSearchIndex} trên Elasticsearch: dựng truy vấn từ {@link AdvancedSearchQuery} lên
 * các trường chuẩn hoá do td-woker sinh ({@code norm_dyn}/{@code norm_col}/{@code date_dyn}/{@code date_col}/
 * {@code kw.*}) và chỉ lấy {@code _id} + tổng số (dữ liệu hiển thị vẫn nạp từ DB).
 *
 * <p>Chỉ dùng index khi: bật cấu hình + bật module, alias {@code {prefix}-{tenant}-{entity}} tồn tại, và worker
 * KHÔNG còn dòng nạp lần đầu/nạp lại ({@code operation='R'}) của entity đó trong {@code search_index_queue} — đang
 * nạp thì kết quả ES thiếu, phải chạy SQL. Mọi lỗi → {@link Optional#empty()} (người gọi chạy SQL).</p>
 */
@Slf4j
@Component
public class ElasticsearchAdvancedSearchIndex implements AdvancedSearchIndex {

    private final AdvancedSearchProperties props;
    private final TenantProperties tenantProperties;
    private final ObjectProvider<RestClient> restClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Readiness> readiness = new ConcurrentHashMap<>();
    private final AtomicLong lastErrorLog = new AtomicLong();

    public ElasticsearchAdvancedSearchIndex(AdvancedSearchProperties props, TenantProperties tenantProperties,
                                            ObjectProvider<RestClient> restClient, DataSource dataSource) {
        this.props = props;
        this.tenantProperties = tenantProperties;
        this.restClient = restClient;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<Hits> search(String module, String entity, AdvancedSearchQuery query, int pageIndex, int pageSize) {
        if (!props.isModuleEnabled(module)) {
            return Optional.empty();
        }
        RestClient client = restClient.getIfAvailable();
        if (client == null) {
            return Optional.empty();
        }
        String tenant = Optional.ofNullable(TenantContext.getCurrentTenant()).orElse(tenantProperties.getDefaultTenantId());
        String alias = alias(props.getIndexPrefix(), tenant, entity);
        try {
            if (!isReady(client, tenant, alias, entity)) {
                return Optional.empty();
            }
            Request request = new Request("POST", "/" + alias + "/_search");
            request.addParameter("filter_path", "hits.total.value,hits.hits._id");
            request.setJsonEntity(buildSearchBody(query, pageIndex, pageSize).toString());
            Response response = client.performRequest(request);
            JsonNode body = objectMapper.readTree(EntityUtils.toString(response.getEntity()));
            List<UUID> ids = new ArrayList<>();
            for (JsonNode hit : body.path("hits").path("hits")) {
                ids.add(UUID.fromString(hit.path("_id").asText()));
            }
            return Optional.of(new Hits(ids, body.path("hits").path("total").path("value").asLong()));
        } catch (Exception ex) {
            logError("Tìm kiếm Elasticsearch " + alias + " lỗi, chạy SQL thay thế: " + ex.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ sẵn sàng

    private boolean isReady(RestClient client, String tenant, String alias, String entity) throws Exception {
        String key = tenant + "|" + entity;
        Readiness cached = readiness.get(key);
        if (cached != null && cached.checkedAt().plus(props.getReadinessCacheTtl()).isAfter(Instant.now())) {
            return cached.ready();
        }
        boolean ready = aliasExists(client, alias) && !bootstrapPending(entity);
        readiness.put(key, new Readiness(ready, Instant.now()));
        if (!ready) {
            log.info("Index tìm kiếm {} chưa sẵn sàng (chưa có hoặc worker đang nạp) — tìm kiếm {} chạy SQL", alias, entity);
        }
        return ready;
    }

    private boolean aliasExists(RestClient client, String alias) throws Exception {
        try {
            return client.performRequest(new Request("HEAD", "/_alias/" + alias)).getStatusLine().getStatusCode() == 200;
        } catch (ResponseException ex) {
            return false;
        }
    }

    private boolean bootstrapPending(String entity) {
        Boolean pending = jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM search_index_queue WHERE entity_type = ? AND operation = 'R')",
            Boolean.class, entity);
        return Boolean.TRUE.equals(pending);
    }

    // ------------------------------------------------------------------ dựng truy vấn

    ObjectNode buildSearchBody(AdvancedSearchQuery query, int pageIndex, int pageSize) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("from", Math.max(0, pageIndex) * Math.max(1, pageSize));
        root.put("size", Math.max(1, pageSize));
        root.put("track_total_hits", true);
        root.put("_source", false);
        ObjectNode bool = root.putObject("query").putObject("bool");
        ArrayNode filter = bool.putArray("filter");
        for (Condition condition : query.filters()) {
            filter.add(toJson(condition));
        }
        if (query.keyword() != null) {
            bool.putArray("must").add(keywordQuery(query.keyword(), query.keywordFields(), props.isIncludeFileContent()));
        }
        ArrayNode sort = root.putArray("sort");
        for (AdvancedSearchQuery.SortField field : query.sort()) {
            switch (field) {
                case CREATED_ON -> sort.add(sortDesc("created_on", "date"));
                case LAST_MODIFIED_ON -> sort.add(sortDesc("last_modified_on", "date"));
                case PROMULGATION_DATE -> sort.add(sortDesc("sort_promulgation_date", "date"));
                case DOCUMENT_NUMBER -> sort.add(sortDesc("sort_document_number", "long"));
            }
        }
        sort.add(sortDesc("id", "keyword"));
        return root;
    }

    private ObjectNode toJson(Condition condition) {
        ObjectNode node = objectMapper.createObjectNode();
        if (condition instanceof Terms terms) {
            ArrayNode values = node.putObject("terms").putArray(field(terms.source(), terms.key(), false));
            terms.values().stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .forEach(values::add);
        } else if (condition instanceof DateRange range) {
            ObjectNode bounds = node.putObject("range").putObject(field(range.source(), range.key(), true));
            if (range.from() != null) {
                bounds.put("gte", range.from().toString());
            }
            if (range.to() != null) {
                bounds.put("lte", range.to().toString());
            }
        } else if (condition instanceof Exists exists) {
            // Cột: có thể là chữ (norm_col) hoặc ngày (date_col) — chấp nhận cả 2.
            ArrayNode should = node.putObject("bool").put("minimum_should_match", 1).putArray("should");
            should.addObject().putObject("exists").put("field", field(exists.source(), exists.key(), false));
            should.addObject().putObject("exists").put("field", field(exists.source(), exists.key(), true));
        } else if (condition instanceof AnyOf anyOf) {
            ObjectNode bool = node.putObject("bool");
            bool.put("minimum_should_match", 1);
            ArrayNode should = bool.putArray("should");
            anyOf.conditions().forEach(c -> should.add(toJson(c)));
        } else if (condition instanceof AllOf allOf) {
            ArrayNode all = node.putObject("bool").putArray("filter");
            allOf.conditions().forEach(c -> all.add(toJson(c)));
        } else if (condition instanceof Not not) {
            node.putObject("bool").putArray("must_not").add(toJson(not.condition()));
        } else if (condition instanceof Match match) {
            return keywordQuery(match.text(), List.of(match.key()), false);
        }
        return node;
    }

    private static String field(Source source, String key, boolean date) {
        String prefix = switch (source) {
            case DYN -> date ? "date_dyn" : "norm_dyn";
            case COL -> date ? "date_col" : "norm_col";
        };
        return prefix + "." + key;
    }

    /**
     * Từ khoá: mọi từ phải có mặt (bỏ dấu, không phân biệt hoa thường) trong các cột được chọn; từ cuối được phép
     * là tiền tố (gõ dở). Không có cột → tìm trên {@code search_text} (toàn văn bản).
     */
    private ObjectNode keywordQuery(String keyword, List<String> keywordFields, boolean includeFileContent) {
        List<String> fields = keywordFields.stream()
            .filter(f -> f != null && f.matches("[A-Za-z0-9_]{1,100}"))
            .map(f -> "kw." + f.toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
        if (fields.isEmpty()) {
            fields = List.of("search_text");
        }
        if (includeFileContent) {
            // Nội dung tệp đính kèm (Tika, chưa OCR) — trường riêng, gộp cùng các cột (docs/architecture/14).
            fields = new java.util.ArrayList<>(fields);
            fields.add("files_text");
        }
        ObjectNode node = objectMapper.createObjectNode();
        ObjectNode bool = node.putObject("bool");
        bool.put("minimum_should_match", 1);
        ArrayNode should = bool.putArray("should");
        ObjectNode crossFields = should.addObject().putObject("multi_match");
        crossFields.put("query", keyword).put("type", "cross_fields").put("operator", "and");
        fields.forEach(crossFields.putArray("fields")::add);
        ObjectNode prefix = should.addObject().putObject("multi_match");
        prefix.put("query", keyword).put("type", "bool_prefix").put("operator", "and");
        fields.forEach(prefix.putArray("fields")::add);
        return node;
    }

    private ObjectNode sortDesc(String field, String unmappedType) {
        ObjectNode node = objectMapper.createObjectNode();
        node.putObject(field).put("order", "desc").put("missing", "_last").put("unmapped_type", unmappedType);
        return node;
    }

    /** PHẢI giống td-woker {@code SearchIndexNaming#alias}. */
    static String alias(String prefix, String tenantId, String entity) {
        return normalize(prefix) + "-" + normalize(tenantId) + "-" + normalize(entity);
    }

    private static String normalize(String value) {
        String cleaned = (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9_-]+", "_");
        while (cleaned.startsWith("-") || cleaned.startsWith("_")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }

    private void logError(String message) {
        long now = System.currentTimeMillis();
        long last = lastErrorLog.get();
        if (now - last > 60_000 && lastErrorLog.compareAndSet(last, now)) {
            log.warn(message);
        }
    }

    private record Readiness(boolean ready, Instant checkedAt) {
    }
}
