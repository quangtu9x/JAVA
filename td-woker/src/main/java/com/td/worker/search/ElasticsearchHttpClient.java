package com.td.worker.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client REST tối giản cho Elasticsearch 8.x (JDK HttpClient — không thêm thư viện). Nhiều node: thử
 * lần lượt theo thứ tự cấu hình khi lỗi kết nối.
 *
 * <p>Lỗi KẾT NỐI ném {@link ElasticsearchUnavailableException} — người gọi giữ nguyên hàng đợi (không
 * tính lần thử) và thử lại chu kỳ sau. Lỗi theo TỪNG dòng trong bulk trả về qua {@link BulkItemResult}.</p>
 */
public class ElasticsearchHttpClient {

    private final List<String> baseUris;
    private final String authorization;
    private final HttpClient http;
    private final java.time.Duration requestTimeout;
    private final ObjectMapper objectMapper;

    public ElasticsearchHttpClient(SearchSyncProperties props, ObjectMapper objectMapper) {
        this.baseUris = props.getUris().stream()
            .filter(u -> u != null && !u.isBlank())
            .map(u -> u.trim().replaceAll("/+$", ""))
            .toList();
        this.authorization = buildAuthorization(props);
        this.requestTimeout = props.getRequestTimeout();
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder().connectTimeout(props.getConnectTimeout()).build();
    }

    private static String buildAuthorization(SearchSyncProperties props) {
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            return "ApiKey " + props.getApiKey().trim();
        }
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            String raw = props.getUsername() + ":" + (props.getPassword() == null ? "" : props.getPassword());
            return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    /** Tên các index vật lý alias đang trỏ tới (rỗng nếu alias chưa có). */
    public Set<String> aliasTargets(String alias) {
        Response response = send("GET", "/_alias/" + alias, null, null);
        if (response.status() == 404) {
            return Set.of();
        }
        response.requireSuccess("GET alias " + alias);
        JsonNode body = response.json(objectMapper);
        List<String> names = new ArrayList<>();
        body.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    /** Tạo index; đã tồn tại thì bỏ qua (nhiều instance worker có thể cùng tạo). */
    public void createIndex(String index, JsonNode body) {
        Response response = send("PUT", "/" + index, body.toString(), "application/json");
        if (response.status() == 400 && response.body().contains("resource_already_exists_exception")) {
            return;
        }
        response.requireSuccess("PUT index " + index);
    }

    public void deleteIndex(String index) {
        Response response = send("DELETE", "/" + index, null, null);
        if (response.status() != 404) {
            response.requireSuccess("DELETE index " + index);
        }
    }

    /** Trỏ alias sang {@code index} và gỡ khỏi {@code oldIndices} trong 1 thao tác nguyên tử. */
    public void pointAlias(String alias, String index, Collection<String> oldIndices) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode actions = root.putArray("actions");
        for (String old : oldIndices) {
            if (!old.equals(index)) {
                actions.addObject().putObject("remove").put("index", old).put("alias", alias);
            }
        }
        actions.addObject().putObject("add").put("index", index).put("alias", alias).put("is_write_index", true);
        send("POST", "/_aliases", root.toString(), "application/json").requireSuccess("POST _aliases " + alias);
    }

    public long count(String alias) {
        Response response = send("GET", "/" + alias + "/_count", null, null);
        response.requireSuccess("GET _count " + alias);
        return response.json(objectMapper).path("count").asLong();
    }

    /** Trang id kế tiếp (sắp theo trường "id"), dùng cho đối soát. */
    public List<String> scanIds(String alias, String searchAfter, int size) {
        ObjectNode query = objectMapper.createObjectNode();
        query.put("size", size);
        query.put("_source", false);
        query.putArray("sort").addObject().put("id", "asc");
        query.putArray("docvalue_fields").add("id");
        if (searchAfter != null) {
            query.putArray("search_after").add(searchAfter);
        }
        Response response = send("POST", "/" + alias + "/_search", query.toString(), "application/json");
        response.requireSuccess("POST _search " + alias);
        List<String> ids = new ArrayList<>();
        for (JsonNode hit : response.json(objectMapper).path("hits").path("hits")) {
            ids.add(hit.path("_id").asText());
        }
        return ids;
    }

