package com.td.infrastructure.persistence.repository;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BL-102 (View Query Engine): xác nhận JSON containment filter cho {@code vwDocument_ChoXuLy}
 * (query {@code dynamic_fields @> :userProcessFilter}) đúng định dạng mảng JSON, khớp với cách
 * {@code DynamicFieldValueConverter} lưu field {@code _tlist}/{@code _aut}/{@code _rdr}.
 */
class PostgresDocumentRepositoryTest {

    private final PostgresDocumentRepository repository = new PostgresDocumentRepository(null);

    @Test
    void buildUserProcessFilter_shouldProduceJsonArrayContainmentPayload() {
        String json = repository.buildUserProcessFilter("username_test");

        assertThat(json).isEqualTo("{\"user_process\":[\"username_test\"]}");
    }

    @Test
    void buildUserProcessedFilter_shouldProduceJsonArrayContainmentPayload() {
        String json = repository.buildUserProcessedFilter("username_test");

        assertThat(json).isEqualTo("{\"user_processed\":[\"username_test\"]}");
    }

    @Test
    void buildParentIdFilter_shouldProduceJsonStringContainmentPayload() {
        String json = repository.buildParentIdFilter("parent-unid-123");

        assertThat(json).isEqualTo("{\"parentid\":\"parent-unid-123\"}");
    }

    @Test
    void buildFieldValueFilter_shouldProduceJsonStringContainmentPayloadForArbitraryField() {
        String json = repository.buildFieldValueFilter("idvanban", "van-ban-unid-123");

        assertThat(json).isEqualTo("{\"idvanban\":\"van-ban-unid-123\"}");
    }

    // BL-210 (VBDen Core) - N03: OR containment tren N field JSON-array (captrengiao/dennoibo/
    // quamangnoibo), moi field 1 dieu kien rieng cung cong thuc voi buildUserProcessFilter.
    @Test
    void buildContainsFilter_withSingleField_shouldProduceJsonArrayContainmentPayload() {
        String json = repository.buildContainsFilter("contact_unit_identifier", "PHONG_TH");

        assertThat(json).isEqualTo("{\"contact_unit_identifier\":[\"PHONG_TH\"]}");
    }

    @Test
    void buildContainsFilter_calledPerField_shouldEachProduceOwnArrayContainmentPayload() {
        // appendAnyFieldContainsPredicate goi buildContainsFilter 1 lan cho moi field trong danh
        // sach (vd. unit_process/unit_coordinate/unit_to_read cho captrengiao/dennoibo) - xac nhan
        // tung field tao dung payload rieng, khong lan vao nhau.
        List<String> fieldNames = List.of("unit_process", "unit_coordinate", "unit_to_read");

        assertThat(repository.buildContainsFilter(fieldNames.get(0), "PHONG_TH"))
            .isEqualTo("{\"unit_process\":[\"PHONG_TH\"]}");
        assertThat(repository.buildContainsFilter(fieldNames.get(1), "PHONG_TH"))
            .isEqualTo("{\"unit_coordinate\":[\"PHONG_TH\"]}");
        assertThat(repository.buildContainsFilter(fieldNames.get(2), "PHONG_TH"))
            .isEqualTo("{\"unit_to_read\":[\"PHONG_TH\"]}");
    }

    // BUGFIX 21/08/2026: "identifier"/"identifier_creator" khong khop hau to _aut/_rdr/_tlist nen
    // FieldTypeInferrer luu SCALAR TEXT (khong phai JSON array) - phai OR them dieu kien scalar
    // equality, khong chi containment "@>", neu khong se KHONG BAO GIO khop du liem thuc te (day la
    // nguyen nhan GetSoVanBanDen/chophanxuly/cuatochuc bao khong co du lieu du van ban da tao).
    @Test
    void appendAnyFieldContainsPredicate_shouldOrScalarEqualityWithArrayContainmentPerField() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsPredicate(sql, parameters, List.of("identifier_creator"), "PHONG_TH");

