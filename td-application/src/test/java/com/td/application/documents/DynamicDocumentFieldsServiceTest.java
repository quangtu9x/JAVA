package com.td.application.documents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-101 (Dynamic Data Engine) — Definition of Done (2)/(3): xây dựng và đọc lại field động
 * theo hậu tố tên field + danh sách date_fields/number_fields/richtext_fields tương thích
 * qlvb/add, qlvb/get phía Domino.
 */
class DynamicDocumentFieldsServiceTest {

    private final DynamicDocumentFieldsService service = new DynamicDocumentFieldsService();

    private Map<String, String> rawFields;

    @BeforeEach
    void setUp() {
        rawFields = new LinkedHashMap<>();
        rawFields.put("subject", "Van ban test BL-101");
        rawFields.put("received_date", "2026-08-04");
        rawFields.put("so_tien_number", "150000");
        rawFields.put("process_obj", "{\"step\":1}");
        rawFields.put("nguoi_xu_ly_custom", "user1;user2");
    }

    @Test
    void buildDynamicFieldsJson_shouldInferTypeFromSuffix() {
        String json = service.buildDynamicFieldsJson(rawFields, null, null, null);
        Map<String, Object> parsed = service.parseDynamicFields(json);

        assertThat(parsed.get("subject")).isEqualTo("Van ban test BL-101");
        assertThat(parsed.get("received_date")).isEqualTo("2026-08-04");
        assertThat(parsed.get("so_tien_number")).isEqualTo(150000.0d);
        assertThat(parsed.get("process_obj")).isInstanceOf(Map.class);
        // Khong co hau tu nhan dien va khong trong date_fields/number_fields/richtext_fields -> TEXT nguyen ban.
        assertThat(parsed.get("nguoi_xu_ly_custom")).isEqualTo("user1;user2");
    }

    @Test
    void buildDynamicFieldsJson_shouldRespectExplicitFieldLists() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ghi_chu_custom", "{\"note\":\"uu tien\"}");

        String json = service.buildDynamicFieldsJson(fields, null, null, "ghi_chu_custom");
        Map<String, Object> parsed = service.parseDynamicFields(json);

        assertThat(parsed.get("ghi_chu_custom")).isInstanceOf(Map.class);
    }

    @Test
    void filterItems_getAll_shouldReturnEverything() {
        Map<String, Object> allFields = Map.of("a", 1, "b", 2);

        assertThat(service.filterItems(allFields, "getall")).isEqualTo(allFields);
        assertThat(service.filterItems(allFields, null)).isEqualTo(allFields);
        assertThat(service.filterItems(allFields, "")).isEqualTo(allFields);
    }

    @Test
    void filterItems_withFieldList_shouldReturnOnlyRequestedFields() {
        Map<String, Object> allFields = new LinkedHashMap<>();
        allFields.put("subject", "abc");
        allFields.put("received_date", "2026-08-04");
        allFields.put("so_tien_number", 150000.0);

        Map<String, Object> filtered = service.filterItems(allFields, "subject;so_tien_number");

        assertThat(filtered).containsOnlyKeys("subject", "so_tien_number");
    }

    @Test
    void parseDynamicFields_withBlankJson_shouldReturnEmptyMap() {
        assertThat(service.parseDynamicFields(null)).isEmpty();
        assertThat(service.parseDynamicFields("")).isEmpty();
        assertThat(service.parseDynamicFields("{}")).isEmpty();
    }
}
