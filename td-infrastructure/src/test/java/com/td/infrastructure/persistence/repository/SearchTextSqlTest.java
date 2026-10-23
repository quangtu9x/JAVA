package com.td.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 20/10/2026 — tìm kiếm không phân biệt dấu: từ khoá chuẩn hoá khớp td_search_norm (V1.0.75). */
class SearchTextSqlTest {

    @Test
    void normalize_shouldLowercaseAndStripVietnameseDiacritics() {
        assertThat(SearchTextSql.normalize("  Công Văn số 12/QĐ-UBND ")).isEqualTo("cong van so 12/qd-ubnd");
        assertThat(SearchTextSql.normalize("ĐƠN VỊ Phường Đông Hương")).isEqualTo("don vi phuong dong huong");
        assertThat(SearchTextSql.normalize("Trường Sơn - Ơn Ước - Ữ Ỹ Ậ")).isEqualTo("truong son - on uoc - u y a");
        assertThat(SearchTextSql.normalize(null)).isEmpty();
    }

    @Test
    void keywordSql_shouldBindNormalizedKeyword() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();

        KeywordSql.append(sql, params, "Kế Hoạch", List.of("subject"), "d");

        assertThat(sql.toString()).contains("td_search_norm(COALESCE(d.dynamic_fields->>'subject', '')) LIKE :keyword0");
        assertThat(params).containsEntry("keyword0", "%ke hoach%");
    }
}
