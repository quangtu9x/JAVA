package com.td.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mở rộng BL-240 (HoSoCV Core): unit test SQL builder của {@link PostgresHoSoCVDocumentRepository}
 * (không cần DB/EntityManager — repository khởi tạo với jpaRepository null, chỉ gọi các method
 * build predicate package-private) — đúng khuôn {@code PostgresDuThaoDocumentRepositoryTest}.
 */
class PostgresHoSoCVDocumentRepositoryTest {

    private final PostgresHoSoCVDocumentRepository repository = new PostgresHoSoCVDocumentRepository(null);

    @Test
    void appendAnyFieldContainsPredicate_withNullFieldNames_shouldAppendImpossibleCondition() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsPredicate(sql, parameters, null, "hosovan01");

        assertThat(sql.toString()).isEqualTo(" AND 1 = 0");
    }

    @Test
    void appendAnyFieldContainsPredicate_withFieldName_shouldAppendContainmentAndScalarEquality() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsPredicate(sql, parameters, List.of("user_process_handler"), "hosovan01");

        String text = sql.toString();
        assertThat(text).doesNotContain("1 = 0");
        assertThat(text).contains("d.dynamic_fields @> CAST(:containsField0 AS jsonb)");
        assertThat(text).contains("COALESCE(d.dynamic_fields->>'user_process_handler', '') = :containsFieldScalar0");
        assertThat(parameters).containsEntry("containsFieldScalar0", "hosovan01");
    }

    // "vwUser_Coordinator": field luu chuoi ";"-joined - phai la member-match (LIKE neo ";"), khong
    // phai containment/scalar-equality toan chuoi.
    @Test
    void appendAnyListFieldMemberPredicate_include_shouldAppendMemberMatchWithoutImpossibleCondition() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyListFieldMemberPredicate(sql, parameters, List.of("user_coordinator"), "HoSoVan01");

        String text = sql.toString();
        assertThat(text).doesNotContain("1 = 0");
        assertThat(text).contains("LOWER(COALESCE(d.dynamic_fields->>'user_coordinator', '')) = :incl0Exact");
        assertThat(text).contains("LIKE :incl0Prefix");
        assertThat(text).contains("LIKE :incl0Suffix");
        assertThat(text).contains("LIKE :incl0Middle");
        assertThat(parameters).containsEntry("incl0Exact", "hosovan01");
        assertThat(parameters).containsEntry("incl0Prefix", "hosovan01;%");
        assertThat(parameters).containsEntry("incl0Suffix", "%;hosovan01");
        assertThat(parameters).containsEntry("incl0Middle", "%;hosovan01;%");
    }

    // "vwUser_Coordinated_Status" nhanh "_chua": phai LOAI TRU thanh vien cua user_coordinated
    // (negate=true) - dung "AND NOT (...)", khong phai "AND (...)".
    @Test
    void appendAnyListFieldMemberPredicate_exclude_shouldNegateMemberMatch() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyListFieldMemberPredicate(sql, parameters, List.of("user_coordinated"), "hosovan01", true);

        String text = sql.toString();
        assertThat(text).startsWith(" AND NOT (");
        assertThat(parameters).containsEntry("excl0Exact", "hosovan01");
    }

    @Test
    void appendAnyListFieldMemberPredicate_withBlankValue_includeBranch_shouldAppendImpossibleCondition() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyListFieldMemberPredicate(sql, parameters, List.of("user_coordinator"), " ");

        assertThat(sql.toString()).isEqualTo(" AND 1 = 0");
    }

    // Nhanh loai tru voi gia tri rong PHAI khong them dieu kien nao (khac nhanh include tra "1 = 0")
    // - loai tru "khong ai" thi khong duoc chan ca ket qua include hop le.
    @Test
    void appendAnyListFieldMemberPredicate_withBlankValue_excludeBranch_shouldAppendNothing() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyListFieldMemberPredicate(sql, parameters, List.of("user_coordinated"), " ", true);

        assertThat(sql.toString()).isEmpty();
    }

    // 01/10/2026: "Chua phoi hop" (Domino fieldnot_user_coordinated) - field KHONG chua gia tri o ca 3
    // dang: phan tu mang JSON, scalar bang, thanh vien chuoi "a;b".
    @Test
    void appendExcludeMemberFieldValuePredicates_shouldNegateArrayScalarAndDelimitedMember() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendExcludeMemberFieldValuePredicates(sql, parameters, Map.of("user_coordinated", List.of("lpha")));

        String text = sql.toString();
        assertThat(text).startsWith(" AND NOT (COALESCE(d.dynamic_fields->'user_coordinated' @> jsonb_build_array(CAST(:noneVal0_0 AS text)), false)");
        assertThat(text).contains("COALESCE(d.dynamic_fields->>'user_coordinated', '') = :noneValScalar0_0");
        assertThat(text).contains("LIKE :noneValMember0_0");
        assertThat(parameters).containsEntry("noneVal0_0", "lpha").containsEntry("noneValMember0_0", "%;lpha;%");
    }

    @Test
    void appendExcludeMemberFieldValuePredicates_withNullMap_shouldAppendNothing() {
        StringBuilder sql = new StringBuilder();
        repository.appendExcludeMemberFieldValuePredicates(sql, new LinkedHashMap<>(), null);
        assertThat(sql.toString()).isEmpty();
    }
}