        assertThat(sql.toString()).contains("d.dynamic_fields @> CAST(:containsField0 AS jsonb)");
        assertThat(sql.toString()).contains("COALESCE(d.dynamic_fields->>'identifier_creator', '') = :containsFieldScalar0");
        assertThat(parameters).containsEntry("containsField0", "{\"identifier_creator\":[\"PHONG_TH\"]}");
        assertThat(parameters).containsEntry("containsFieldScalar0", "PHONG_TH");
    }

    @Test
    void appendAnyFieldContainsPredicate_withMultipleFields_shouldGiveEachFieldItsOwnScalarAndArrayCheck() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendAnyFieldContainsPredicate(
            sql, parameters, List.of("identifier", "unit_process"), "PHONG_TH");

        assertThat(sql.toString()).contains("COALESCE(d.dynamic_fields->>'identifier', '') = :containsFieldScalar0");
        assertThat(sql.toString()).contains("COALESCE(d.dynamic_fields->>'unit_process', '') = :containsFieldScalar1");
        assertThat(parameters).containsEntry("containsFieldScalar0", "PHONG_TH");
        assertThat(parameters).containsEntry("containsFieldScalar1", "PHONG_TH");
    }

    // D.44 (01/09/2026, "tim kiem nang cao" theodoi/datheodoi): dao nguoc appendAnyFieldContainsPredicate
    // - CUNG 1 field, NHIEU gia tri OR (khong phai nhieu field cho CUNG 1 gia tri).
    @Test
    void appendContainsAnyFieldValuePredicates_withNullOrEmptyMap_shouldAppendNothing() {
        StringBuilder sql = new StringBuilder("BASE");
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendContainsAnyFieldValuePredicates(sql, parameters, null);
        repository.appendContainsAnyFieldValuePredicates(sql, parameters, Map.of());

        assertThat(sql.toString()).isEqualTo("BASE");
        assertThat(parameters).isEmpty();
    }

    @Test
    void appendContainsAnyFieldValuePredicates_withSingleFieldSingleValue_shouldAddScalarAndArrayCheck() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendContainsAnyFieldValuePredicates(
            sql, parameters, Map.of("unit_in_charge", List.of("UBND tinh")));

        assertThat(sql.toString()).contains("d.dynamic_fields @> CAST(:anyVal0_0 AS jsonb)");
        assertThat(sql.toString()).contains("COALESCE(d.dynamic_fields->>'unit_in_charge', '') = :anyValScalar0_0");
        assertThat(parameters).containsEntry("anyVal0_0", "{\"unit_in_charge\":[\"UBND tinh\"]}");
        assertThat(parameters).containsEntry("anyValScalar0_0", "UBND tinh");
    }

    // "Nguoi xu ly" chon NHIEU nguoi (";"-noi) - moi username la 1 dieu kien OR rieng cho CUNG 1 field.
    @Test
    void appendContainsAnyFieldValuePredicates_withMultipleValuesForOneField_shouldOrThem() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendContainsAnyFieldValuePredicates(
            sql, parameters, Map.of("user_process", List.of("nguyenvana", "tranthib")));

        String rendered = sql.toString();
        assertThat(rendered).contains(" OR ");
        assertThat(parameters).containsEntry("anyVal0_0", "{\"user_process\":[\"nguyenvana\"]}");
        assertThat(parameters).containsEntry("anyVal0_1", "{\"user_process\":[\"tranthib\"]}");
    }

    @Test
    void appendContainsAnyFieldValuePredicates_withMultipleFields_shouldAndBetweenFields() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();
        Map<String, List<String>> filters = new LinkedHashMap<>();
        filters.put("unit_in_charge", List.of("UBND tinh"));
        filters.put("unit_process", List.of("H56.09"));

        repository.appendContainsAnyFieldValuePredicates(sql, parameters, filters);

        long andCount = sql.toString().split(" AND \\(", -1).length - 1;
        assertThat(andCount).isEqualTo(2);
        assertThat(parameters).containsEntry("anyValScalar0_0", "UBND tinh");
        assertThat(parameters).containsEntry("anyValScalar1_0", "H56.09");
    }

    // Bugfix 31/08/2026 (xem javadoc/comment o appendDeadlineInRangePredicate that): deadline_date
    // luu chuoi "dd/MM/yyyy" (KHONG PHAI ISO nhu gia dinh cu) - da xac nhan qua doi chieu FE
    // (date_to_dd_mm_yyyy luon duoc goi truoc khi gui) + DynamicFieldValueConverter (giu nguyen
    // chuoi cho DATETIME). Test nay cap nhat theo dung dinh dang that, dung TO_DATE(...,'DD/MM/YYYY')
    // giong het dynamicSortExpression() da dung dung cho sort.
    @Test
    void appendDeadlineInRangePredicate_withBothBounds_shouldAddFormatGuardAndBothComparisons() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendDeadlineInRangePredicate(
            sql, parameters, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20));

        assertThat(sql.toString()).contains("~ :deadlineDateFormat");
        assertThat(sql.toString()).contains("TO_DATE(d.dynamic_fields->>'deadline_date', 'DD/MM/YYYY') >= :minDeadline");
        assertThat(sql.toString()).contains("TO_DATE(d.dynamic_fields->>'deadline_date', 'DD/MM/YYYY') <= :maxDeadline");
        assertThat(parameters).containsEntry("deadlineDateFormat", "^[0-9]{2}/[0-9]{2}/[0-9]{4}$");
        assertThat(parameters).containsEntry("minDeadline", java.sql.Date.valueOf(LocalDate.of(2026, 8, 20)));
        assertThat(parameters).containsEntry("maxDeadline", java.sql.Date.valueOf(LocalDate.of(2026, 8, 20)));
    }

    @Test
    void appendDeadlineInRangePredicate_withNullMinDeadline_shouldOnlyAddMaxComparison() {
        // Domino: sap toi han = songayconlai<=3, khong co can duoi trong bang chung goc.
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendDeadlineInRangePredicate(sql, parameters, null, LocalDate.of(2026, 8, 23));

        assertThat(sql.toString()).doesNotContain(">= :minDeadline");
        assertThat(sql.toString()).contains("<= :maxDeadline");
        assertThat(parameters).doesNotContainKey("minDeadline");
        assertThat(parameters).containsEntry("maxDeadline", java.sql.Date.valueOf(LocalDate.of(2026, 8, 23)));
    }

    // 31/08/2026 (port "toanbovanban" - RangePicker "khoang ngay nhan van ban"): mirror y het bo test
    // appendDeadlineInRangePredicate, chi khac ten field/param - xem javadoc VbdenOrgViewFilter#minReceivedDate.
    // 02/10/2026: dung chung bieu thuc sort "date" - khop ca 3 dang received_date that.
    @Test
    void appendReceivedDateInRangePredicate_withBothBounds_shouldCompareParsedDateOfAllFormats() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendReceivedDateInRangePredicate(
            sql, parameters, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        String receivedDate = repository.dynamicSortExpression("received_date", "date");
        assertThat(sql.toString()).isEqualTo(
            " AND " + receivedDate + " >= :minReceivedDate AND " + receivedDate + " <= :maxReceivedDate");
        assertThat(parameters).doesNotContainKey("receivedDateFormat");
        assertThat(parameters).containsEntry("minReceivedDate", java.sql.Date.valueOf(LocalDate.of(2026, 8, 1)));
        assertThat(parameters).containsEntry("maxReceivedDate", java.sql.Date.valueOf(LocalDate.of(2026, 8, 31)));
    }

    @Test
    void appendReceivedDateInRangePredicate_withNullMaxReceivedDate_shouldOnlyAddMinComparison() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendReceivedDateInRangePredicate(sql, parameters, LocalDate.of(2026, 8, 1), null);

        assertThat(sql.toString()).contains(">= :minReceivedDate");
        assertThat(sql.toString()).doesNotContain("<= :maxReceivedDate");
        assertThat(parameters).doesNotContainKey("maxReceivedDate");
        assertThat(parameters).containsEntry("minReceivedDate", java.sql.Date.valueOf(LocalDate.of(2026, 8, 1)));
    }

    // N03/N13 (Slice 4): scalar field dong (internal/document_internet/Reply/document_type) IN/NOT
    // IN danh sach gia tri, doc dung tu docs/views_domino/view_vanbanden.dxl.
    @Test
    void appendDynamicFieldValueInPredicate_include_shouldUseInClauseWithCoalesce() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendDynamicFieldValueInPredicate(sql, parameters, "document_internet", List.of("1", "2"), true, "p0");

        assertThat(sql.toString()).contains("COALESCE(d.dynamic_fields->>'document_internet', '')");
        assertThat(sql.toString()).contains(" IN (:p0_0,:p0_1)");
        assertThat(parameters).containsEntry("p0_0", "1");
        assertThat(parameters).containsEntry("p0_1", "2");
    }

    @Test
    void appendDynamicFieldValueInPredicate_exclude_shouldUseNotInClause() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendDynamicFieldValueInPredicate(sql, parameters, "internal", List.of("1"), false, "p0");

        assertThat(sql.toString()).contains("COALESCE(d.dynamic_fields->>'internal', '')");
        assertThat(sql.toString()).contains(" NOT IN (:p0_0)");
        assertThat(parameters).containsEntry("p0_0", "1");
    }

    @Test
    void appendDynamicFieldValueInPredicate_withEmptyValues_shouldAddNoPredicate() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendDynamicFieldValueInPredicate(sql, parameters, "Reply", List.of(), false, "p0");

        assertThat(sql.toString()).isEmpty();
        assertThat(parameters).isEmpty();
    }

    // N13 (Slice 4) rieng cho phaixuly_cuatochuc: (deadline_date!="" | documentreply="1").
    @Test
    void appendDeadlineOrDocumentReplyPredicate_shouldOrTwoConditions() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendDeadlineOrDocumentReplyPredicate(sql, parameters);

        assertThat(sql.toString()).contains("COALESCE(d.dynamic_fields->>'deadline_date', '') != ''");
        assertThat(sql.toString()).contains("OR COALESCE(d.dynamic_fields->>'documentreply', '') = :documentReplyValue");
        assertThat(parameters).containsEntry("documentReplyValue", "1");
    }

    // N05 (chophanxuly, Slice 6): user_process="" - field dong dang mang JSON phai rong/vang mat.
    @Test
    void appendFieldEmptyPredicate_shouldCheckNullOrEmptyJsonArray() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendFieldEmptyPredicate(sql, parameters, "user_process");

        assertThat(sql.toString()).contains("d.dynamic_fields->'user_process' IS NULL");
        assertThat(sql.toString()).contains("jsonb_array_length(d.dynamic_fields->'user_process') = 0");
        assertThat(parameters).isEmpty();
    }

    // Slice 7 (tim kiem/loc): keyword OR tren nhieu field dong scalar (columnsSearchVBden phia FE).
    @Test
    void appendKeywordPredicate_withKeywordAndColumns_shouldOrLikeAcrossColumns() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendKeywordPredicate(sql, parameters, "Cong Van A", List.of("document_number", "code", "subject"));

        assertThat(sql.toString()).contains("td_search_norm(COALESCE(d.dynamic_fields->>'document_number', '')) LIKE :keyword0");
        assertThat(sql.toString()).contains("td_search_norm(COALESCE(d.dynamic_fields->>'code', '')) LIKE :keyword1");
        assertThat(sql.toString()).contains("td_search_norm(COALESCE(d.dynamic_fields->>'subject', '')) LIKE :keyword2");
        assertThat(sql.toString()).contains(" OR ");
        assertThat(parameters).containsEntry("keyword0", "%cong van a%");
        assertThat(parameters).containsEntry("keyword1", "%cong van a%");
        assertThat(parameters).containsEntry("keyword2", "%cong van a%");
    }

    // Bugfix 01/10/2026: trich yeu nam o cot tinh title; ban ghi so vbden_so khop qua van ban goc (parentid).
    @Test
    void appendKeywordPredicate_shouldAlsoMatchTitleAndParentDocumentOfSoEntries() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendKeywordPredicate(sql, parameters, "Van ban", List.of("code", "subject"));

        String text = sql.toString();
        assertThat(text).contains("td_search_norm(COALESCE(d.title, '')) LIKE :keyword1");
        assertThat(text).contains("d.document_type = 'vbden_so' AND EXISTS (SELECT 1 FROM documents p"
            + " WHERE CAST(p.id AS text) = d.dynamic_fields->>'parentid'");
        assertThat(text).contains("td_search_norm(COALESCE(p.dynamic_fields->>'code', '')) LIKE :keyword0");
        assertThat(text).contains("td_search_norm(COALESCE(p.title, '')) LIKE :keyword1");
        // title chi them cho subject, khong cho code.
        assertThat(text).doesNotContain("d.title, '')) LIKE :keyword0");
    }

    // 01/10/2026: ten cot tu client noi thang vao SQL -> bo cot khong an toan (chong SQL injection).
    @Test
    void appendKeywordPredicate_shouldIgnoreUnsafeColumnNames() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendKeywordPredicate(sql, parameters, "x", List.of("code') OR 1=1 --", "subject"));

        assertThat(sql.toString()).doesNotContain("1=1").contains("'subject'");
        assertThat(parameters).containsOnlyKeys("keyword0");
    }

    @Test
    void appendKeywordPredicate_withOnlyUnsafeColumns_shouldAddNoPredicate() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendKeywordPredicate(sql, parameters, "x", List.of("a'b"));

        assertThat(sql.toString()).isEmpty();
    }

    @Test
    void appendKeywordPredicate_withBlankKeyword_shouldAddNoPredicate() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendKeywordPredicate(sql, parameters, "  ", List.of("subject"));

        assertThat(sql.toString()).isEmpty();
        assertThat(parameters).isEmpty();
    }

    @Test
    void appendKeywordPredicate_withEmptyColumns_shouldAddNoPredicate() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendKeywordPredicate(sql, parameters, "vanban", List.of());

        assertThat(sql.toString()).isEmpty();
        assertThat(parameters).isEmpty();
    }

    // Slice 9 (sovanban - "Vao so van ban den", doc tu docs/views_domino/view_vanbanden_so.dxl).
    @Test
    void appendFieldNonEmptyPredicate_shouldCheckNotNullAndNonEmptyJsonArray() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendFieldNonEmptyPredicate(sql, parameters, "document_register_id");

        assertThat(sql.toString()).contains("d.dynamic_fields->'document_register_id' IS NOT NULL");
        assertThat(sql.toString()).contains("jsonb_array_length(d.dynamic_fields->'document_register_id') > 0");
        assertThat(parameters).isEmpty();
    }

    @Test
    void appendCreatedOnInRangePredicate_withBothBounds_shouldCastStaticColumnToDate() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendCreatedOnInRangePredicate(
            sql, parameters, LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23));

        assertThat(sql.toString()).contains("CAST(d.created_on AS date) >= :minCreatedOn");
        assertThat(sql.toString()).contains("CAST(d.created_on AS date) <= :maxCreatedOn");
        assertThat(parameters).containsEntry("minCreatedOn", java.sql.Date.valueOf(LocalDate.of(2026, 8, 17)));
        assertThat(parameters).containsEntry("maxCreatedOn", java.sql.Date.valueOf(LocalDate.of(2026, 8, 23)));
    }

    @Test
    void appendCreatedOnInRangePredicate_withOnlyMin_shouldOnlyAddMinComparison() {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();

        repository.appendCreatedOnInRangePredicate(sql, parameters, LocalDate.of(2026, 8, 1), null);

        assertThat(sql.toString()).contains(">= :minCreatedOn");
        assertThat(sql.toString()).doesNotContain("maxCreatedOn");
    }

    // Bugfix 21/08/2026: du lieu thuc te "document_number" la "3.0" (khong phai "3") vi ket thuc
    // hau to "_number" -> FieldTypeInferrer suy NUMBER (Double) - phai ep qua numeric truoc.
    // Bugfix 21/08/2026 (lan 2): khong dung toan tu Postgres "::" trong native SQL cua Hibernate -
    // bi parser named-parameter cua Hibernate nuot mat 1 dau ":" (loi cu phap SQL thuc te khi
    // chay lai), phai dung CAST(...AS numeric) long nhau (chuan ANSI, khong co ky tu ":").
    @Test
    void dynamicSortExpression_withIntegerType_shouldCastThroughNumericFirst() {
        assertThat(repository.dynamicSortExpression("document_number", "integer"))
            .isEqualTo("CAST(CAST(NULLIF(d.dynamic_fields->>'document_number', '') AS numeric) AS integer)");
    }

    // Bugfix 21/08/2026: du lieu thuc te "received_date" la "dd/MM/yyyy" (vd "21/08/2026"), khong
    // phai ISO - dung TO_DATE voi format ro rang, guard regex tranh loi runtime voi du lieu sai dang.
    // 02/10/2026: them 2 dang "dd/MM/yyyy HH:mm:ss" va "yyyy-MM-dd" (truoc tra NULL) - chi lay ngay.
    @Test
    void dynamicSortExpression_withDateType_shouldParseAllStoredFormatsAtDayPrecision() {
        assertThat(repository.dynamicSortExpression("received_date", "date"))
            .isEqualTo("(CASE WHEN d.dynamic_fields->>'received_date' ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}'"
                + " THEN TO_DATE(SUBSTRING(d.dynamic_fields->>'received_date' FROM 1 FOR 10), 'DD/MM/YYYY')"
                + " WHEN d.dynamic_fields->>'received_date' ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}'"
                + " THEN TO_DATE(SUBSTRING(d.dynamic_fields->>'received_date' FROM 1 FOR 10), 'YYYY-MM-DD')"
                + " ELSE NULL END)");
    }

    // 02/10/2026 (theodoi): received_date that co 3 dang; khong duoc co ky tu ":" (Hibernate parser).
    @Test
    void dynamicSortExpression_withDatetimeType_shouldParseAllStoredFormatsWithoutColons() {
        String expression = repository.dynamicSortExpression("received_date", "datetime");

        assertThat(expression)
            .contains("'DD/MM/YYYY HH24 MI SS'")
            .contains("'DD/MM/YYYY'")
            .contains("'YYYY-MM-DD'")
            .doesNotContain(":");
    }

    @Test
    void dynamicSortExpression_withoutType_shouldReturnRawTextPath() {
        assertThat(repository.dynamicSortExpression("subject", null))
            .isEqualTo("d.dynamic_fields->>'subject'");
    }
}
