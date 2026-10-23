package com.td.worker.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchDocumentMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SearchSyncProperties props = new SearchSyncProperties();
    private final SearchDocumentMapper mapper = new SearchDocumentMapper(objectMapper, props);

    private Map<String, String> columns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "uuid");
        columns.put("title", "varchar");
        columns.put("created_on", "timestamptz");
        columns.put("version_no", "int8");
        columns.put("dynamic_fields", "jsonb");
        columns.put("files", "jsonb");
        columns.put("secret_blob", "bytea");
        return columns;
    }

    @Test
    void toDocument_shouldKeepColumnsAndAddSyncFields() {
        String row = """
            {"id":"6f1c2a4e-1111-2222-3333-444455556666","title":"Công văn số 12","created_on":"2026-10-16T09:00:00+07:00",
             "version_no":3,"dynamic_fields":{"trich_yeu":"Về việc triển khai"},"files":[{"name":"a.pdf"}],"secret_blob":null}
            """;

        ObjectNode doc = mapper.toDocument("H05", "vbdi", row, columns());

        assertThat(doc.path("title").asText()).isEqualTo("Công văn số 12");
        assertThat(doc.path("dynamic_fields").path("trich_yeu").asText()).isEqualTo("Về việc triển khai");
        assertThat(doc.path("sync_tenant").asText()).isEqualTo("H05");
        assertThat(doc.path("sync_entity").asText()).isEqualTo("vbdi");
        assertThat(doc.path("search_text").asText())
            .contains("Công văn số 12").contains("Về việc triển khai").contains("a.pdf")
            .doesNotContain("6f1c2a4e");
    }

    // Cột jsonb có dòng chứa mảng/chuỗi thay vì object phải được bọc — mapping "flattened" chỉ nhận object,
    // không bọc sẽ bị ES từ chối và kẹt hàng đợi.
    @Test
    void toDocument_shouldWrapNonObjectJsonbValues() {
        String row = """
            {"id":"6f1c2a4e-1111-2222-3333-444455556666","files":[{"name":"a.pdf"}],"dynamic_fields":"chuoi"}
            """;

        ObjectNode doc = mapper.toDocument("H05", "vbdi", row, columns());

        assertThat(doc.path("files").isObject()).isTrue();
        assertThat(doc.path("files").path("value").isArray()).isTrue();
        assertThat(doc.path("dynamic_fields").path("value").asText()).isEqualTo("chuoi");
    }

    @Test
    void toDocument_shouldDropExcludedColumns() {
        props.setExcludedColumns(List.of("secret_blob"));
        String row = """
            {"id":"6f1c2a4e-1111-2222-3333-444455556666","title":"x","secret_blob":"AAAA"}
            """;

        ObjectNode doc = mapper.toDocument("H05", "vbdi", row, columns());

        assertThat(doc.has("secret_blob")).isFalse();
    }

    @Test
    void toDocument_shouldTruncateSearchText() {
        props.setSearchTextMaxChars(10);
        String row = """
            {"id":"6f1c2a4e-1111-2222-3333-444455556666","title":"abcdefghijklmnopqrstuvwxyz"}
            """;

        ObjectNode doc = mapper.toDocument("H05", "vbdi", row, columns());

        assertThat(doc.path("search_text").asText()).hasSize(10);
    }

    // 17/10/2026: trường chuẩn hoá cho tìm kiếm nâng cao td-web (docs/analysis/27).
    @Test
    void toDocument_shouldBuildNormalizedSearchFields() {
        String row = """
            {"id":"6f1c2a4e-1111-2222-3333-444455556666","title":"Về việc A","status":"ISSUED",
             "created_on":"2026-10-16T09:00:00+07:00",
             "dynamic_fields":{"unit_process":["H05.01","H05.02"],"user_process":"NguyenVanA; tranb",
               "received_date":"15/10/2026 08:30:00","promulgation_date":"2026-10-01","document_number":"12/UBND",
               "code":"12/UBND-VP","creator":"Nguyễn Văn A","is_revoke":"", "so":3.0}}
            """;

        ObjectNode doc = mapper.toDocument("H05", "vbdi", row, columns());

        JsonNode norm = doc.path("norm_dyn");
        assertThat(norm.path("unit_process").toString()).contains("\"h05.01\"").contains("\"h05.02\"");
        assertThat(norm.path("user_process").toString()).contains("\"nguyenvana\"").contains("\"tranb\"")
            .contains("\"nguyenvana; tranb\"");
        assertThat(norm.path("creator").toString()).contains("\"nguyễn văn a\"").contains("\"nguyễnvăna\"");
        assertThat(norm.has("is_revoke")).as("giá trị rỗng không sinh token (exists = có giá trị)").isFalse();
        assertThat(norm.path("so").toString()).contains("\"3\"").contains("\"3.0\"");
        assertThat(doc.path("norm_col").path("status").toString()).contains("\"issued\"");
        assertThat(doc.path("date_dyn").path("received_date").asText()).isEqualTo("2026-10-15");
        assertThat(doc.path("date_dyn").path("promulgation_date").asText()).isEqualTo("2026-10-01");
        assertThat(doc.path("date_col").path("created_on").asText()).isEqualTo("2026-10-16");
        assertThat(doc.path("kw").path("code").asText()).isEqualTo("12/UBND-VP");
        assertThat(doc.path("kw").path("subject").asText()).as("subject gộp cột title").isEqualTo("Về việc A");
        assertThat(doc.path("sort_promulgation_date").asText()).isEqualTo("2026-10-01");
        assertThat(doc.path("sort_document_number").asLong()).isEqualTo(12L);
    }

    @Test
    void indexDefinition_shouldMapByPostgresColumnType() {
        props.setNumberOfReplicas(0);
        props.setTotalFieldsLimit(1500);

        JsonNode definition = mapper.indexDefinition(columns());

        JsonNode index = definition.path("settings").path("index");
        assertThat(index.path("number_of_replicas").asInt()).isZero();
        assertThat(index.path("mapping").path("total_fields").path("limit").asInt()).isEqualTo(1500);
        assertThat(index.path("mapping").path("ignore_malformed").asBoolean()).isTrue();
        assertThat(index.path("analysis").path("analyzer").path("vi_folding").path("filter").toString())
            .contains("lowercase").contains("vi_ascii");

        JsonNode mappings = definition.path("mappings");
        assertThat(mappings.path("date_detection").asBoolean()).isFalse();
        JsonNode properties = mappings.path("properties");
        assertThat(properties.path("id").path("type").asText()).isEqualTo("keyword");
        assertThat(properties.path("title").path("type").asText()).isEqualTo("text");
        assertThat(properties.path("title").path("fields").path("keyword").path("type").asText()).isEqualTo("keyword");
        assertThat(properties.path("created_on").path("type").asText()).isEqualTo("date");
        assertThat(properties.path("version_no").path("type").asText()).isEqualTo("long");
        assertThat(properties.path("dynamic_fields").path("type").asText()).isEqualTo("flattened");
        assertThat(properties.path("search_text").path("analyzer").asText()).isEqualTo("vi_folding");
    }
}
