package com.td.infrastructure.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.application.search.AdvancedSearchIndex;
import com.td.application.search.AdvancedSearchQuery;
import com.td.application.search.AdvancedSearchQuery.DateRange;
import com.td.application.search.AdvancedSearchQuery.Exists;
import com.td.application.search.AdvancedSearchQuery.Match;
import com.td.application.search.AdvancedSearchQuery.Not;
import com.td.application.search.AdvancedSearchQuery.SortField;
import com.td.application.search.AdvancedSearchQuery.Source;
import com.td.infrastructure.tenant.TenantProperties;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.List;

import static com.td.application.search.AdvancedSearchQuery.anyOf;
import static com.td.application.search.AdvancedSearchQuery.col;
import static com.td.application.search.AdvancedSearchQuery.dyn;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 17/10/2026 — JSON td-web gửi Elasticsearch phải đúng dạng mà td-woker
 * {@code SearchIndexSyncEndToEndTest#advancedSearchQueryContract} đã chứng minh chạy được trên mapping thật.
 */
class ElasticsearchAdvancedSearchIndexTest {

    private ElasticsearchAdvancedSearchIndex index(AdvancedSearchProperties props) {
        return new ElasticsearchAdvancedSearchIndex(props, new TenantProperties(),
            new StaticListableBeanFactory().getBeanProvider(RestClient.class), new DriverManagerDataSource());
    }

    @Test
    void buildSearchBody_shouldUseNormalizedFieldsAndPaging() {
        AdvancedSearchQuery query = new AdvancedSearchQuery()
            .filter(new Not(new Exists(Source.COL, "deleted_on")))
            .filter(col("document_type", "vbden"))
            .filter(anyOf(List.of(dyn("unit_process", " H05.01 "), dyn("identifier_creator", "H05.01"))))
            .filter(new DateRange(Source.DYN, "received_date", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
            .filter(new Match("description", "phần mềm"))
            .keyword("Công văn", List.of("subject", "code", "bad column;drop"))
            .sortDesc(SortField.CREATED_ON, SortField.LAST_MODIFIED_ON);

        JsonNode body = index(new AdvancedSearchProperties()).buildSearchBody(query, 2, 20);

        assertThat(body.path("from").asInt()).isEqualTo(40);
        assertThat(body.path("size").asInt()).isEqualTo(20);
        assertThat(body.path("_source").asBoolean()).isFalse();
        String filters = body.path("query").path("bool").path("filter").toString();
        assertThat(filters)
            .contains("{\"exists\":{\"field\":\"norm_col.deleted_on\"}}")
            .contains("{\"exists\":{\"field\":\"date_col.deleted_on\"}}")
            .contains("{\"terms\":{\"norm_col.document_type\":[\"vbden\"]}}")
            .contains("{\"terms\":{\"norm_dyn.unit_process\":[\"h05.01\"]}}")
            .contains("{\"range\":{\"date_dyn.received_date\":{\"gte\":\"2026-10-01\",\"lte\":\"2026-10-31\"}}}")
            .contains("\"fields\":[\"kw.description\"]");
        String must = body.path("query").path("bool").path("must").toString();
        assertThat(must).contains("\"type\":\"cross_fields\"").contains("\"type\":\"bool_prefix\"")
            .contains("\"kw.subject\"").contains("\"kw.code\"").doesNotContain("drop");
        assertThat(body.path("sort").toString())
            .contains("\"created_on\"").contains("\"last_modified_on\"").contains("\"unmapped_type\":\"date\"")
            .contains("\"id\"");
    }

    // 17/10/2026: từ khoá tìm cả nội dung tệp đính kèm (files_text) khi bật; điều kiện "chứa" theo trường thì không.
    @Test
    void keyword_shouldIncludeFileContentOnlyWhenEnabled() {
        AdvancedSearchQuery query = new AdvancedSearchQuery()
            .filter(new Match("code", "12/TT"))
            .keyword("hop dong", List.of("subject"));

        AdvancedSearchProperties on = new AdvancedSearchProperties();
        JsonNode withFiles = index(on).buildSearchBody(query, 0, 10);
        assertThat(withFiles.path("query").path("bool").path("must").toString()).contains("\"files_text\"");
        assertThat(withFiles.path("query").path("bool").path("filter").toString()).doesNotContain("files_text");

        AdvancedSearchProperties off = new AdvancedSearchProperties();
        off.setIncludeFileContent(false);
        assertThat(index(off).buildSearchBody(query, 0, 10).toString()).doesNotContain("files_text");
    }

    @Test
    void keywordWithoutColumns_shouldSearchWholeDocument() {
        JsonNode body = index(new AdvancedSearchProperties())
            .buildSearchBody(new AdvancedSearchQuery().keyword("quyet dinh", List.of()), 0, 10);

        assertThat(body.path("query").path("bool").path("must").toString()).contains("\"search_text\"");
    }

    // Tắt cấu hình / module không bật / không có RestClient → không dùng index (người gọi chạy SQL).
    @Test
    void search_whenDisabledOrNoClient_shouldReturnEmpty() {
        AdvancedSearchProperties disabled = new AdvancedSearchProperties();
        assertThat(index(disabled).search("vbden", "documents", new AdvancedSearchQuery(), 0, 20)).isEmpty();

        AdvancedSearchProperties enabled = new AdvancedSearchProperties();
        enabled.setEnabled(true);
        enabled.setModules(List.of("vbdi"));
        assertThat(enabled.isModuleEnabled("vbden")).isFalse();
        assertThat(index(enabled).search("vbdi", "vbdi", new AdvancedSearchQuery(), 0, 20))
            .as("không có RestClient").isEmpty();
        assertThat(AdvancedSearchIndex.DISABLED.search("vbden", "documents", new AdvancedSearchQuery(), 0, 20)).isEmpty();
    }

    @Test
    void alias_shouldMatchWorkerNaming() {
        assertThat(ElasticsearchAdvancedSearchIndex.alias("hscv", "H05_01", "documents")).isEqualTo("hscv-h05_01-documents");
        assertThat(ElasticsearchAdvancedSearchIndex.alias("HSCV", "So KH&CN", "vbdi")).isEqualTo("hscv-so_kh_cn-vbdi");
    }
}
