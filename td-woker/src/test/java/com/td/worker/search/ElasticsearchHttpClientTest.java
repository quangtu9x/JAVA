package com.td.worker.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.worker.search.ElasticsearchHttpClient.BulkItemResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchHttpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseBulkResponse_shouldClassifyEachItem() throws Exception {
        String body = """
            {"errors":true,"items":[
              {"index":{"_id":"a","status":201}},
              {"index":{"_id":"b","status":409,"error":{"type":"version_conflict_engine_exception","reason":"newer"}}},
              {"delete":{"_id":"c","status":404,"result":"not_found"}},
              {"index":{"_id":"d","status":404,"error":{"type":"index_not_found_exception","reason":"no such index"}}},
              {"index":{"_id":"e","status":400,"error":{"type":"mapper_parsing_exception","reason":"bad"}}}
            ]}
            """;

        List<BulkItemResult> results = ElasticsearchHttpClient.parseBulkResponse(objectMapper.readTree(body));

        assertThat(results).extracting(BulkItemResult::id).containsExactly("a", "b", "c", "d", "e");
        // ghi được / ES đã có bản mới hơn / xoá mà không còn => xong
        assertThat(results.get(0).isDone()).isTrue();
        assertThat(results.get(1).isDone()).isTrue();
        assertThat(results.get(2).isDone()).isTrue();
        // alias mất => chưa xong, cần tạo lại index
        assertThat(results.get(3).isDone()).isFalse();
        assertThat(results.get(3).isMissingAlias()).isTrue();
        // lỗi dữ liệu => chưa xong, giữ hàng đợi
        assertThat(results.get(4).isDone()).isFalse();
        assertThat(results.get(4).error()).contains("mapper_parsing_exception");
    }

    // ES không chạy => lỗi kết nối riêng để worker rollback, giữ nguyên hàng đợi.
    @Test
    void unreachableCluster_shouldThrowUnavailable() {
        SearchSyncProperties props = new SearchSyncProperties();
        props.setUris(List.of("http://127.0.0.1:1"));
        props.setConnectTimeout(java.time.Duration.ofMillis(500));
        ElasticsearchHttpClient client = new ElasticsearchHttpClient(props, objectMapper);

        assertThatThrownBy(() -> client.count("hscv-h05-vbdi"))
            .isInstanceOf(ElasticsearchHttpClient.ElasticsearchUnavailableException.class);
    }

    @Test
    void noUris_shouldThrowUnavailable() {
        SearchSyncProperties props = new SearchSyncProperties();
        props.setUris(List.of(""));
        ElasticsearchHttpClient client = new ElasticsearchHttpClient(props, objectMapper);

        assertThat(props.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.aliasTargets("x"))
            .isInstanceOf(ElasticsearchHttpClient.ElasticsearchUnavailableException.class);
    }
}
