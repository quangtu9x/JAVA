package com.td.application.common.dynamicfields;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ItemsFilterTest {

    @Test
    void filter_getAll_shouldReturnEverything() {
        Map<String, Object> allFields = Map.of("a", 1, "b", 2);

        assertThat(ItemsFilter.filter(allFields, "getall")).isEqualTo(allFields);
        assertThat(ItemsFilter.filter(allFields, null)).isEqualTo(allFields);
        assertThat(ItemsFilter.filter(allFields, "")).isEqualTo(allFields);
        assertThat(ItemsFilter.filter(allFields, "GETALL")).isEqualTo(allFields);
    }

    @Test
    void filter_withFieldList_shouldReturnOnlyRequestedFields() {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("username", "alice");
        allFields.put("full_name", "Alice Nguyen");
        allFields.put("email", "alice@example.com");

        Map<String, Object> filtered = ItemsFilter.filter(allFields, "username;email");

        assertThat(filtered).containsOnlyKeys("username", "email");
    }

    @Test
    void filter_withNullAllFields_shouldReturnEmptyMap() {
        assertThat(ItemsFilter.filter(null, "getall")).isEmpty();
    }

    // Bugfix 24/08/2026 (xem javadoc ItemsFilter#filter): "id" là intrinsic (như UNID Domino),
    // phải luôn còn lại dù "items" không liệt kê tên nó — nếu không FE nhận dòng không có "id" và
    // không click xem chi tiết được.
    @Test
    void filter_withFieldListNotIncludingId_shouldStillKeepId() {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("id", "doc-1");
        allFields.put("name", "DT-001");
        allFields.put("creator", "alice");
        allFields.put("subject", "abc");

        Map<String, Object> filtered = ItemsFilter.filter(allFields, "name;creator;subject");

        assertThat(filtered).containsOnlyKeys("id", "name", "creator", "subject");
        assertThat(filtered.get("id")).isEqualTo("doc-1");
    }

    @Test
    void filter_withoutIdInAllFields_shouldNotAddId() {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("username", "alice");
        allFields.put("full_name", "Alice Nguyen");

        Map<String, Object> filtered = ItemsFilter.filter(allFields, "username");

        assertThat(filtered).containsOnlyKeys("username");
    }

    @Test
    void splitSemicolonList_shouldTrimAndLowercase() {
        assertThat(ItemsFilter.splitSemicolonList(" Username_Test ; Full_Name "))
            .isEqualTo(Set.of("username_test", "full_name"));
    }

    @Test
    void splitSemicolonList_withBlank_shouldReturnEmptySet() {
        assertThat(ItemsFilter.splitSemicolonList(null)).isEmpty();
        assertThat(ItemsFilter.splitSemicolonList("")).isEmpty();
    }
}
