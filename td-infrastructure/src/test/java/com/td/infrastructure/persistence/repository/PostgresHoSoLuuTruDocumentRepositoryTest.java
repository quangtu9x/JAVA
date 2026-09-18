package com.td.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port module "Hồ sơ lưu trữ": unit test SQL builder của {@link PostgresHoSoLuuTruDocumentRepository}
 * (không cần DB/EntityManager — repository khởi tạo với jpaRepository null), đúng khuôn
 * {@code PostgresHoSoCVDocumentRepositoryTest}.
 */
class PostgresHoSoLuuTruDocumentRepositoryTest {

    private final PostgresHoSoLuuTruDocumentRepository repository = new PostgresHoSoLuuTruDocumentRepository(null);

    @Test
    void appendAnyFieldContainsPredicate_withNullFieldNames_shouldAppendImpossibleCondition() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsPredicate(sql, parameters, null, "tinh01");

        assertThat(sql.toString()).isEqualTo(" AND 1 = 0");
    }

    @Test
    void appendAnyFieldContainsPredicate_withFieldName_shouldAppendContainmentAndScalarEquality() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsPredicate(sql, parameters, List.of("identifier"), "tinh01");

        String text = sql.toString();
        assertThat(text).doesNotContain("1 = 0");
        assertThat(text).contains("d.dynamic_fields @> CAST(:containsField0 AS jsonb)");
        assertThat(text).contains("COALESCE(d.dynamic_fields->>'identifier', '') = :containsFieldScalar0");
        assertThat(parameters).containsEntry("containsFieldScalar0", "tinh01");
    }

    @Test
    void appendFieldValueInPredicate_withStatusKey_shouldTargetStaticColumn() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendFieldValueInPredicate(sql, parameters, Map.of("status", List.of("hoso_chuaduyet")));

        String text = sql.toString();
        assertThat(text).contains("LOWER(COALESCE(d.status, '')) IN (:fieldValueIn0_0)");
        assertThat(parameters).containsEntry("fieldValueIn0_0", "hoso_chuaduyet");
    }

    @Test
    void appendFieldValueInPredicate_withDynamicFieldKey_shouldTargetJsonbColumn() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendFieldValueInPredicate(sql, parameters, Map.of("chuyenluutru", List.of("", "0")));

        String text = sql.toString();
        assertThat(text).contains("LOWER(COALESCE(d.dynamic_fields->>'chuyenluutru', ''))");
        assertThat(parameters).containsEntry("fieldValueIn0_0", "");
        assertThat(parameters).containsEntry("fieldValueIn0_1", "0");
    }

    @Test
    void appendFieldValueInPredicate_withNullOrEmpty_shouldAppendNothing() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendFieldValueInPredicate(sql, parameters, null);
        repository.appendFieldValueInPredicate(sql, parameters, Map.of());

        assertThat(sql.toString()).isEmpty();
        assertThat(parameters).isEmpty();
    }
}
