package com.td.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 01/10/2026: tim trong danh sach du thao/ho so - title + chong SQL injection ten cot. */
class KeywordSqlTest {

    @Test
    void append_subjectAndTitle_shouldAlsoMatchStaticTitleColumn() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        KeywordSql.append(sql, parameters, "Ho So", List.of("name", "title", "subject"), "d");

        String text = sql.toString();
        assertThat(text).contains("LOWER(COALESCE(d.dynamic_fields->>'name', '')) LIKE :keyword0");
        assertThat(text).contains("LOWER(COALESCE(d.title, '')) LIKE :keyword1");
        assertThat(text).contains("LOWER(COALESCE(d.title, '')) LIKE :keyword2");
        assertThat(text).doesNotContain("d.title, '')) LIKE :keyword0");
        assertThat(parameters).containsEntry("keyword0", "%ho so%");
    }

    @Test
    void append_shouldDropUnsafeColumnNames() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        KeywordSql.append(sql, parameters, "x", List.of("name'); DROP TABLE x; --"), "d");

        assertThat(sql.toString()).isEmpty();
        assertThat(parameters).isEmpty();
    }
}