    /**
     * Gửi bulk; {@code require_alias=true} để KHÔNG BAO GIỜ tự sinh index tên trùng alias với mapping đoán
     * (khi alias bị xoá ngoài ý muốn) — lỗi đó trả về theo từng dòng để worker tạo lại index.
     */
    public List<BulkItemResult> bulk(String ndjson) {
        Response response = send("POST", "/_bulk?require_alias=true", ndjson, "application/x-ndjson");
        response.requireSuccess("POST _bulk");
        return parseBulkResponse(response.json(objectMapper));
    }

    static List<BulkItemResult> parseBulkResponse(JsonNode body) {
        List<BulkItemResult> results = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            Map.Entry<String, JsonNode> entry = item.fields().next();
            String action = entry.getKey();
            JsonNode detail = entry.getValue();
            int status = detail.path("status").asInt();
            JsonNode error = detail.path("error");
            String errorType = error.path("type").asText("");
            String reason = error.isMissingNode() ? "" : errorType + ": " + error.path("reason").asText("");
            results.add(new BulkItemResult(detail.path("_id").asText(), action, status, errorType, reason));
        }
        return results;
    }

    private Response send(String method, String path, String body, String contentType) {
        if (baseUris.isEmpty()) {
            throw new ElasticsearchUnavailableException("Chưa cấu hình địa chỉ Elasticsearch", null);
        }
        IOException last = null;
        for (String base : baseUris) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(requestTimeout)
                .method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (contentType != null) {
                builder.header("Content-Type", contentType);
            }
            if (authorization != null) {
                builder.header("Authorization", authorization);
            }
            try {
                HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 500 || response.statusCode() == 429) {
                    last = new IOException("HTTP " + response.statusCode() + " từ " + base + ": " + abbreviate(response.body()));
                    continue;
                }
                return new Response(response.statusCode(), response.body());
            } catch (IOException ex) {
                last = ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ElasticsearchUnavailableException("Bị ngắt khi gọi Elasticsearch", ex);
            }
        }
        throw new ElasticsearchUnavailableException(
            "Không gọi được Elasticsearch (" + method + " " + path + "): " + (last == null ? "" : last.getMessage()), last);
    }

    static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    record Response(int status, String body) {
        void requireSuccess(String action) {
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(action + " thất bại HTTP " + status + ": " + abbreviate(body));
            }
        }

        JsonNode json(ObjectMapper mapper) {
            try {
                return mapper.readTree(body);
            } catch (IOException ex) {
                throw new IllegalStateException("Phản hồi Elasticsearch không phải JSON: " + abbreviate(body), ex);
            }
        }
    }

    /** Kết quả 1 dòng bulk. */
    public record BulkItemResult(String id, String action, int status, String errorType, String error) {

        /**
         * Thành công theo nghĩa đồng bộ: ghi được; 409 = ES đã có bản MỚI HƠN (version lớn hơn) → bản này
         * đã lỗi thời, coi như xong; xoá mà không thấy (404) cũng là xong.
         */
        public boolean isDone() {
            if (status >= 200 && status < 300) {
                return true;
            }
            if (status == 409 && "version_conflict_engine_exception".equals(errorType)) {
                return true;
            }
            return "delete".equals(action) && status == 404 && errorType.isEmpty();
        }

        public boolean isMissingAlias() {
            return "index_not_found_exception".equals(errorType);
        }
    }

    /** Không kết nối được ES (mạng, node chết, 5xx, 429). */
    public static class ElasticsearchUnavailableException extends RuntimeException {
        public ElasticsearchUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
