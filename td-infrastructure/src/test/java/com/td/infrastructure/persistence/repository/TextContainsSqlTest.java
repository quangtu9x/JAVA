package com.td.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TextContainsSqlTest {

    @Test
    void append_shouldIlikeEachFieldAndEscapeWildcards() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, String> text = new LinkedHashMap<>();
        text.put("code_full", " 12%_3 ");

        TextContainsSql.append(sql, params, text, true);

        assertThat(sql.toString()).contains("td_search_norm(COALESCE(d.dynamic_fields->>'code_full', '')) LIKE :txtContains0");
        assertThat(params).containsEntry("txtContains0", "%12\\%\\_3%");
    }

    @Test
    void append_titleField_shouldAlsoMatchStaticTitleColumnWhenTableHasIt() {
        StringBuilder withTitle = new StringBuilder();
        StringBuilder withoutTitle = new StringBuilder();

        TextContainsSql.append(withTitle, new LinkedHashMap<>(), Map.of("title", "Ke hoach"), true);
        TextContainsSql.append(withoutTitle, new LinkedHashMap<>(), Map.of("title", "Ke hoach"), false);

        assertThat(withTitle.toString()).contains("OR td_search_norm(COALESCE(d.title, '')) LIKE");
        assertThat(withoutTitle.toString()).doesNotContain("d.title");
    }

    @Test
    void append_unsafeFieldNameOrBlankValue_shouldBeSkipped() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, String> text = new LinkedHashMap<>();
        text.put("x'); DROP TABLE documents; --", "a");
        text.put("name", "  ");

        TextContainsSql.append(sql, params, text, true);

        assertThat(sql.toString()).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void appendContainsAny_shouldOrValuesWithinFieldAndAndAcrossFields() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("unit_process", List.of("H56.09", "H56.10"));

        TextContainsSql.appendContainsAny(sql, params, values, "xyAny");

        assertThat(sql.toString()).startsWith(" AND (").contains(" OR ");
        assertThat(params).containsEntry("xyAny1_0", "H56.09").containsEntry("xyAnyS1_1", "H56.10");
    }
}
